"""Loopback-only RCON for an explicitly selected ignored development server."""
import socket
import struct
import sys
from pathlib import Path

properties = {}
for line in (Path(sys.argv[1]) / 'server.properties').read_text(encoding='utf-8').splitlines():
    if '=' in line and not line.startswith('#'):
        k, v = line.split('=', 1)
        properties[k] = v

def receive(sock):
    def exact(n):
        data = b''
        while len(data) < n:
            part = sock.recv(n-len(data))
            if not part:
                raise RuntimeError('RCON closed before response')
            data += part
        return data
    size = struct.unpack('<i', exact(4))[0]
    if size < 10 or size > 1048576:
        raise RuntimeError('Invalid RCON packet length')
    data = exact(size)
    return struct.unpack('<ii', data[:8]), data[8:-2].decode('utf-8', 'replace')

def send(sock, kind, text):
    body = struct.pack('<ii', 1, kind) + text.encode('utf-8') + b'\0\0'
    sock.sendall(struct.pack('<i', len(body)) + body)

with socket.create_connection(('127.0.0.1', int(properties['rcon.port'])), timeout=30) as sock:
    send(sock, 3, properties['rcon.password'])
    header, _ = receive(sock)
    if header[0] == -1:
        raise RuntimeError('RCON authentication refused')
    for command in sys.argv[2:]:
        send(sock, 2, command)
        _, result = receive(sock)
        print(command + ': ' + result)
