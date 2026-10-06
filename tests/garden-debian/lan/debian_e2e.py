# Browser acceptance against the phone's LAN Mode. Usage: phone_e2e2.py PIN
import os
import sys, re, json, time
from playwright.sync_api import sync_playwright
PORT = sys.argv[2] if len(sys.argv) > 2 else '7682'
ORIGIN = 'http://' + os.environ.get('PHONE', '192.168.1.103') + ':' + PORT
PIN = sys.argv[1]
SC = os.environ.get('OUT', '.')
results = []
def check(name, ok, detail=''):
    results.append((name, bool(ok))); print(('PASS ' if ok else 'FAIL ') + name + ('  ' + str(detail) if detail != '' else ''), flush=True)
def screen(pg):
    return pg.evaluate("() => [...document.querySelectorAll('.xterm-rows > div')].map(r => r.textContent.replace(/\\s+$/,'')).join('\\n')")
def run(pg, cmd, marker, timeout=30):
    pg.keyboard.type(cmd); pg.keyboard.press('Enter')
    end = time.time() + timeout
    while time.time() < end:
        s = screen(pg)
        if marker in s: return s
        pg.wait_for_timeout(200)
    return screen(pg)
def wait_ok(pg):
    pg.wait_for_selector('#state.ok', timeout=30000); pg.wait_for_timeout(1200); pg.focus('.xterm-helper-textarea')
