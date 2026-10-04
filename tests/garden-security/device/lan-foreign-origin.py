"""Foreign-origin and foreign-host checks against a paired LAN Mode, as a hostile web page would try.

    lan-foreign-origin.py HOST PORT

Needs the lan-token file that lan-device-check.py pair writes (a paired browser). Every
refused request must leave that token working.
"""
import socket, json, sys
H, P = sys.argv[1], int(sys.argv[2])
tok = open("lan-token").read().strip()
def raw(lines, body=b""):
    s = socket.create_connection((H, P), timeout=10)
    s.sendall(("\r\n".join(lines) + "\r\n\r\n").encode() + body)
    d = b""
    while True:
        try: c = s.recv(65536)
        except socket.timeout: break
        if not c: break
        d += c
    s.close()
    return int(d.split(b" ")[1]) if d else 0
fail = False
def check(ok, w):
    global fail; print(("PASS " if ok else "FAIL ") + w); fail |= not ok
auth = "Authorization: Bearer " + tok
good = "Origin: http://%s:%d" % (H, P)
for origin in ("http://evil.example", "http://192.168.1.50:7684", "null"):
    body = json.dumps({"size": 10}).encode()
    st = raw(["POST /api/upload/begin HTTP/1.1", "Host: %s:%d" % (H, P), "Origin: " + origin, auth,
              "Content-Type: application/json", "Content-Length: %d" % len(body), "Connection: close"], body)
    check(st == 403, "foreign Origin %r on upload begin refused (HTTP %d)" % (origin, st))
    st = raw(["GET /ws HTTP/1.1", "Host: %s:%d" % (H, P), "Origin: " + origin, "Upgrade: websocket", "Connection: Upgrade",
              "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==", "Sec-WebSocket-Version: 13", auth])
    check(st != 101, "foreign Origin %r WebSocket upgrade not accepted (HTTP %d)" % (origin, st))
st = raw(["GET /api/session HTTP/1.1", "Host: evil.example:%d" % P, good, auth, "Connection: close"])
check(st in (400, 403, 421), "foreign Host header (DNS rebinding) refused (HTTP %d)" % st)
for path in ("/api/upload/cancel", "/api/upload/finish", "/api/logout"):
    body = json.dumps({"upload": "x"}).encode()
    st = raw(["POST %s HTTP/1.1" % path, "Host: %s:%d" % (H, P), "Origin: http://evil.example", auth,
              "Content-Type: application/json", "Content-Length: %d" % len(body), "Connection: close"], body)
    check(st == 403, "foreign Origin on POST %s refused (HTTP %d)" % (path, st))
st = raw(["GET /api/session HTTP/1.1", "Host: %s:%d" % (H, P), good, auth, "Connection: close"])
check(st == 204, "control: the token still works after every refused request (HTTP %d)" % st)
sys.exit(1 if fail else 0)
