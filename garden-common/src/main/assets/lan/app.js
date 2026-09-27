/*
 * ThothTerm Garden -- LAN Terminal page.
 *
 * Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0.
 *
 * The browser credential is a bearer token kept in this origin's
 * localStorage. It is sent only in the WebSocket handshake's
 * Sec-WebSocket-Protocol header and in an Authorization header -- never in
 * a URL, never as a cookie. Each tab remembers its own terminal id in
 * sessionStorage, so a reload reattaches to the same shell.
 *
 * Uploads go to the current folder of this tab's terminal, which the phone
 * reads from the terminal's own session; the page only names the terminal
 * and sends the files, one streamed request each (XMLHttpRequest, for its
 * upload progress). Browsers send no empty folders, so none arrive.
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
    $('upload-files').hidden = !inTerminal;
    $('upload-folder').hidden = !inTerminal;
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
    abandonUpload('This browser was signed out.');
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

  /** A #rrggbb custom property from the stylesheets. */
  function cssColor(name) {
    return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  }

  function ensureTerminal() {
    if (term) return;
    term = new ThothXterm.Terminal({
      cursorBlink: true,
      scrollback: 10000,
      fontSize: 14,
      // The bundled font (fonts.css), identical in every browser and OS.
      fontFamily: '"ThothTerm Mono", monospace',
      // The edition's defaults (edition.css). Programs' own colours are
      // untouched: the ANSI palette is xterm's standard one, as on the phone.
      theme: { background: cssColor('--term-bg'), foreground: cssColor('--term-fg'),
               cursor: cssColor('--term-cursor'), cursorAccent: cssColor('--term-bg'),
               selectionBackground: cssColor('--term-cursor') + '59' }
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
          abandonUpload('LAN Mode was turned off on the phone.');
          forgetToken();
          setState('LAN Mode off', 'bad');
          notice('LAN Mode was turned off on the phone. Turn it on again and pair this browser with a new PIN.',
            'Pair again', function () { showPairing(''); });
          return;
        case 4401:
          abandonUpload('This browser was signed out.');
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

  // ------------------------------------------------------------ uploads

  var UPLOAD_ERRORS = {
    unauthorized: 'This browser is no longer signed in.',
    forbidden: 'The phone refused the upload.',
    no_terminal: 'This terminal is no longer open on the phone.',
    no_upload: 'The upload was stopped on the phone.',
    no_directory: 'The phone cannot find this terminal\u2019s current folder.',
    outside: 'Uploads cannot go to this folder.',
    directory_gone: 'The current folder no longer exists.',
    not_writable: 'Cannot upload to this folder.',
    no_space: 'Not enough storage space on the phone.',
    bad_name: 'A file or folder name cannot be used',
    conflict: 'Could not find a free name to keep both files.',
    busy: 'Another upload is already running for this terminal.',
    cancelled: 'The upload was stopped.',
    io: 'The upload failed.'
  };

  var upload = null;

  function formatBytes(n) {
    var units = ['B', 'KB', 'MB', 'GB', 'TB'];
    var i = 0;
    while (n >= 1000 && i < units.length - 1) { n /= 1000; ++i; }
    return (i === 0 ? n : n.toFixed(n < 10 ? 1 : 0)) + ' ' + units[i];
  }

  function plural(n, one, many) { return n + ' ' + (n === 1 ? one : many); }

  function authHeaders(json) {
    var h = { Authorization: 'Bearer ' + token };
    if (json) h['Content-Type'] = 'application/json';
    return h;
  }

  function api(path, body) {
    return fetch(path, {
      method: 'POST', headers: authHeaders(true), body: JSON.stringify(body),
      cache: 'no-store', credentials: 'omit'
    }).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (b) {
        b.status = r.status;
        return b;
      });
    });
  }

  function explain(code, name) {
    var text = UPLOAD_ERRORS[code] || 'The upload failed.';
    return code === 'bad_name' && name ? text + ': ' + name : text + (code === 'bad_name' ? '.' : '');
  }

  function showUpload(title) {
    $('upload-title').textContent = title;
    $('upload-what').textContent = '';
    $('upload-where').hidden = true;
    $('upload-progress').hidden = true;
    $('upload-message').textContent = '';
    $('upload-message').className = '';
    $('upload-go').hidden = true;
    $('upload-cancel').textContent = 'Cancel';
    $('upload').hidden = false;
    $('upload-cancel').focus();
  }

  function uploadMessage(text, bad) {
    $('upload-message').textContent = text;
    $('upload-message').className = bad ? 'bad' : '';
  }

  function closeUpload() {
    $('upload').hidden = true;
    upload = null;
    if (term) term.focus();
  }

  /** The dialog stays with the outcome; its button only closes it now. */
  function settle(text, bad) {
    if (upload) upload.settled = true;
    $('upload-go').hidden = true;
    $('upload-cancel').textContent = 'Close';
    uploadMessage(text, bad);
  }

  function pick(kind) {
    if (upload) return;
    var terminalId = load(window.sessionStorage, TERM_KEY);
    if (!token || !terminalId) return;
    var input = $(kind === 'folder' ? 'pick-folder' : 'pick-files');
    input.value = '';
    input.click();
  }

  $('upload-files').addEventListener('click', function () { pick('files'); });
  $('upload-folder').addEventListener('click', function () { pick('folder'); });
  $('pick-files').addEventListener('change', function () { begin('files', this.files); });
  $('pick-folder').addEventListener('change', function () { begin('folder', this.files); });

  /** List the picked files, then ask the phone where they would go. */
  function begin(kind, list) {
    if (upload) return;
    var files = Array.prototype.slice.call(list || []);
    var folder = kind === 'folder';
    showUpload(folder ? 'Upload folder' : 'Upload files');
    upload = { kind: kind, items: [], total: 0, id: null, xhr: null, settled: false, started: false,
      index: 0, names: [] };
    if (!files.length) {
      settle(folder ? 'That folder has no files. Browsers do not send empty folders.' : 'No files were chosen.');
      return;
    }
    var root = null;
    for (var i = 0; i < files.length; ++i) {
      var f = files[i];
      var path = f.name;
      if (folder) {
        var parts = (f.webkitRelativePath || '').split('/');
        if (parts.length < 2 || (root !== null && parts[0] !== root)) {
          settle('This browser did not give the folder\u2019s structure.', true);
          return;
        }
        root = parts[0];
        path = parts.slice(1).join('/');
      }
      upload.items.push({ file: f, path: path });
      upload.total += f.size;
    }
    upload.name = root;
    var count = plural(files.length, 'file', 'files') + ' (' + formatBytes(upload.total) + ')';
    $('upload-what').textContent = folder ? 'Folder \u201c' + root + '\u201d \u2014 ' + count : count;
    uploadMessage('Asking the phone for this terminal\u2019s current folder\u2026');
    var current = upload;
    api('/api/upload/begin', {
      term: load(window.sessionStorage, TERM_KEY), kind: kind, name: root, bytes: current.total
    }).then(function (r) {
      if (upload !== current) {
        if (r.upload) api('/api/upload/cancel', { upload: r.upload }).catch(function () {});
        return;
      }
      if (r.status !== 200 || !r.upload) {
        settle(explain(r.error, root), true);
        return;
      }
      current.id = r.upload;
      $('upload-target').textContent = r.target;
      $('upload-where').hidden = false;
      uploadMessage('');
      $('upload-go').hidden = false;
      $('upload-go').focus();
    }).catch(function () {
      if (upload === current) settle('The phone did not answer. Is LAN Mode still on?', true);
    });
  }

  $('upload-go').addEventListener('click', function () {
    if (!upload || !upload.id || upload.xhr || upload.settled) return;
    $('upload-go').hidden = true;
    $('upload-progress').hidden = false;
    upload.started = true;
    upload.done = 0;
    sendNext(upload);
  });

  function progress(u, loaded) {
    var done = u.done + loaded;
    $('upload-bar').value = u.total > 0 ? Math.round(done * 1000 / u.total) : 0;
    $('upload-bytes').textContent = formatBytes(done) + ' of ' + formatBytes(u.total)
      + (u.total > 0 ? ' (' + Math.floor(done * 100 / u.total) + '%)' : '')
      + ' \u00b7 file ' + Math.min(u.index + 1, u.items.length) + ' of ' + u.items.length;
  }

  function sendNext(u) {
    if (upload !== u || u.settled) return;
    if (u.index >= u.items.length) {
      finish(u);
      return;
    }
    var item = u.items[u.index];
    $('upload-name').textContent = item.path;
    progress(u, 0);
    var xhr = new XMLHttpRequest();
    u.xhr = xhr;
    xhr.open('PUT', '/api/upload/file');
    xhr.setRequestHeader('Authorization', 'Bearer ' + token);
    xhr.setRequestHeader('Content-Type', 'application/octet-stream');
    xhr.setRequestHeader('X-ThothTerm-Upload', u.id);
    xhr.setRequestHeader('X-ThothTerm-Path', item.path.split('/').map(encodeURIComponent).join('/'));
    xhr.upload.onprogress = function (e) { if (upload === u) progress(u, e.loaded); };
    xhr.onload = function () {
      u.xhr = null;
      var body = {};
      try { body = JSON.parse(xhr.responseText); } catch (e) { /* not JSON */ }
      if (upload !== u) return;
      if (xhr.status !== 200) {
        fail(u, explain(body.error, item.path));
        return;
      }
      if (u.kind === 'files' && body.name !== item.path) u.names.push(body.name);
      u.done += item.file.size;
      u.index += 1;
      sendNext(u);
    };
    xhr.onerror = function () {
      u.xhr = null;
      if (upload === u) fail(u, 'The connection to the phone was lost.');
    };
    // The browser streams the File from disk; it is never read into memory here.
    xhr.send(item.file);
  }

  function fail(u, text) {
    if (u.id) api('/api/upload/cancel', { upload: u.id }).catch(function () {});
    settle(text + (u.kind === 'folder' ? ' Nothing was added.' : ''), true);
  }

  function finish(u) {
    api('/api/upload/finish', { upload: u.id }).then(function (r) {
      if (upload !== u) return;
      if (r.status !== 200) {
        settle(explain(r.error), true);
        return;
      }
      $('upload-bar').value = 1000;
      var where = $('upload-target').textContent;
      if (u.kind === 'folder') {
        settle('Uploaded \u201c' + r.name + '\u201d to ' + where + '.');
      } else {
        var text = 'Uploaded ' + plural(u.items.length, 'file', 'files') + ' to ' + where + '.';
        if (u.names.length) text += ' Kept both, as: ' + u.names.join(', ') + '.';
        settle(text);
      }
    }).catch(function () {
      if (upload === u) settle('The phone did not answer. Is LAN Mode still on?', true);
    });
  }

  /** Stop the upload in progress, if any; the phone removes what it had not finished. */
  function abandonUpload(why) {
    var u = upload;
    if (!u || u.settled) return;
    if (u.xhr) {
      u.xhr.onerror = null;
      u.xhr.abort();
      u.xhr = null;
    }
    if (u.id && token) api('/api/upload/cancel', { upload: u.id }).catch(function () {});
    settle(why, true);
  }

  $('upload-cancel').addEventListener('click', function () {
    if (upload && !upload.settled && !upload.started) {
      // Nothing was sent yet: just tell the phone and close.
      if (upload.id) api('/api/upload/cancel', { upload: upload.id }).catch(function () {});
      closeUpload();
      return;
    }
    if (upload && !upload.settled) {
      abandonUpload('Upload cancelled. Nothing partial was left behind.');
      return;
    }
    closeUpload();
  });

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && !$('upload').hidden) $('upload-cancel').click();
  });

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