with sync_playwright() as p:
    b = p.chromium.launch()
    ctx = b.new_context(viewport={'width': 1100, 'height': 620})
    # Plain http on a LAN address is not a secure context, so the page copies
    # through a hidden textarea and execCommand('copy'): record what it copies.
    ctx.add_init_script("""window.__copied = [];
        const execCommand = document.execCommand.bind(document);
        document.execCommand = function (command, ...rest) {
            if (command === 'copy') { const el = document.activeElement; window.__copied.push(el && 'value' in el ? el.value : ''); }
            return execCommand(command, ...rest);
        };""")
    pg = ctx.new_page(); errors = []
    pg.on('pageerror', lambda e: errors.append(str(e)))
    pg.goto(ORIGIN + '/'); pg.wait_for_selector('#pin', state='visible', timeout=15000)
    check('pairing page first, no shell before auth', pg.is_visible('#pin') and not pg.is_visible('.xterm-rows'))
    check('page names the product: ThothTerm • ThothDock / LAN Terminal', pg.title() == 'ThothTerm • ThothDock — LAN Terminal' and pg.inner_text('.brand') == 'ThothTerm • ThothDock' and pg.inner_text('.sub') == 'LAN Terminal', (pg.title(), pg.inner_text('.brand')))
    pg.fill('#pin', '000000' if PIN != '000000' else '111111'); pg.click('#pair-form button'); pg.wait_for_timeout(1500)
    check('wrong PIN rejected', 'Wrong PIN' in pg.inner_text('#pair-error'), pg.inner_text('#pair-error'))
    pg.wait_for_timeout(1100); pg.fill('#pin', PIN); pg.click('#pair-form button'); wait_ok(pg)
    check('correct PIN pairs, terminal connects', True)
    pg.wait_for_timeout(1100)
    reuse = pg.evaluate("pin => fetch('/api/pair', {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({pin})}).then(r => r.status)", PIN)
    check('PIN cannot be reused', reuse == 409, reuse)
    tok = pg.evaluate("localStorage.getItem('thothterm.lan.token')")
    check('token never in the URL', tok and tok not in pg.url)
    s = run(pg, 'clear; whoami; pwd; echo MARK-$((6*7))', 'MARK-42')
    check('whoami thoth, pwd /home/thoth', re.search(r'^thoth$', s, re.M) and '/home/thoth' in s)
    s = run(pg, 'grep -E "^PRETTY_NAME=" /etc/os-release; tty; echo TTYDONE', 'TTYDONE')
    m = re.search(r'/dev/pts/\d+', s)
    check('Debian 13 trixie on a real PTY', 'Debian GNU/Linux 13 (trixie)' in s and m, m.group(0) if m else '')
    s = run(pg, 'clear; sudo -n id -un; echo SUDODONE', 'SUDODONE')
    check('sudo -n id -un = root in the browser terminal', re.search(r'^root$', s, re.M))
    browser_tty = m.group(0) if m else ''
    s = run(pg, 'clear; echo "سلام عليكم"; echo -n "سلام" | od -An -tx1; echo UTFDONE', 'UTFDONE')
    check('Arabic round-trips through the PTY in logical UTF-8', 'd8 b3 d9 84 d8 a7 d9 85' in s)
    strings = ['سلام عليكم', 'مرحبا بالعالم', 'ThothTerm Trixie مرحبا', 'الإصدار 0.1.0', 'test (مرحبا) 123']
    for t in strings:
        k = strings.index(t)
        s = run(pg, 'clear; echo "%s"; echo -n "%s" | od -An -tx1 | tr -d " \\n"; echo; echo ARDONE-$((%d+100))' % (t, t, k), 'ARDONE-%d' % (k + 100))
        hexpect = t.encode().hex()
        check('logical UTF-8 through the PTY: ' + t, hexpect in s.replace(' ', ''), '')
        # An RTL-first line is drawn with its runs in visual order (UBA), e.g.
        # "0.1.0 الإصدار"; each run stays intact and shaped.
        row = [i for i, l in enumerate(screen(pg).split('\n')) if l.strip() == t or (len(l.replace(' ', '')) == len(t.replace(' ', '')) and all(w in l for w in t.split()))]
        check('drawn in the browser: ' + t, bool(row), '' if row else [repr(l) for l in screen(pg).split('\n')[:2]])
        pg.screenshot(path=SC + '/deb-ar-%d.png' % strings.index(t))
    s = run(pg, "clear; printf 'مرحبا\\n'; echo PFDONE", 'PFDONE')
    check("printf 'مرحبا\\n' prints one logical line", 'مرحبا' in [l.strip() for l in screen(pg).split('\n')])
    s = run(pg, "clear; printf '\\033[1;31mمرحبا\\033[0m \\033[32mسلام\\033[0m\\n'; echo ANSIDONE", 'ANSIDONE')
    check('ANSI-coloured Arabic renders', 'مرحبا سلام' in screen(pg))
    pg.screenshot(path=SC + '/deb-ar-ansi.png')
    s = run(pg, 'clear; echo "سلام عليكم"; echo UTF2', 'UTF2')
    rows = pg.locator('.xterm-rows > div')
    idx = [i for i, t in enumerate(screen(pg).split('\n')) if t.strip() == 'سلام عليكم']
    check('Arabic row drawn by the RTL overlay', idx and pg.evaluate("i => !!document.querySelectorAll('.xterm-rows > div')[i].querySelector('span[style*=\"scaleX\"]')", idx[0]))
    pg.screenshot(path=SC + '/deb-e2e-arabic.png')
    # Logical copy: select the Arabic line, Ctrl+Shift+C, read the clipboard.
    box = rows.nth(idx[0]).bounding_box()
    pg.mouse.click(box['x'] + 40, box['y'] + box['height'] / 2, click_count=3); pg.wait_for_timeout(300)
    pg.keyboard.press('Control+Shift+KeyC'); pg.wait_for_timeout(500)
    clip = pg.evaluate("(window.__copied.slice(-1)[0]) || ''")
    check('the page is not a secure context (legacy copy path in use)', not pg.evaluate("window.isSecureContext"))
    check('copy gives logical Arabic text', clip.strip() == 'سلام عليكم', repr(clip))
    pg.focus('.xterm-helper-textarea')
    # Logical paste: a real paste event into xterm.js reaches the PTY in logical order.
    pg.evaluate("""t => { const dt = new DataTransfer(); dt.setData('text/plain', t);
        document.querySelector('.xterm-helper-textarea').dispatchEvent(new ClipboardEvent('paste', {clipboardData: dt, bubbles: true, cancelable: true})); }""",
        'clear; echo -n "مرحبا" | od -An -tx1; echo PASTE-$((5*5))')
    pg.wait_for_timeout(500); pg.keyboard.press('Enter')
    end = time.time() + 20
    while time.time() < end and 'PASTE-25' not in screen(pg): pg.wait_for_timeout(200)
    check('paste reaches the PTY in logical order', 'd9 85 d8 b1 d8 ad d8 a8 d8 a7' in screen(pg))
    s = run(pg, 'clear; stty size; echo S1DONE', 'S1DONE'); before = re.findall(r'^(\d+) (\d+)$', s, re.M)[-1]
    pg.set_viewport_size({'width': 780, 'height': 460}); pg.wait_for_timeout(1500)
    s = run(pg, 'stty size; echo S2DONE', 'S2DONE'); after = re.findall(r'^(\d+) (\d+)$', s, re.M)[-1]
    check('browser resize changes the real PTY size (stty)', before != after, (before, after))
    pg.keyboard.type('sleep 300'); pg.keyboard.press('Enter'); pg.wait_for_timeout(1500)
    pg.keyboard.press('Control+c'); pg.wait_for_timeout(1500)
    s = run(pg, 'echo "after-ctrl-c $((0+$?))"', 'after-ctrl-c 1')
    # A job killed by SIGINT exits 128+2.
    check('Ctrl-C interrupts the foreground job (SIGINT, status 130)', '^C' in s and 'after-ctrl-c 130' in s, [l for l in s.split('\n') if 'after-ctrl-c' in l][-1:])
    run(pg, 'clear; seq 1 3000; echo SEQ-$((1+1))', 'SEQ-2', 40)
    for _ in range(160):
        pg.keyboard.press('Shift+PageUp')
    pg.wait_for_timeout(500)
    visible = set(screen(pg).split('\n'))
    check('scrollback reaches back to line 1 of 3000', '1' in visible and '2' in visible, sorted((v for v in visible if v.isdigit()), key=int)[:3])
    for _ in range(160):
        pg.keyboard.press('Shift+PageDown')

    run(pg, 'clear; echo BEFORE-RELOAD-$((40+2))', 'BEFORE-RELOAD-42')
    term_id = pg.evaluate("sessionStorage.getItem('thothterm.lan.term')")
    pg.reload(); wait_ok(pg)
    check('reconnect reattaches the same terminal', pg.evaluate("sessionStorage.getItem('thothterm.lan.term')") == term_id)
    s = run(pg, 'tty; echo AFTER-RELOAD', 'AFTER-RELOAD')
    check('same shell after reconnect (same pts)', browser_tty and browser_tty in s, browser_tty)
    s = run(pg, "clear; ps -eo tty,comm | grep -E ' bash$' | sort -u; echo PSDONE", 'PSDONE')
    ttys = sorted(set(re.findall(r'pts/\d+', s)))
    check('browser PTY is separate from the phone window PTY', len(ttys) >= 2 and browser_tty.replace('/dev/', '') in ttys, ttys)
    pg.screenshot(path=SC + '/deb-e2e-final.png')
    # Sign out: the page forgets the token and the server revokes it.
    tok = pg.evaluate("localStorage.getItem('thothterm.lan.token')")
    with pg.expect_response(lambda r: r.url.endswith('/api/logout')) as logout:
        pg.click('#signout')
    check('logout request answered 204', logout.value.status == 204, logout.value.status)
    pg.wait_for_selector('#pin', state='visible', timeout=10000)
    status = pg.evaluate("t => fetch('/api/session', {headers: {Authorization: 'Bearer ' + t}}).then(r => r.status)", tok)
    check('sign-out revokes the token', status == 401 and not pg.evaluate("localStorage.getItem('thothterm.lan.token')"), status)
    check('no page errors', not errors, errors)
    b.close()
print('ALL %d PASS' % len(results) if all(ok for _, ok in results) else 'FAILED: ' + ', '.join(n for n, ok in results if not ok))
