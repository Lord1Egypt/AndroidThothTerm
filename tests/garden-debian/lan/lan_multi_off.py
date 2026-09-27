# Two paired browsers at once, then LAN Mode OFF on the phone while both are live.
import os
import re, subprocess, time, socket
from playwright.sync_api import sync_playwright
SC = os.environ.get('OUT', '.')
URL = 'http://' + os.environ.get('PHONE', '192.168.1.103') + ':7682/'
res = []
def check(n, ok, d=''):
    res.append(ok); print(('PASS ' if ok else 'FAIL ') + n + ('  ' + str(d) if d != '' else ''), flush=True)
def screen(pg):
    return pg.evaluate("() => [...document.querySelectorAll('.xterm-rows > div')].map(r => r.textContent.replace(/\\s+$/,'')).join('\\n')")
def run(pg, cmd, marker, t=20):
    pg.keyboard.type(cmd); pg.keyboard.press('Enter'); end = time.time() + t
    while time.time() < end and marker not in screen(pg): pg.wait_for_timeout(200)
    return screen(pg)
with sync_playwright() as p:
    b = p.chromium.launch(); pages = []
    for name in ('A', 'B'):
        ctx = b.new_context(viewport={'width': 1000, 'height': 500}, storage_state=SC + '/deb%s.json' % name)
        pg = ctx.new_page(); pg.goto(URL); pg.wait_for_selector('#state.ok', timeout=30000); pg.wait_for_timeout(1000)
        pg.focus('.xterm-helper-textarea'); pages.append(pg)
    a, bb = pages
    sa = run(a, 'clear; SECRET_A=alpha; sleep 4343 & echo "TTY=$(tty) JOB=$! DONE-A"', 'DONE-A')
    sb = run(bb, 'clear; sleep 4444 & echo "TTY=$(tty) JOB=$! DONE-B"; echo "seen:${SECRET_A:-none}"', 'seen:')
    ta = re.search(r'TTY=(\S+) JOB=(\d+)', sa); tb = re.search(r'TTY=(\S+) JOB=(\d+)', sb)
    check('two browsers get two different PTYs', ta and tb and ta.group(1) != tb.group(1), (ta and ta.group(1), tb and tb.group(1)))
    check("browser B does not see browser A's shell", 'seen:none' in sb)
    open(SC + '/offjobs.txt', 'w').write('%s %s\n' % (ta.group(2), tb.group(2)))
    subprocess.run([os.environ.get('TAP', 'tap.sh'), 'com.thothterm.debian/com.thothterm.lan.LanModeActivity', '947', '589'], check=True)
    t0 = time.time()
    for pg in pages: pg.wait_for_selector('#notice:not([hidden])', timeout=10000)
    check('both pages told LAN Mode stopped (%.1f s)' % (time.time() - t0), True, [pg.inner_text('#notice-text') for pg in pages])
    time.sleep(1)
    try:
        socket.create_connection(('' + os.environ.get('PHONE', '192.168.1.103') + '', 7682), timeout=3).close(); refused = False
    except OSError: refused = True
    check('nothing listens on 7682 after OFF', refused)
    pages[0].screenshot(path=SC + '/deb-lan-off.png')
    b.close()
print('ALL %d PASS' % len(res) if all(res) else 'SOME FAILED')
