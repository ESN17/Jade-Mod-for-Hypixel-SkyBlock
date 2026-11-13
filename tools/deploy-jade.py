"""Run on the Sapphire web host: python3 deploy-jade.py /path/to/jade-site"""
import fcntl
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

site = Path("/www/wwwroot/sapphire.esn17cabin.club/jade")
config = Path("/www/server/panel/vhost/nginx/html_sapphire.esn17cabin.club.conf")
nginx = "/www/server/nginx/sbin/nginx"
stage = Path(sys.argv[1]).resolve()
routes = """    # Jade read-only music distribution
    location = /jade { return 301 /jade/; }
    location = /jade/ { rewrite ^ /jade/files.json last; }
    location = /jade/files.json { try_files $uri =404; default_type application/json; add_header Cache-Control no-cache; limit_except GET { deny all; } }
    location ~ "^/jade/audio/[a-f0-9]{64}$" { try_files $uri =404; default_type application/octet-stream; add_header Cache-Control "public, max-age=31536000, immutable"; limit_except GET { deny all; } }
    location ~ "^/jade/Wesnoth-(Credits|COPYING)\\.txt$" { try_files $uri =404; default_type text/plain; limit_except GET { deny all; } }
"""
def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()

def publish(source, dest):
    with tempfile.NamedTemporaryFile(dir=dest.parent, delete=False) as tmp:
        temp = Path(tmp.name)
    try:
        shutil.copyfile(source, temp)
        temp.chmod(0o644)
        os.replace(temp, dest)
    finally:
        if temp.exists():
            temp.unlink()

with open("/www/sapphire-deploy/jade.lock", "a") as lock:
    fcntl.flock(lock, fcntl.LOCK_EX)
    original = config.read_text()
    anchor = "    location = /style.css"
    if routes not in original and ("/jade" in original or original.count(anchor) != 1):
        sys.exit("Existing Jade routes or unknown virtual host layout")
    assets = json.loads((stage / "files.json").read_text())
    if not assets:
        sys.exit("No tracks")
    (site / "audio").mkdir(parents=True, exist_ok=True)
    for asset in assets.values():
        key = asset["sha256"]
        if not re.fullmatch("[a-f0-9]{64}", key):
            sys.exit("Invalid asset hash")
        dest = site / "audio" / key
        if dest.is_file() and digest(dest) == key:
            continue
        source = stage / "audio" / key
        if source.stat().st_size != asset["bytes"] or digest(source) != key:
            sys.exit("Invalid staged recording: " + key)
        publish(source, dest)
    for name in ("Wesnoth-COPYING.txt", "Wesnoth-Credits.txt", "files.json"):
        publish(stage / name, site / name)
    if routes not in original:
        backup = Path(tempfile.mkdtemp(prefix="jade-backup-", dir="/www/sapphire-deploy")) / "vhost.conf"
        shutil.copy2(config, backup)
        try:
            config.write_text(original.replace(anchor, routes + anchor))
            subprocess.run([nginx, "-t"], check=True)
            subprocess.run([nginx, "-s", "reload"], check=True)
        except BaseException:
            shutil.copy2(backup, config)
            subprocess.run([nginx, "-t"], check=True)
            subprocess.run([nginx, "-s", "reload"], check=True)
            raise
        print("vhost backup:", backup)
    print("Published", len(assets), "Jade tracks")

