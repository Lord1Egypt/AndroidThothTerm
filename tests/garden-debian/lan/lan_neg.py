# LAN Mode negative tests over the raw protocol. Usage: lan_neg.py HOST PORT CURRENT_PIN
import os
# Consumes the current PIN (the lockout check ends with it).
import socket, sys, time, json
H, P, PIN = sys.argv[1], int(sys.argv[2]), sys.argv[3]
ORIGIN = 'http://%s:%d' % (H, P)
res = []
def check(name, ok, detail=''):
    res.append(ok); print(('PASS ' if ok else 'FAIL ') + name + ('  ' + str(detail) if detail != '' else ''), flush=True)
def raw(req):
    s = socket.create_connection((H, P), timeout=10); s.sendall(req.encode()); data = b''
    try:
        while True:
            c = s.recv(4096)
            if not c: break
            data += c
            if b'\r\n\r\n' in data and not data.startswith(b'HTTP/1.1 101'): break
    except socket.timeout: pass
    s.close(); line = data.split(b'\r\n', 1)[0].decode(errors='replace'); return int(line.split()[1]) if line else 0, data
def http(method, path, headers=None, body=''):
    h = {'Host': '%s:%d' % (H, P), 'Connection': 'close'}; h.update(headers or {})
    if body: h['Content-Length'] = str(len(body.encode()))
    return raw('%s %s HTTP/1.1\r\n%s\r\n%s' % (method, path, ''.join('%s: %s\r\n' % kv for kv in h.items()), body))[0]
WS = {'Upgrade': 'websocket', 'Connection': 'Upgrade', 'Sec-WebSocket-Version': '13', 'Sec-WebSocket-Key': 'dGhlIHNhbXBsZSBub25jZQ=='}
check('foreign Host header refused (DNS rebinding)', http('GET', '/', {'Host': 'evil.example:%d' % P}) == 421)
check('page served for the real Host', http('GET', '/') == 200)
check('pair without Origin refused', http('POST', '/api/pair', {'Content-Type': 'application/json'}, '{"pin":"000000"}') == 403)
check('pair from a foreign Origin refused', http('POST', '/api/pair', {'Content-Type': 'application/json', 'Origin': 'http://evil.example'}, '{"pin":"000000"}') == 403)
check('session without a token is 401', http('GET', '/api/session') == 401)
check('session with a forged token is 401', http('GET', '/api/session', {'Authorization': 'Bearer ' + 'A' * 43}) == 401)
code, data = raw('GET /ws HTTP/1.1\r\nHost: %s:%d\r\nOrigin: %s\r\n%sSec-WebSocket-Protocol: thothterm.v1\r\n\r\n' % (H, P, ORIGIN, ''.join('%s: %s\r\n' % kv for kv in WS.items())))
check('WebSocket without a token refused before upgrade', code == 401 and b'101' not in data.split(b'\r\n')[0], code)
code, data = raw('GET /ws HTTP/1.1\r\nHost: %s:%d\r\nOrigin: %s\r\n%sSec-WebSocket-Protocol: thothterm.v1, auth.%s\r\n\r\n' % (H, P, ORIGIN, ''.join('%s: %s\r\n' % kv for kv in WS.items()), 'B' * 43))
check('WebSocket with a forged token refused before upgrade', code == 401, code)
code, data = raw('GET /ws HTTP/1.1\r\nHost: %s:%d\r\nOrigin: http://evil.example\r\n%sSec-WebSocket-Protocol: thothterm.v1, auth.%s\r\n\r\n' % (H, P, ''.join('%s: %s\r\n' % kv for kv in WS.items()), 'B' * 43))
check('WebSocket from a foreign Origin refused before upgrade', code == 403, code)
code, _ = raw('GET /ws?token=%s HTTP/1.1\r\nHost: %s:%d\r\nOrigin: %s\r\n%sSec-WebSocket-Protocol: thothterm.v1\r\n\r\n' % ('C' * 43, H, P, ORIGIN, ''.join('%s: %s\r\n' % kv for kv in WS.items())))
check('a token in the URL is not accepted', code == 401, code)
check('path traversal is 404', http('GET', '/../../etc/passwd') == 404)
# Rate limit and lockout: five wrong PINs, then even the right one is refused.
J = {'Content-Type': 'application/json', 'Origin': ORIGIN}
wrong = '000000' if PIN != '000000' else '111111'
first = http('POST', '/api/pair', J, json.dumps({'pin': wrong}))
fast = http('POST', '/api/pair', J, json.dumps({'pin': wrong}))
check('a second attempt within a second is rate-limited', first == 401 and fast == 429, (first, fast))
codes = [first]
for _ in range(4):
    time.sleep(1.2); codes.append(http('POST', '/api/pair', J, json.dumps({'pin': wrong})))
time.sleep(1.2); right = http('POST', '/api/pair', J, json.dumps({'pin': PIN}))
check('five wrong PINs lock pairing; the right PIN is then refused', codes[-1] == 423 and right == 423, (codes, right))
print('ALL %d PASS' % len(res) if all(res) else 'SOME FAILED')
