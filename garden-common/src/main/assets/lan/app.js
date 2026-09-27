/*
 * ThothTerm Ubuntu -- LAN Terminal page.
 *
 * Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0.
 *
 * The browser credential is a bearer token kept in this origin's
 * localStorage. It is sent only in the WebSocket handshake's
 * Sec-WebSocket-Protocol header and in an Authorization header -- never in
 * a URL, never as a cookie. Each tab remembers its own terminal id in
 * sessionStorage, so a reload reattaches to the same shell.
 */
(function () {
  'use strict';

  var PROTOCOL = 'thothterm.v1';
  var TOKEN_KEY = 'thothterm.lan.token';
  var TERM_KEY = 'thothterm.lan.term';
  /** The phone keeps a detached terminal for 30 s; stop retrying a little before. */
  var RECONNECT_WINDOW_MS = 25000;
  var PING_MS = 20000;

  var $ = function (id) { return document.getElementById(id); };
  var encoder = new TextEncoder();

  function load(storage, key) {
    try { return storage.getItem(key); } catch (e) { return null; }
  }
  function save(storage, key, value) {
    try {
      if (value === null) storage.removeItem(key); else storage.setItem(key, value);
    } catch (e) { /* storage unavailable: the page still works for this load */ }
  }
  var token = load(window.localStorage, TOKEN_KEY);

  function setState(text, kind) {
    var el = $('state');
    el.textContent = text;
    el.className = 'state' + (kind ? ' ' + kind : '');
  }

  function showOnly(id) {
    ['pair', 'notice', 'terminal'].forEach(function (v) { $(v).hidden = v !== id; });
    var inTerminal = id === 'terminal';
    $('copy').hidden = !inTerminal;
    $('signout').hidden = !token;
  }

  function notice(text, action, onAction) {
    $('notice-text').textContent = text;
    var button = $('notice-action');
    button.textContent = action;
    button.onclick = onAction;
    showOnly('notice');
  }

  // ------------------------------------------------------------ pairing

  function showPairing(message) {
    closeSocket();
    setState('Not paired', 'wait');
    $('pair-error').textContent = message || '';
    $('pin').value = '';
    showOnly('pair');
    $('pin').focus();
  }

  var PAIR_ERRORS = {
    wrong_pin: function (r) {
      return 'Wrong PIN. ' + r.attemptsLeft + (r.attemptsLeft === 1 ? ' attempt' : ' attempts') + ' left.';
    },
    locked: function () { return 'Too many wrong attempts. Tap “New PIN” on the phone.'; },
    expired: function () { return 'That PIN has expired. Tap “New PIN” on the phone.'; },
    no_pin: function () { return 'That PIN was already used. Tap “New PIN” on the phone.'; },
    too_fast: function () { return 'Please wait a moment and try again.'; },
    full: function () { return 'Too many browsers are paired. Sign one out first.'; }
  };

  $('pair-form').addEventListener('submit', function (event) {
    event.preventDefault();
    var pin = $('pin').value.trim();
    if (!/^[0-9]{6}$/.test(pin)) {
      $('pair-error').textContent = 'The PIN is six digits.';
      return;
    }
    $('pair-error').textContent = '';
    fetch('/api/pair', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pin: pin }),
      cache: 'no-store',
      credentials: 'omit'
    }).then(function (response) {
      return response.json().catch(function () { return {}; }).then(function (body) {
        if (response.ok && body.token) {
          token = body.token;
          save(window.localStorage, TOKEN_KEY, token);
          save(window.sessionStorage, TERM_KEY, null);
          startTerminal();
        } else {
          var explain = PAIR_ERRORS[body.error];
          $('pair-error').textContent = explain ? explain(body) : 'Pairing failed.';
          $('pin').select();
        }
      });
    }).catch(function () {
      $('pair-error').textContent = 'The phone did not answer. Is LAN Mode still on?';
    });
  });

  function checkToken() {
    if (!token) return Promise.resolve(false);
    return fetch('/api/session', {
      headers: { Authorization: 'Bearer ' + token },
      cache: 'no-store',
      credentials: 'omit'
    }).then(function (r) {
      if (r.status === 204) return true;
      if (r.status === 401) return false;
      throw new Error('status ' + r.status);
    });
  }

  function forgetToken() {
    token = null;
    save(window.localStorage, TOKEN_KEY, null);
    save(window.sessionStorage, TERM_KEY, null);
  }

  $('signout').addEventListener('click', function () {
    var old = token;
    forgetToken();
    closeSocket();
    fetch('/api/logout', {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + old },
      cache: 'no-store',
      credentials: 'omit'
    }).catch(function () { /* the credential is gone from this page either way */ });
    showPairing('Signed out.');
  });

  // ----------------------------------------------------------- terminal

  var term = null;
  var fit = null;
  var socket = null;
  var pinger = null;
  var retryTimer = null;
  var disconnectedAt = 0;
  var hadTerminal = false;

  function ensureTerminal() {
    if (term) return;
    term = new ThothXterm.Terminal({
      cursorBlink: true,
      scrollback: 10000,
      fontSize: 14,
      // The bundled font (fonts.css), identical in every browser and OS.
      fontFamily: '"ThothTerm Mono", monospace',
      theme: { background: '#0d1117', foreground: '#e6edf3', cursor: '#e95420',
               selectionBackground: 'rgba(233, 84, 32, 0.35)' }
    });
    fit = new ThothXterm.FitAddon();
    term.loadAddon(fit);
    showOnly('terminal');
    term.open($('terminal'));

    term.onData(function (data) { send(encoder.encode(data)); });
    term.onBinary(function (data) {
      var bytes = new Uint8Array(data.length);
      for (var i = 0; i < data.length; ++i) bytes[i] = data.charCodeAt(i) & 0xff;
      send(bytes);
    });
    term.onResize(function (size) {
      sendControl({ type: 'resize', cols: size.cols, rows: size.rows });
    });
    term.attachCustomKeyEventHandler(function (e) {
      if (e.type !== 'keydown' || !e.ctrlKey || !e.shiftKey) return true;
      if (e.code === 'KeyC') { copySelection(); return false; }
      // Leave Ctrl+Shift+V to the browser, whose paste event xterm.js handles.
      if (e.code === 'KeyV') return false;
      return true;
    });
    new ResizeObserver(function () { if (!$('terminal').hidden) fit.fit(); }).observe($('terminal'));
    ThothRtl.attach(term, ThothXterm.Bidi);
  }

  function copySelection() {
    var text = term ? term.getSelection() : '';
    if (!text) return;
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(text).catch(function () { legacyCopy(text); });
    } else {
      // Plain http:// on a LAN address is not a secure context, so the
      // async clipboard API is unavailable; this still works on a user gesture.
      legacyCopy(text);
    }
    term.focus();
  }

  function legacyCopy(text) {
    var area = document.createElement('textarea');
    area.value = text;
    area.setAttribute('readonly', '');
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    try { document.execCommand('copy'); } catch (e) { /* nothing more to try */ }
    document.body.removeChild(area);
  }
  $('copy').addEventListener('click', copySelection);

  function send(bytes) {
    if (socket && socket.readyState === WebSocket.OPEN) socket.send(bytes);
  }

  function sendControl(message) {
    if (socket && socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify(message));
  }

  function closeSocket() {
    clearTimeout(retryTimer);
    clearInterval(pinger);
    if (socket) {
      socket.onclose = null;
      socket.close();
      socket = null;
    }
  }

  /**
   * xterm.js measures its cell from the font when it opens, so the bundled
   * font must be loaded first -- otherwise the grid is measured on a fallback.
   */
  var fontReady = null;
  function loadFont() {
    if (!fontReady) {
      var faces = ['400 14px "ThothTerm Mono"', '700 14px "ThothTerm Mono"'];
      fontReady = Promise.all(faces.map(function (f) {
        return document.fonts.load(f, 'M\u0628\u05d0');
      })).catch(function () { /* fall back to the generic monospace font */ });
    }
    return fontReady;
  }

  function startTerminal() {
    loadFont().then(openTerminal);
  }

  function openTerminal() {
    ensureTerminal();
    showOnly('terminal');
    fit.fit();
    connect();
  }

  function connect() {
    closeSocket();
    setState(disconnectedAt ? 'Reconnecting…' : 'Connecting…', 'wait');
    var ws;
    try {
      ws = new WebSocket('ws://' + location.host + '/ws', [PROTOCOL, 'auth.' + token]);
    } catch (e) {
      scheduleRetry();
      return;
    }
    ws.binaryType = 'arraybuffer';
    socket = ws;
    ws.onopen = function () {
      ws.send(JSON.stringify({
        type: 'open',
        term: load(window.sessionStorage, TERM_KEY),
        cols: term.cols,
        rows: term.rows
      }));
      pinger = setInterval(function () { sendControl({ type: 'ping' }); }, PING_MS);
    };
    ws.onmessage = function (event) {
      if (typeof event.data !== 'string') {
        term.write(new Uint8Array(event.data));
        return;
      }
      var message;
      try { message = JSON.parse(event.data); } catch (e) { return; }
      if (message.type === 'ready') {
        if (message.created && hadTerminal) term.reset();
        hadTerminal = true;
        save(window.sessionStorage, TERM_KEY, message.term);
        disconnectedAt = 0;
        setState('Connected', 'ok');
        showOnly('terminal');
        fit.fit();
        term.focus();
      }
    };
    ws.onclose = function (event) {
      clearInterval(pinger);
      socket = null;
      switch (event.code) {
        case 4000:
          save(window.sessionStorage, TERM_KEY, null);
          setState('Session ended', 'bad');
          notice('The shell exited.', 'Open a new terminal', startTerminal);
          return;
        case 4410:
          forgetToken();
          setState('LAN Mode off', 'bad');
          notice('LAN Mode was turned off on the phone. Turn it on again and pair this browser with a new PIN.',
            'Pair again', function () { showPairing(''); });
          return;
        case 4401:
          forgetToken();
          showPairing('This browser was signed out.');
          return;
        case 4429:
          setState('Limit reached', 'bad');
          notice('Too many terminals are open for this browser. Close one and try again.',
            'Try again', startTerminal);
          return;
        default:
          if (!disconnectedAt) disconnectedAt = Date.now();
          scheduleRetry();
      }
    };
  }

  function scheduleRetry() {
    if (Date.now() - disconnectedAt > RECONNECT_WINDOW_MS) {
      setState('Disconnected', 'bad');
      notice('The connection to the phone was lost.', 'Reconnect', function () {
        disconnectedAt = Date.now();
        startTerminal();
      });
      return;
    }
    setState('Reconnecting…', 'wait');
    retryTimer = setTimeout(function () {
      // A refused upgrade is invisible to page script, so ask whether the
      // credential is still good before trying again.
      checkToken().then(function (valid) {
        if (valid) connect();
        else {
          forgetToken();
          showPairing('LAN Mode was restarted on the phone. Pair this browser again.');
        }
      }).catch(scheduleRetry);
    }, 2000);
  }

  // -------------------------------------------------------------- start

  checkToken().then(function (valid) {
    if (valid) startTerminal();
    else {
      forgetToken();
      showPairing('');
    }
  }).catch(function () {
    showPairing('The phone did not answer. Is LAN Mode still on?');
  });
})();
