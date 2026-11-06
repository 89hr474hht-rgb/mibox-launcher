#!/usr/bin/env python3
"""A fake Xtream Codes server, so LaunchOS TV can be built and tested without anyone's real account.

Usage:  python3 -I mibox/tv/mock/xtream_mock.py [--port 8080] [--episodes-as-list] [--redirect]

It listens on 127.0.0.1 only. The Android emulator reaches it at http://10.0.2.2:<port>. The account is
demo / demo; any other pair gets the "auth": 0 answer real panels give.

It answers the calls the app makes, with the shapes real panels use, numbers sent as strings included:
  player_api.php                                   account and server info
  player_api.php?action=get_live_categories        and get_vod_categories, get_series_categories
  player_api.php?action=get_live_streams&category_id=N   and get_vod_streams, get_series
  player_api.php?action=get_series_info&series_id=N
  /live/<user>/<pass>/<id>.ts|.m3u8, /movie/<user>/<pass>/<id>.mp4, /series/<user>/<pass>/<id>.mp4
  /img/<name>.png                                  logos and posters

The media (a test pattern with a tone) is made once with ffmpeg into mock/media/, which git ignores.
--episodes-as-list sends get_series_info's episodes as a list, as some panels do.
--redirect answers every stream with a 302 to the same server, as many panels do.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

HERE = os.path.dirname(os.path.abspath(__file__))
MEDIA = os.path.join(HERE, "media")
USER, PASSWORD = "demo", "demo"

LIVE_CATEGORIES = [("1", "Généralistes"), ("2", "Information"), ("3", "Sport"), ("4", "Jeunesse"),
                   ("5", "Cinéma")]
VOD_CATEGORIES = [("10", "Nouveautés"), ("11", "Action"), ("12", "Comédie"), ("13", "Animation")]
SERIES_CATEGORIES = [("20", "Séries françaises"), ("21", "Séries US"), ("22", "Documentaires")]

COLOURS = ["0x3B5BDB", "0x9C36B5", "0xE8590C", "0x2B8A3E", "0xC2255C", "0x1098AD", "0x5F3DC4", "0xF08C00"]


def build_catalogue():
    live, vod, series, episodes = [], [], [], {}
    n = 0
    for cat_id, cat_name in LIVE_CATEGORIES:
        for i in range(1, 9):
            n += 1
            live.append({
                "num": n, "name": f"{cat_name} {i}", "stream_type": "live", "stream_id": 100 + n,
                "stream_icon": f"/img/logo{n % 8}.png" if n % 5 else "", "epg_channel_id": f"chan{n}.fr",
                "added": "1700000000", "category_id": cat_id, "custom_sid": "", "tv_archive": 0,
                "direct_source": "", "tv_archive_duration": 0,
            })
    n = 0
    for cat_id, cat_name in VOD_CATEGORIES:
        for i in range(1, 13):
            n += 1
            vod.append({
                "num": n, "name": f"Film {cat_name.lower()} {i} ({2010 + n % 15})", "stream_type": "movie",
                "stream_id": 5000 + n, "stream_icon": f"/img/poster{n % 8}.png", "rating": str(5 + n % 5),
                "rating_5based": (5 + n % 5) / 2, "added": str(1700000000 + n), "category_id": cat_id,
                "container_extension": "mp4", "custom_sid": "", "direct_source": "",
            })
    n = 0
    for cat_id, cat_name in SERIES_CATEGORIES:
        for i in range(1, 7):
            n += 1
            sid = 800 + n
            series.append({
                "num": n, "name": f"Série {i} – {cat_name}", "series_id": sid, "cover": f"/img/poster{(n + 3) % 8}.png",
                "plot": "Une série inventée pour tester LaunchOS TV, avec un résumé assez long pour voir comment "
                        "le texte se coupe sur deux ou trois lignes.",
                "cast": "", "director": "", "genre": cat_name, "releaseDate": f"{2015 + n % 9}-01-01",
                "last_modified": "1700000000", "rating": "7", "rating_5based": 3.5, "backdrop_path": [],
                "youtube_trailer": "", "episode_run_time": "42", "category_id": cat_id,
            })
            seasons = {}
            for s in range(1, 2 + n % 3):
                seasons[str(s)] = [{
                    "id": str(90000 + sid * 100 + s * 10 + e), "episode_num": e,
                    "title": f"S{s:02d}E{e:02d} – Épisode {e}", "container_extension": "mp4",
                    "info": {"duration_secs": 2520, "duration": "00:42:00", "plot": f"Résumé de l'épisode {e}.",
                             "movie_image": f"/img/poster{e % 8}.png", "rating": "7"},
                    "custom_sid": "", "added": "1700000000", "season": s, "direct_source": "",
                } for e in range(1, 7)]
            episodes[sid] = seasons
    return live, vod, series, episodes


LIVE, VOD, SERIES, EPISODES = build_catalogue()


def make_media():
    """Test clips and images, made once: a live-style TS, a seekable MP4, and coloured PNGs."""
    os.makedirs(MEDIA, exist_ok=True)
    ff = shutil.which("ffmpeg")
    if not ff:
        sys.exit("ffmpeg is needed to make the test media (brew install ffmpeg)")

    def run(*args):
        subprocess.run([ff, "-hide_banner", "-loglevel", "error", "-y", *args], check=True)

    ts = os.path.join(MEDIA, "live.ts")
    if not os.path.exists(ts):
        run("-f", "lavfi", "-i", "testsrc2=size=1280x720:rate=25", "-f", "lavfi", "-i", "sine=frequency=440",
            "-t", "600", "-c:v", "libx264", "-preset", "veryfast", "-g", "50", "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-b:a", "96k", "-f", "mpegts", ts)
    mp4 = os.path.join(MEDIA, "movie.mp4")
    if not os.path.exists(mp4):
        run("-f", "lavfi", "-i", "smptehdbars=size=1280x720:rate=25", "-f", "lavfi", "-i",
            "sine=frequency=660", "-t", "300", "-c:v", "libx264", "-preset", "veryfast", "-g", "50",
            "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "96k", "-movflags", "+faststart", mp4)
    for i, c in enumerate(COLOURS):
        logo = os.path.join(MEDIA, f"logo{i}.png")
        if not os.path.exists(logo):
            run("-f", "lavfi", "-i", f"color=c={c}:size=256x256", "-frames:v", "1", logo)
        poster = os.path.join(MEDIA, f"poster{i}.png")
        if not os.path.exists(poster):
            run("-f", "lavfi", "-i", f"gradients=size=300x450:c0={c}:c1=0x101218:nb_colors=2:speed=0",
                "-frames:v", "1", poster)


def absolute(base, item, keys):
    out = dict(item)
    for k in keys:
        if isinstance(out.get(k), str) and out[k].startswith("/img/"):
            out[k] = base + out[k]
    return out


class Handler(BaseHTTPRequestHandler):
    server_version = "XtreamMock/1.0"
    episodes_as_list = False
    redirect = False

    def log_message(self, fmt, *args):
        sys.stderr.write("%s %s\n" % (time.strftime("%H:%M:%S"), fmt % args))

    def send_json(self, obj):
        body = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def base(self):
        return "http://" + (self.headers.get("Host") or "127.0.0.1")

    def do_GET(self):
        url = urlparse(self.path)
        if url.path == "/player_api.php":
            return self.api(parse_qs(url.query))
        m = re.fullmatch(r"/(live|movie|series)/([^/]+)/([^/]+)/(\d+)\.(\w+)", url.path)
        if m:
            kind, user, password, _id, _ext = m.groups()
            if (user, password) != (USER, PASSWORD):
                self.send_error(401)
                return
            if self.redirect and "r=1" not in url.query:
                self.send_response(302)
                self.send_header("Location", self.base() + url.path + "?r=1")
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
            return self.media("live.ts" if kind == "live" else "movie.mp4", live=kind == "live")
        m = re.fullmatch(r"/img/(\w+\.png)", url.path)
        if m:
            return self.media(m.group(1))
        self.send_error(404)

    def api(self, q):
        user, password = q.get("username", [""])[0], q.get("password", [""])[0]
        if (user, password) != (USER, PASSWORD):
            return self.send_json({"user_info": {"auth": 0}})
        action = q.get("action", [""])[0]
        cat = q.get("category_id", [None])[0]
        base = self.base()
        if action == "":
            now = int(time.time())
            return self.send_json({
                "user_info": {"username": user, "password": password, "message": "", "auth": 1,
                              "status": "Active", "exp_date": str(now + 86400 * 120), "is_trial": "0",
                              "active_cons": "0", "created_at": str(now - 86400 * 30), "max_connections": "1",
                              "allowed_output_formats": ["m3u8", "ts"]},
                "server_info": {"url": "10.0.2.2", "port": "8080", "https_port": "", "server_protocol": "http",
                                "rtmp_port": "", "timezone": "Europe/Paris", "timestamp_now": now,
                                "time_now": time.strftime("%Y-%m-%d %H:%M:%S")},
            })
        if action == "get_live_categories":
            return self.send_json([{"category_id": i, "category_name": n, "parent_id": 0} for i, n in LIVE_CATEGORIES])
        if action == "get_vod_categories":
            return self.send_json([{"category_id": i, "category_name": n, "parent_id": 0} for i, n in VOD_CATEGORIES])
        if action == "get_series_categories":
            return self.send_json([{"category_id": i, "category_name": n, "parent_id": 0}
                                   for i, n in SERIES_CATEGORIES])
        if action == "get_live_streams":
            return self.send_json([absolute(base, s, ["stream_icon"]) for s in LIVE if cat in (None, s["category_id"])])
        if action == "get_vod_streams":
            return self.send_json([absolute(base, s, ["stream_icon"]) for s in VOD if cat in (None, s["category_id"])])
        if action == "get_series":
            return self.send_json([absolute(base, s, ["cover"]) for s in SERIES if cat in (None, s["category_id"])])
        if action == "get_series_info":
            sid = int(q.get("series_id", ["0"])[0])
            info = next((s for s in SERIES if s["series_id"] == sid), None)
            if info is None:
                return self.send_json({"seasons": [], "info": {}, "episodes": []})
            seasons = EPISODES[sid]
            eps = {k: [dict(e, info=absolute(base, e["info"], ["movie_image"])) for e in v] for k, v in seasons.items()}
            return self.send_json({
                "seasons": [{"season_number": int(k), "name": f"Saison {k}", "episode_count": str(len(v)),
                             "cover": base + info["cover"]} for k, v in seasons.items()],
                "info": absolute(base, info, ["cover"]),
                "episodes": [e for v in eps.values() for e in v] if self.episodes_as_list else eps,
            })
        return self.send_json([])

    def media(self, name, live=False):
        path = os.path.join(MEDIA, name)
        if not os.path.exists(path):
            self.send_error(404)
            return
        size = os.path.getsize(path)
        ctype = {"ts": "video/mp2t", "mp4": "video/mp4", "png": "image/png"}[name.rsplit(".", 1)[1]]
        start, end = 0, size - 1
        rng = self.headers.get("Range")
        if rng and not live:
            m = re.fullmatch(r"bytes=(\d*)-(\d*)", rng.strip())
            if m and m.group(1):
                start = int(m.group(1))
                end = int(m.group(2)) if m.group(2) else size - 1
            self.send_response(206)
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        else:
            self.send_response(200)
        self.send_header("Content-Type", ctype)
        if not live:
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Content-Length", str(end - start + 1))
        self.end_headers()
        try:
            with open(path, "rb") as f:
                f.seek(start)
                left = end - start + 1
                while left > 0:
                    chunk = f.read(min(65536, left))
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    left -= len(chunk)
                    if live:
                        time.sleep(0.05)   # roughly real time for a live channel, so it does not end at once
        except (BrokenPipeError, ConnectionResetError):
            pass


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--port", type=int, default=8080)
    p.add_argument("--episodes-as-list", action="store_true")
    p.add_argument("--redirect", action="store_true")
    a = p.parse_args()
    make_media()
    Handler.episodes_as_list = a.episodes_as_list
    Handler.redirect = a.redirect
    print(f"Fake Xtream server on http://127.0.0.1:{a.port} (emulator: http://10.0.2.2:{a.port}), account demo / demo")
    ThreadingHTTPServer(("127.0.0.1", a.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
