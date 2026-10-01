"""SurfCraft dev log: numbered screenshots with captions, a gallery page, and a montage at the end.

  .tools/venv/bin/python tools/devlog.py add <image> "Title" "One-line caption"
  .tools/venv/bin/python tools/devlog.py clip <video> "Title" "One-line caption"   # gameplay clip (H.264, poster frame)
  .tools/venv/bin/python tools/devlog.py page       # rebuild devlog/index.html
  .tools/venv/bin/python tools/devlog.py montage    # devlog/montage.mp4 from every shot (needs ffmpeg)
  .tools/venv/bin/python tools/devlog.py deploy     # publish devlog/ to Railway (project surfcraft-devlog)

Shots are stored as JPEG (at most 1600 px wide) in devlog/shots/, listed in devlog/entries.json. Every `add`
commits the dev log and publishes it at https://devlog-production-6292.up.railway.app right away.
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


def add(image, title, caption, video=None):
    # Agents in several worktrees add shots to this one log at the same time.
    with open(ROOT / ".lock", "a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        _add(image, title, caption, video)
        # Every update goes live right away: commit just the dev log, then publish it.
        repo = ["git", "-C", str(ROOT.parent)]
        subprocess.run(repo + ["add", "devlog"], check=False)
        subprocess.run(repo + ["-c", "user.name=Alex Funk", "-c", "user.email=11507011+a-funk@users.noreply.github.com", "commit", "-q",
                               "-m", f"Dev log: {title}\n\nCo-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>",
                               "--", "devlog"], check=False)
        deploy()


def _add(image, title, caption, video=None):
    entries = load()
    SHOTS.mkdir(parents=True, exist_ok=True)
    slug = re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")[:48]
    name = f"{len(entries) + 1:02d}-{slug}.jpg"
    entry = {"file": f"shots/{name}", "title": title, "caption": caption, "at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M")}
    if video:
        # A web-friendly H.264 copy, and its middle frame as the poster.
        (ROOT / "clips").mkdir(exist_ok=True)
        clip = ROOT / "clips" / name.replace(".jpg", ".mp4")
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(video), "-vf", "scale=1280:-2", "-c:v", "libx264", "-crf", "24",
                        "-preset", "medium", "-pix_fmt", "yuv420p", "-an", "-movflags", "+faststart", str(clip)], check=True)
        image = SHOTS / name
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-ss", f"{duration(clip) / 2:.2f}", "-i", str(clip), "-frames:v", "1", str(image)], check=True)
        entry["video"] = f"clips/{clip.name}"
    img = Image.open(image).convert("RGB")
    if img.width > 1600:
        img = img.resize((1600, round(img.height * 1600 / img.width)), Image.LANCZOS)
    img.save(SHOTS / name, quality=88, optimize=True)
    entries.append(entry)
    ENTRIES.write_text(json.dumps(entries, indent=1) + "\n")
    page()
    print(SHOTS / name)


def duration(video):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(video)],
                         capture_output=True, text=True, check=True)
    return float(out.stdout.strip())


def media(e):
    if "video" in e:
        return (f'<video controls muted loop playsinline preload="none" poster="{html.escape(e["file"])}">'
                f'<source src="{html.escape(e["video"])}" type="video/mp4"></video>')
    return f'<a href="{html.escape(e["file"])}"><img src="{html.escape(e["file"])}" loading="lazy" alt="{html.escape(e["title"])}"></a>'


def page():
    cards = "\n".join(
        f'<figure>{media(e)}<figcaption><span class="n">{i:02d}</span> '
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
figure img, figure video {{ display: block; width: 100%; height: auto; }}
figcaption {{ padding: 12px 16px; }} figcaption p {{ margin: 4px 0 0; color: var(--muted); }}
.n {{ color: var(--accent); font-weight: 700; margin-right: 6px; }} .at {{ float: right; color: var(--muted); font-size: 13px; }}
</style></head><body>
<header><h1>Surf<span>Craft</span> dev log</h1><p>CS:S surf ramps and surf physics for Minecraft 26.3, built step by step. Work in progress; updated {datetime.datetime.now().strftime("%Y-%m-%d %H:%M")}.</p></header>
<main>
{cards}
</main></body></html>
""")


