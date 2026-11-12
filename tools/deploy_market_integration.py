"""Deploy an explicit KubeJS/FTBQ file list, with remote backups and readback checks."""
import argparse
import base64
import hashlib
import json
from datetime import datetime
from pathlib import Path, PurePosixPath

from rosetta_server import call


def remote_bytes(path):
    response = call("read", {"file": path, "offset": 0, "max": 1048576}, timeout=30)
    if not response.get("ok"):
        raise RuntimeError(f"Read failed for {path}: {response.get('error')}")
    return base64.b64decode(response["result"]["base64"])


def digest(payload):
    return hashlib.sha256(payload).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pack", type=Path)
    parser.add_argument("files", type=Path, help="JSON array of paths relative to the pack root")
    parser.add_argument("--backup-root", type=Path, required=True)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    paths = json.loads(args.files.read_text(encoding="utf-8"))
    if len(paths) != len(set(paths)):
        raise ValueError("Duplicate deployment path")
    payloads = {}
    for name in paths:
        relative = PurePosixPath(name)
        if relative.is_absolute() or ".." in relative.parts or not (
            name.startswith("kubejs/") or name.startswith("config/ftbquests/")
        ):
            raise ValueError(f"Outside deployment scope: {name}")
        payloads[name] = (args.pack / name).read_bytes()
    backup = args.backup_root / datetime.now().strftime("%Y%m%d-%H%M%S-%f")
    backup.mkdir(parents=True, exist_ok=False)
    rows = []
    # Complete every backup before the first remote write.
    for name, wanted in payloads.items():
        old = remote_bytes(name)
        target = backup / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(old)
        rows.append({"path": name, "before": digest(old), "after": digest(wanted),
                     "changed": old != wanted})
    report = {"backup": str(backup), "applied": False, "files": rows}
    manifest = backup / "deployment.json"
    manifest.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if args.apply:
        for row in rows:
            name = row["path"]
            if row["changed"]:
                response = call("upload", {"path": name,
                    "base64": base64.b64encode(payloads[name]).decode("ascii")}, timeout=30)
                if not response.get("ok"):
                    raise RuntimeError(f"Upload failed for {name}: {response.get('error')}")
            if digest(remote_bytes(name)) != row["after"]:
                raise RuntimeError(f"Remote checksum mismatch: {name}")
        report["applied"] = True
        manifest.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"backup": str(backup), "files": len(rows),
        "changed": sum(row["changed"] for row in rows), "applied": report["applied"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
