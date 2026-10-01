"""Draws the 16x16 ramp textures (python3 + Pillow): a flat surf-map colour with faint mottling and a lighter
grid line on two edges. Ramps map the texture world-aligned, so the lines draw the block grid across a whole
ramp. Usage: python3 tools/draw-ramp-textures.py"""
import random
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/surfcraft/textures/block"

# name: (base tones dark, mid, light; grid line)
PALETTES = {
    "surf_ramp": ([(37, 151, 169), (41, 158, 176), (46, 165, 182)], (96, 199, 210)),
    "steep_surf_ramp": ([(218, 126, 35), (225, 135, 41), (231, 144, 49)], (250, 186, 102)),
}

for name, (tones, grid) in PALETTES.items():
    rng = random.Random(name)  # deterministic per texture
    img = Image.new("RGB", (16, 16))
    for y in range(16):
        for x in range(16):
            c = grid if x == 0 or y == 0 else tones[rng.choices((0, 1, 2), weights=(1, 6, 1))[0]]
            img.putpixel((x, y), c)
    img.save(OUT / f"{name}.png")
    print("wrote", OUT / f"{name}.png")