def montage():
    """Each shot for 3.5 s with a slow zoom and a title bar, crossfaded, 1280x720 (Homebrew ffmpeg has no drawtext,
    so Pillow draws the frames)."""
    from PIL import ImageDraw, ImageFont
    import um
    fonts = Path(um.__file__).parent / "fonts"
    bold, regular = ImageFont.truetype(str(fonts / "SpaceGrotesk-Bold.ttf"), 34), ImageFont.truetype(str(fonts / "SpaceGrotesk-Medium.ttf"), 22)
    entries = load()
    if not entries:
        sys.exit("no shots yet")
    out = ROOT / "montage.mp4"
    tmp_root = ROOT.parent / ".tools" / "tmp"
    tmp_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=tmp_root) as tmp:
        clips = []
        for i, e in enumerate(entries):
            frame = Image.new("RGB", (1280, 720), (13, 17, 23))
            img = Image.open(ROOT / e["file"]).convert("RGB")
            img.thumbnail((1280, 720), Image.LANCZOS)
            frame.paste(img, ((1280 - img.width) // 2, (720 - img.height) // 2))
            still = Path(tmp) / f"{i:03d}.png"
            frame.save(still)
            # The title bar stays put while the shot slowly zooms behind it.
            bar = Image.new("RGBA", (1280, 720), (0, 0, 0, 0))
            d = ImageDraw.Draw(bar)
            d.rectangle((0, 624, 1280, 720), fill=(0, 0, 0, 170))
            d.text((28, 634), f"{i + 1:02d}", font=bold, fill=(61, 214, 208))
            d.text((84, 634), e["title"], font=bold, fill=(255, 255, 255))
            caption = e["caption"]
            while d.textlength(caption, font=regular) > 1280 - 84 - 28:
                caption = caption[: caption.rstrip("…").rfind(" ")] + "…"
            d.text((84, 680), caption, font=regular, fill=(201, 209, 217))
            overlay = Path(tmp) / f"{i:03d}-bar.png"
            bar.save(overlay)
            clip = Path(tmp) / f"{i:03d}.mp4"
            if "video" in e:
                length = min(12.0, duration(ROOT / e["video"]))
                subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(ROOT / e["video"]), "-loop", "1", "-i", str(overlay),
                                "-filter_complex", "[0]scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2,"
                                "fps=30,setsar=1[v];[v][1]overlay=0:0", "-t", f"{length:.2f}", "-an", "-pix_fmt", "yuv420p", "-r", "30",
                                str(clip)], check=True)
            else:
                length = 3.5
                subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-loop", "1", "-i", str(still), "-loop", "1", "-i", str(overlay),
                                "-filter_complex", "[0]zoompan=z='min(zoom+0.0005,1.04)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'"
                                ":d=105:s=1280x720:fps=30[z];[z][1]overlay=0:0",
                                "-t", "3.5", "-pix_fmt", "yuv420p", "-r", "30", str(clip)], check=True)
            clips.append((clip, length))
        # Chain crossfades: each clip overlaps the next by 0.5 s.
        inputs, chain, last, offset = [], "", "0:v", 0.0
        for c, _ in clips:
            inputs += ["-i", str(c)]
        for i in range(1, len(clips)):
            offset += clips[i - 1][1] - 0.5
            chain += f"[{last}][{i}:v]xfade=transition=fade:duration=0.5:offset={offset:.2f}[v{i}];"
            last = f"v{i}"
        cmd = ["ffmpeg", "-loglevel", "error", "-y", *inputs]
        cmd += ["-filter_complex", chain.rstrip(";"), "-map", f"[{last}]"] if chain else ["-map", "0:v"]
        subprocess.run(cmd + ["-pix_fmt", "yuv420p", "-movflags", "+faststart", str(out)], check=True)
    print(out)


def deploy():
    """Railway serves devlog/ through its Dockerfile (Caddy); the directory is linked to project surfcraft-devlog."""
    page()
    subprocess.run(["railway", "up", "--service", "devlog", "--detach", "-m", f"dev log: {len(load())} shots"], cwd=ROOT, check=True,
                   stdout=subprocess.DEVNULL)
    print(f"{PUBLIC_URL} (live in about a minute)")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "add" and len(sys.argv) == 5:
        add(*sys.argv[2:])
    elif cmd == "clip" and len(sys.argv) == 5:
        add(None, sys.argv[3], sys.argv[4], video=sys.argv[2])
    elif cmd == "page":
        page()
    elif cmd == "montage":
        montage()
    elif cmd == "deploy":
        deploy()
    else:
        sys.exit(__doc__)
