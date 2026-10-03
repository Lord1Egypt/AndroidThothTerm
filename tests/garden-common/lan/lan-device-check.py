#!/usr/bin/env python3
"""LAN Mode checks against a phone, as a browser would talk to it.

    lan-device-check.py HOST PORT PIN STEP

STEP is one of:
  pair        pair with PIN; prints the token (also saved to ./lan-token)
  terminal    open a terminal over the WebSocket, run a command, print the term id
  upload      upload one file into the terminal's directory and finish
  cancel      start a 4 MiB file, send 1 MiB, cancel from a second connection:
              the PUT must end and the upload must be gone
  signout     start a 4 MiB file, send 1 MiB, sign out: the PUT must end, the
              token must stop working
Prints PASS/FAIL lines; exits 1 on any FAIL. Standard library only. The pairing
PIN is shown by the app; nothing here guesses or retries it.
"""
import base64
import hashlib
import json
import os
import socket
import struct
import sys
import time

HOST, PORT, PIN, STEP = sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4]
ORIGIN = "http://%s:%d" % (HOST, PORT)
TOKEN_FILE = "lan-token"
TERM_FILE = "lan-term"
failed = False


def check(ok, what):
    global failed
    print(("PASS " if ok else "FAIL ") + what)
    failed |= not ok


def connect():
    s = socket.create_connection((HOST, PORT), timeout=15)
    return s


def request(method, path, body=b"", headers=None, token=None):
    h = {"Host": "%s:%d" % (HOST, PORT), "Origin": ORIGIN, "Content-Length": str(len(body)),
         "Connection": "close"}
    if token:
        h["Authorization"] = "Bearer " + token
    h.update(headers or {})
    s = connect()
    s.sendall(("%s %s HTTP/1.1\r\n" % (method, path)).encode()
              + "".join("%s: %s\r\n" % kv for kv in h.items()).encode() + b"\r\n" + body)
    data = b""
    while True:
        chunk = s.recv(65536)
        if not chunk:
            break
        data += chunk
    s.close()
    head, _, rest = data.partition(b"\r\n\r\n")
    status = int(head.split(b" ")[1]) if head else 0
    return status, rest


def ws_open(token, term=None):
    s = connect()
    key = base64.b64encode(os.urandom(16)).decode()
    s.sendall(("GET /ws HTTP/1.1\r\nHost: %s:%d\r\nOrigin: %s\r\nUpgrade: websocket\r\n"
               "Connection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: %s\r\n"
               "Sec-WebSocket-Protocol: thothterm.v1, auth.%s\r\n\r\n"
               % (HOST, PORT, ORIGIN, key, token)).encode())
    head = b""
    while b"\r\n\r\n" not in head:
        head += s.recv(1)
    if b" 101 " not in head.split(b"\r\n")[0]:
        raise RuntimeError("upgrade refused: %r" % head.split(b"\r\n")[0])
    ws_send(s, 1, json.dumps({"type": "open", "term": term, "cols": 80, "rows": 24}).encode())
    return s


def ws_send(s, opcode, payload):
    mask = os.urandom(4)
    n = len(payload)
    head = bytes([0x80 | opcode])
    if n < 126:
        head += bytes([0x80 | n])
    elif n < 65536:
        head += bytes([0x80 | 126]) + struct.pack(">H", n)
    else:
        head += bytes([0x80 | 127]) + struct.pack(">Q", n)
    s.sendall(head + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(payload)))


def ws_recv(s):
    def exact(n):
        b = b""
        while len(b) < n:
            c = s.recv(n - len(b))
            if not c:
                raise EOFError
            b += c
        return b
    h = exact(2)
    opcode, n = h[0] & 0x0F, h[1] & 0x7F
    if n == 126:
        n = struct.unpack(">H", exact(2))[0]
    elif n == 127:
        n = struct.unpack(">Q", exact(8))[0]
    return opcode, exact(n)


def token():
    return open(TOKEN_FILE).read().strip()


def term():
    return open(TERM_FILE).read().strip()


def begin(tok, size):
    status, body = request("POST", "/api/upload/begin",
                           json.dumps({"term": term(), "kind": "files", "bytes": size}).encode(),
                           {"Content-Type": "application/json"}, tok)
    return status, (json.loads(body) if body.startswith(b"{") else {})


