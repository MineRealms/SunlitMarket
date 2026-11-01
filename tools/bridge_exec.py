# -*- coding: utf-8 -*-
"""Run a Java snippet inside a live Rosetta dev client.

Usage: python tools/bridge_exec.py <port> <game-dir> <snippet-file>

The snippet is a Java body (no imports needed for fully qualified names);
it runs on the client thread and its return value is printed.

Examples:
  reload.kubejs.txt:
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      net.minecraft.server.MinecraftServer s = mc.getSingleplayerServer();
      s.execute(() -> s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "reload"));
      return "reload scheduled";
"""
import io
import json
import os
import socket
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

port = int(sys.argv[1])
gamedir = sys.argv[2]
code = open(sys.argv[3], encoding="utf-8").read()
token = open(os.path.join(gamedir, "RosettaRemoteDebugBridge", "remote-token.txt"), encoding="utf-8").read().strip()

sock = socket.create_connection(("127.0.0.1", port), timeout=60)
stream = sock.makefile("rwb")
stream.write((json.dumps({"token": token, "cmd": "exec", "args": {"code": code}}) + "\n").encode("utf-8"))
stream.flush()
reply = json.loads(stream.readline().decode("utf-8", "replace"))
sock.close()

if reply.get("ok"):
    print(reply.get("result", {}).get("result", "ok"))
else:
    print("ERROR:", reply.get("error"), reply.get("exception", ""), file=sys.stderr)
    sys.exit(1)
