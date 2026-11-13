"""Run in the staged release directory on the existing Jade web host."""
import fcntl
import hashlib
import html
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as src:
        for chunk in iter(lambda: src.read(1024*1024), b""):
            h.update(chunk)
    return h.hexdigest()

def install(src, target):
    with tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as temp:
        path = Path(temp.name)
    try:
        shutil.copyfile(src, path)
        path.chmod(0o644)
        os.replace(path, target)
    finally:
        if path.exists():
            path.unlink()

stage = Path(__file__).resolve().parent
version, edition = sys.argv[1:]
if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version) or not re.fullmatch(r"[0-9]{8}_[1-9][0-9]*", edition):
    sys.exit("Invalid release or library version")
web = Path("/www/wwwroot/sapphire.esn17cabin.club")
jade = web / "jade"
conf = Path("/www/server/panel/vhost/nginx/html_sapphire.esn17cabin.club.conf")
home = web / "index.php"
nginx = "/www/server/nginx/sbin/nginx"
routes = r'''    # Jade web releases
    location ~ "^/jade/releases/jade-[0-9.]+\+mc[0-9.]+\.jar$" { try_files $uri =404; default_type application/java-archive; limit_except GET { deny all; } }
    location = /jade/library/index.json { try_files $uri =404; default_type application/json; add_header Cache-Control no-cache; }
    location ~ "^/jade/library/[0-9]{8}_[1-9][0-9]*\.zip$" { try_files $uri =404; default_type application/zip; limit_except GET { deny all; } }
'''
git = "https://github.com/ESN17/Jade-Mod-for-Hypixel-SkyBlock/releases/tag/v" + version
with open("/www/sapphire-deploy/jade.lock", "a") as lock:
    fcntl.flock(lock, fcntl.LOCK_EX)
    index = json.loads((jade/"files.json").read_text())
    if index != json.loads((stage/"files.json").read_text()):
        sys.exit("The server's music index differs from this release")
    downloads = jade/"releases"
    library = jade/"library"
    downloads.mkdir(exist_ok=True)
    library.mkdir(exist_ok=True)
    links = []
    for game in ("26.1.2", "26.2", "26.3"):
        name = "jade-" + version + "+mc" + game + ".jar"
        src = stage/name
        with zipfile.ZipFile(src) as archive:
            meta = json.loads(archive.read("fabric.mod.json"))
            if meta["version"] != version+"+mc"+game or meta["depends"]["minecraft"] != "="+game:
                sys.exit("Invalid artifact: "+name)
        if (downloads/name).exists() and digest(downloads/name) != digest(src):
            sys.exit("Release filename already used")
        install(src, downloads/name)
        links.append('<a href="/jade/releases/'+name+'">下载 Minecraft '+game+' 版</a>')
    package = library/(edition+".zip")
    if not package.exists():
        tmp = stage/(edition+".zip")
        with zipfile.ZipFile(tmp, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name, data in sorted(index.items()):
                if Path(name).name != name or not re.fullmatch("[a-f0-9]{64}", data["sha256"]):
                    sys.exit("Invalid music index")
                src = jade/"audio"/data["sha256"]
                if src.stat().st_size != data["bytes"] or digest(src) != data["sha256"]:
                    sys.exit("Invalid audio: "+name)
                archive.write(src, "Jade Defaults/"+name)
            for name in ("Wesnoth-COPYING.txt", "Wesnoth-Credits.txt"):
                archive.write(jade/name, "Jade Defaults/"+name)
        install(tmp, package)
    index_path = library/"index.json"
    entries = json.loads(index_path.read_text())["files"] if index_path.exists() else []
    record = dict(name=package.name, bytes=package.stat().st_size, sha256=digest(package), tracks=len(index), url="/jade/library/"+package.name)
    entries = [e for e in entries if e["name"] != package.name] + [record]
    entries.sort(key=lambda e: tuple(int(x) for x in e["name"][:-4].split("_")), reverse=True)
    release_index = stage/"library-index.json"
    release_index.write_text(json.dumps(dict(latest=entries[0]["name"],files=entries), indent=2))
    install(release_index, index_path)
    links.append('<a href="'+git+'">GitHub 发布说明</a>')
    links.append('<a href="#jade-library">下载曲库</a>')
    rows = ['<p><a href="'+html.escape(e["url"], quote=True)+'">'+html.escape(e["name"])+'</a> <small>'+format(e["bytes"]/1048576,".2f")+' MiB</small></p>' for e in entries]
    panel = stage/"jade-release.html"
    panel.write_text('<section id="jade"><h2>Jade 最新正式版 '+version+'</h2><p>Jade 根据 Hypixel SkyBlock 地点播放配乐。输入 /jade 可为整座岛屿或单个地点选择音乐。</p>'+" · ".join(links)+'</section>\n<section id="jade-library"><h2>下载 Jade 曲库</h2><p>所有玩家均可下载默认曲库。Jade 首次启动会自动下载缺少的曲目。手动安装时，将 ZIP 解压到游戏实例的 config/jade-music/music 文件夹。</p><p><a href="/jade/library/index.json">查看更新索引</a></p>'+"".join(rows)+'</section>')
    install(panel, jade/"release.html")
    anchor = "<?php include __DIR__.'/mod-release.php'; ?>"
    include = "<?php readfile(__DIR__.'/jade/release.html'); ?>"
    cfg, page = conf.read_text(), home.read_text()
    if (routes not in cfg and cfg.count("    # Jade read-only music distribution") != 1) or page.count(anchor)!=1:
        sys.exit("Unknown layout")
    backup = Path(tempfile.mkdtemp(prefix="jade-site-", dir="/www/sapphire-deploy"))
    shutil.copy2(home, backup/"index.php")
    shutil.copy2(conf, backup/"vhost.conf")
    try:
        if routes not in cfg:
            conf.write_text(cfg.replace("    # Jade read-only music distribution",routes+"    # Jade read-only music distribution"))
            subprocess.run([nginx,"-t"],check=True)
            subprocess.run([nginx,"-s","reload"],check=True)
        if include not in page:
            home.write_text(page.replace(anchor,anchor+"\n"+include))
            subprocess.run(["/www/server/php/74/bin/php","-l",str(home)],check=True)
    except BaseException:
        shutil.copy2(backup/"index.php", home)
        shutil.copy2(backup/"vhost.conf", conf)
        subprocess.run([nginx,"-t"],check=True)
        subprocess.run([nginx,"-s","reload"],check=True)
        raise
    print(record, "Backup:", backup)

