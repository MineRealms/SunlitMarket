# -*- coding: utf-8 -*-
"""Minimal Rosetta remote client for the Sunlit Valley server bridge.

Usage:
  python tools/rosetta_server.py ping
  python tools/rosetta_server.py ls <dir>
  python tools/rosetta_server.py upload <local> <remote-relative>
  python tools/rosetta_server.py read <remote-relative> [max]
  python tools/rosetta_server.py tail <remote-relative> [lines]
  python tools/rosetta_server.py console <command...>
  python tools/rosetta_server.py exec <snippet-file>
"""
import base64
import io
import json
import os
import socket
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

CONFIG = os.path.join(os.path.dirname(os.path.abspath(__file__)), "rosetta.local.json")


def load_config():
    """Connection details live outside git: tools/rosetta.local.json or SM_ROSETTA_* variables."""
    data = {}
    if os.path.isfile(CONFIG):
        with open(CONFIG, encoding="utf-8") as handle:
            data = json.load(handle)
    host = os.environ.get("SM_ROSETTA_HOST", data.get("host", ""))
    port = int(os.environ.get("SM_ROSETTA_PORT", data.get("port", 0)) or 0)
    token = os.environ.get("SM_ROSETTA_TOKEN", data.get("token", ""))
    if not host or not port or not token:
        sys.exit("missing Rosetta connection: create tools/rosetta.local.json "
                 '({"host": ..., "port": ..., "token": ...}) or set SM_ROSETTA_HOST/PORT/TOKEN')
    return host, port, token


def call(cmd, args=None, timeout=180):
    host, port, token = load_config()
    sock = socket.create_connection((host, port), timeout=timeout)
    sock.settimeout(timeout)
    stream = sock.makefile("rwb")
    payload = {"token": token, "cmd": cmd}
    if args:
        payload["args"] = args
    stream.write((json.dumps(payload) + "\n").encode("utf-8"))
    stream.flush()
    raw = stream.readline().decode("utf-8", "replace")
    sock.close()
    if not raw:
        return {"ok": False, "error": "empty reply"}
    return json.loads(raw)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    verb = sys.argv[1]
    if verb == "ping":
        reply = call("ping", timeout=30)
    elif verb == "ls":
        reply = call("ls", {"dir": sys.argv[2] if len(sys.argv) > 2 else "."})
    elif verb == "upload":
        local, remote = sys.argv[2], sys.argv[3]
        with open(local, "rb") as handle:
            blob = base64.b64encode(handle.read()).decode("ascii")
        reply = call("upload", {"path": remote, "base64": blob}, timeout=600)
    elif verb == "read":
        reply = call("read", {"file": sys.argv[2], "offset": 0,
                              "max": int(sys.argv[3]) if len(sys.argv) > 3 else 200000})
        if reply.get("ok"):
            data = base64.b64decode(reply["result"].get("base64", ""))
            print(data.decode("utf-8", "replace"))
            return 0
    elif verb == "tail":
        reply = call("tail", {"file": sys.argv[2],
                              "lines": int(sys.argv[3]) if len(sys.argv) > 3 else 50})
        if reply.get("ok"):
            print(reply["result"].get("text", ""))
            return 0
    elif verb == "console":
        reply = call("console", {"command": " ".join(sys.argv[2:])}, timeout=60)
    elif verb == "exec":
        with open(sys.argv[2], encoding="utf-8") as handle:
            code = handle.read()
        reply = call("exec", {"code": code}, timeout=120)
    else:
        print("unknown verb " + verb)
        return 2
    print(json.dumps(reply, ensure_ascii=False))
    return 0 if reply.get("ok") else 1


if __name__ == "__main__":
    sys.exit(main())
