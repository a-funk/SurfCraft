"""SurfCraft dev log: numbered screenshots with captions, a gallery page, and a montage at the end.

  .tools/venv/bin/python tools/devlog.py add <image> "Title" "One-line caption"
  .tools/venv/bin/python tools/devlog.py page       # rebuild devlog/index.html
  .tools/venv/bin/python tools/devlog.py montage    # devlog/montage.mp4 from every shot (needs ffmpeg)
  .tools/venv/bin/python tools/devlog.py deploy     # publish devlog/ to Railway (project surfcraft-devlog)

Shots are stored as JPEG (at most 1600 px wide) in devlog/shots/, listed in devlog/entries.json.
"""
import datetime
import fcntl
import html
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "devlog"
ROOT.mkdir(exist_ok=True)
SHOTS = ROOT / "shots"
ENTRIES = ROOT / "entries.json"
PUBLIC_URL = "https://devlog-production-6292.up.railway.app"


def load():
    return json.loads(ENTRIES.read_text()) if ENTRIES.exists() else []


def add(image, title, caption):
    # Agents in several worktrees add shots to this one log at the same time.
    with open(ROOT.parent / "devlog" / ".lock", "a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        _add(image, title, caption)


def _add(image, title, caption):
    entries = load()
    SHOTS.mkdir(parents=True, exist_ok=True)
    slug = re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")[:48]
    name = f"{len(entries) + 1:02d}-{slug}.jpg"
    img = Image.open(image).convert("RGB")
    if img.width > 1600:
        img = img.resize((1600, round(img.height * 1600 / img.width)), Image.LANCZOS)
    img.save(SHOTS / name, quality=88, optimize=True)
    entries.append({"file": f"shots/{name}", "title": title, "caption": caption,
                    "at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M")})
    ENTRIES.write_text(json.dumps(entries, indent=1) + "\n")
    page()
    print(SHOTS / name)


def page():
    cards = "\n".join(
        f'<figure><a href="{html.escape(e["file"])}"><img src="{html.escape(e["file"])}" loading="lazy" '
        f'alt="{html.escape(e["title"])}"></a><figcaption><span class="n">{i:02d}</span> '
        f'<b>{html.escape(e["title"])}</b><span class="at">{html.escape(e["at"])}</span>'
        f'<p>{html.escape(e["caption"])}</p></figcaption></figure>'
        for i, e in enumerate(load(), 1))
    ROOT.mkdir(exist_ok=True)
    (ROOT / "index.html").write_text(f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>SurfCraft dev log</title>
<style>
:root {{ --bg: #0d1117; --card: #161b22; --text: #e6edf3; --muted: #8b949e; --accent: #3dd6d0; }}
body {{ margin: 0; background: var(--bg); color: var(--text); font: 15px/1.5 -apple-system, system-ui, sans-serif; }}
header {{ padding: 32px 16px 8px; max-width: 1100px; margin: auto; }}
h1 {{ margin: 0; font-size: 28px; }} h1 span {{ color: var(--accent); }}
header p {{ color: var(--muted); margin: 4px 0 0; }}
main {{ max-width: 1100px; margin: auto; padding: 16px; display: grid; gap: 20px; }}
figure {{ margin: 0; background: var(--card); border-radius: 10px; overflow: hidden; }}
figure img {{ display: block; width: 100%; height: auto; }}
figcaption {{ padding: 12px 16px; }} figcaption p {{ margin: 4px 0 0; color: var(--muted); }}
.n {{ color: var(--accent); font-weight: 700; margin-right: 6px; }} .at {{ float: right; color: var(--muted); font-size: 13px; }}
</style></head><body>
<header><h1>Surf<span>Craft</span> dev log</h1><p>CS:S surf ramps and surf physics for Minecraft 26.3, built step by step. Work in progress; updated {datetime.datetime.now().strftime("%Y-%m-%d %H:%M")}.</p></header>
<main>
{cards}
</main></body></html>
""")


def montage():
    """Each shot for 3.5 s with a slow zoom and its title, crossfaded, 1280x720."""
    entries = load()
    out = ROOT / "montage.mp4"
    with tempfile.TemporaryDirectory() as tmp:
        clips = []
        for i, e in enumerate(entries):
            clip = Path(tmp) / f"{i:03d}.mp4"
            title = f"{i + 1:02d}  {e['title']}".replace("\\", "\\\\").replace("'", "’").replace(":", "\\:")
            vf = ("scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2:color=0x0d1117,"
                  "zoompan=z='min(zoom+0.0006,1.05)':d=105:s=1280x720:fps=30,"
                  f"drawbox=y=ih-64:w=iw:h=64:color=black@0.55:t=fill,"
                  f"drawtext=text='{title}':x=24:y=h-46:fontsize=28:fontcolor=white")
            subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-loop", "1", "-i", str(ROOT / e["file"]), "-vf", vf,
                            "-t", "3.5", "-pix_fmt", "yuv420p", "-r", "30", str(clip)], check=True)
            clips.append(clip)
        if not clips:
            sys.exit("no shots yet")
        # Chain crossfades: each clip overlaps the next by 0.5 s.
        inputs, chain, last, offset = [], "", "0:v", 0.0
        for c in clips:
            inputs += ["-i", str(c)]
        for i in range(1, len(clips)):
            offset += 3.0
            chain += f"[{last}][{i}:v]xfade=transition=fade:duration=0.5:offset={offset:.2f}[v{i}];"
            last = f"v{i}"
        cmd = ["ffmpeg", "-loglevel", "error", "-y", *inputs]
        cmd += ["-filter_complex", chain.rstrip(";"), "-map", f"[{last}]"] if chain else ["-map", "0:v"]
        subprocess.run(cmd + ["-pix_fmt", "yuv420p", str(out)], check=True)
    print(out)


def deploy():
    """Railway serves devlog/ through its Dockerfile (Caddy); the directory is linked to project surfcraft-devlog."""
    page()
    subprocess.run(["railway", "up", "--service", "devlog", "--detach", "-m", f"dev log: {len(load())} shots"], cwd=ROOT, check=True)
    print(f"{PUBLIC_URL} (live in about a minute)")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "add" and len(sys.argv) == 5:
        add(*sys.argv[2:])
    elif cmd == "page":
        page()
    elif cmd == "montage":
        montage()
    elif cmd == "deploy":
        deploy()
    else:
        sys.exit(__doc__)