def partial_put(tok, upload, name, size, send):
    """Starts a PUT of SIZE bytes and sends SEND of them; returns the socket."""
    s = connect()
    s.sendall(("PUT /api/upload/file HTTP/1.1\r\nHost: %s:%d\r\nOrigin: %s\r\n"
               "Authorization: Bearer %s\r\nX-ThothTerm-Upload: %s\r\nX-ThothTerm-Path: %s\r\n"
               "Content-Type: application/octet-stream\r\nContent-Length: %d\r\n\r\n"
               % (HOST, PORT, ORIGIN, tok, upload, name, size)).encode())
    s.sendall(os.urandom(send))
    return s


def put_ended(s, seconds=10):
    """True when the server ends the PUT (a response, EOF or reset) within SECONDS."""
    s.settimeout(seconds)
    try:
        while True:
            # Keep writing: a stalled server would let the buffers fill and
            # this send would block until the timeout.
            s.sendall(os.urandom(16384))
    except (BrokenPipeError, ConnectionResetError):
        return True
    except socket.timeout:
        try:
            return s.recv(1) is not None
        except (socket.timeout, OSError):
            return False
    except OSError:
        return True


if STEP == "pair":
    status, body = request("POST", "/api/pair", json.dumps({"pin": PIN}).encode(),
                           {"Content-Type": "application/json"})
    check(status == 200, "pair with the PIN shown on the phone (HTTP %d)" % status)
    if status == 200:
        open(TOKEN_FILE, "w").write(json.loads(body)["token"])
        check(request("GET", "/api/session", token=token())[0] == 204, "session valid after pairing")
        check(request("GET", "/api/session", token="x" * 43)[0] == 401, "a made-up token is refused")
        bad = request("POST", "/api/pair", b'{"pin":"000000"}',
                      {"Content-Type": "application/json", "Origin": "http://evil.example"})[0]
        check(bad == 403, "pairing from a foreign Origin is refused (HTTP %d)" % bad)
elif STEP == "terminal":
    s = ws_open(token())
    op, data = ws_recv(s)
    ready = json.loads(data)
    check(ready.get("type") == "ready", "the terminal WebSocket opens: %s" % data.decode())
    open(TERM_FILE, "w").write(ready["term"])
    ws_send(s, 2, b"echo lan-$((6*7)); pwd\r")
    out = b""
    deadline = time.time() + 15
    s.settimeout(3)
    while b"lan-42" not in out and time.time() < deadline:
        try:
            op, data = ws_recv(s)
            out += data
        except socket.timeout:
            pass
    check(b"lan-42" in out, "the shell answers over LAN")
    s.close()
elif STEP == "upload":
    data = os.urandom(200000)
    status, b = begin(token(), len(data))
    check(status == 200, "upload begin (HTTP %d) target=%s" % (status, b.get("target")))
    up = b["upload"]
    status, body = request("PUT", "/api/upload/file", data,
                           {"X-ThothTerm-Upload": up, "X-ThothTerm-Path": "qa-lan-upload.bin",
                            "Content-Type": "application/octet-stream"}, token())
    check(status == 200, "file body received (HTTP %d %s)" % (status, body.decode()))
    status, body = request("POST", "/api/upload/finish", json.dumps({"upload": up}).encode(),
                           {"Content-Type": "application/json"}, token())
    check(status == 200, "upload finished (HTTP %d %s)" % (status, body.decode()))
    print("SHA256 qa-lan-upload.bin " + hashlib.sha256(data).hexdigest())
elif STEP in ("cancel", "signout"):
    tok = token()
    status, b = begin(tok, 4 << 20)
    check(status == 200, "upload begin (HTTP %d)" % status)
    up = b["upload"]
    s = partial_put(tok, up, "qa-lan-%s.bin" % STEP, 4 << 20, 1 << 20)
    time.sleep(1)
    if STEP == "cancel":
        status, _ = request("POST", "/api/upload/cancel", json.dumps({"upload": up}).encode(),
                            {"Content-Type": "application/json"}, tok)
        check(status == 204, "cancel accepted (HTTP %d)" % status)
    else:
        status, _ = request("POST", "/api/logout", b"", {"Content-Type": "application/json"}, tok)
        check(status == 204, "sign-out accepted (HTTP %d)" % status)
    check(put_ended(s), "the half-sent PUT is ended by the server, not left open")
    s.close()
    if STEP == "signout":
        check(request("GET", "/api/session", token=tok)[0] == 401, "the token no longer works")
        status, _ = begin(tok, 10)
        check(status == 401, "a new upload with the old token is refused (HTTP %d)" % status)
sys.exit(1 if failed else 0)
