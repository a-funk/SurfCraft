"""Draws the mod icon (assets/surfcraft/icon.png): a surf ramp in 3/4 view with the Karambit in front, as 32x32
pixel art scaled 4x.  .tools/venv/bin/python tools/draw-icon.py"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/surfcraft"
img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.rounded_rectangle((0, 0, 31, 31), radius=6, fill=(22, 27, 34, 255))
# A two-sided ramp: its triangular end at the front left, the near slope running back to the upper right.
apex, left, right, back = (9, 10), (2, 25), (16, 25), (12, -6)
far = lambda p: (p[0] + back[0], p[1] + back[1])
d.polygon([apex, right, far(right), far(apex)], fill=(61, 214, 208, 255))           # near slope, lit
for t in (0.25, 0.5, 0.75):                                                          # grid lines along the ramp
    a = (apex[0] + (right[0] - apex[0]) * t, apex[1] + (right[1] - apex[1]) * t)
    d.line([a, far(a)], fill=(130, 238, 232, 255))
d.line([apex, far(apex)], fill=(190, 252, 248, 255))                                 # ridge highlight
d.polygon([apex, left, right], fill=(28, 128, 140, 255))                             # end face, shaded
d.line([(left[0], left[1] + 1), (far(right)[0] + 1, left[1] + 1)], fill=(80, 98, 110, 255))
knife = Image.open(ROOT / "textures/item/karambit.png").convert("RGBA")
img.alpha_composite(knife, (16, 15))
img.resize((128, 128), Image.NEAREST).save(ROOT / "icon.png")
print(ROOT / "icon.png")
