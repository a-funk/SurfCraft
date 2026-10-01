"""Draws the 16x16 Karambit item sprite (python3 + Pillow): a CS:GO-style karambit with a Fade finish.

Geometry in screen pixels (y down), rasterised at 4x4 samples per pixel: a finger ring at the bottom left, a black
grip running up and right from it, and a claw blade (a Bezier centreline with a tapering width) that continues the
grip, arcs over the top right and hooks down to its point, spine outside and edge inside. The blade is coloured along
its length gold -> purple -> pink, darker on the spine and lighter on the edge.
Usage: python3 tools/draw-karambit.py [out.png]
"""
import math
import sys
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/surfcraft/textures/item/karambit.png"

RING_C, RING_OUT, RING_IN = (3.1, 12.9), 2.75, 1.35
GRIP_A, GRIP_B, GRIP_R = (5.0, 11.0), (8.2, 7.8), 1.2
BLADE = [(7.8, 8.2), (8.6, 4.2), (14.6, -0.4), (14.9, 8.2)]  # Bezier: from the grip, over the top, down to the point
W0, W1 = 3.4, 0.4  # blade width at the grip and at the point
FADE = [(0.0, (255, 196, 40)), (0.2, (250, 150, 90)), (0.5, (146, 64, 236)), (1.0, (255, 70, 180))]  # gold, purple, pink


def mix(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def fade(t):
    for (t0, c0), (t1, c1) in zip(FADE, FADE[1:]):
        if t <= t1:
            return mix(c0, c1, (t - t0) / (t1 - t0))
    return FADE[-1][1]


def bezier(t):
    (ax, ay), (bx, by), (cx, cy), (dx, dy) = BLADE
    u = 1 - t
    return (u**3 * ax + 3 * u * u * t * bx + 3 * u * t * t * cx + t**3 * dx,
            u**3 * ay + 3 * u * u * t * by + 3 * u * t * t * cy + t**3 * dy)


CURVE = [bezier(i / 400) for i in range(401)]


def blade(p):
    """(t along the blade 0..1, depth from spine 0 to edge 1) if p is in the blade, else None."""
    i = min(range(len(CURVE)), key=lambda k: (CURVE[k][0] - p[0]) ** 2 + (CURVE[k][1] - p[1]) ** 2)
    a, b = CURVE[max(i - 1, 0)], CURVE[min(i + 1, len(CURVE) - 1)]
    tx, ty = b[0] - a[0], b[1] - a[1]
    n = math.hypot(tx, ty)
    # Lateral offset toward the curve's left (outer, spine) side; the blade turns clockwise on screen.
    s = ((p[0] - CURVE[i][0]) * ty - (p[1] - CURVE[i][1]) * tx) / n
    t = i / (len(CURVE) - 1)
    w = W1 + (W0 - W1) * (1 - t) ** 0.9
    along = (p[0] - CURVE[i][0]) * tx + (p[1] - CURVE[i][1]) * ty
    if abs(s) > w / 2 or (i == 0 and along < 0) or (i == len(CURVE) - 1 and along > 0):
        return None
    return t, (w / 2 - s) / w


def grip(p):
    ax, ay, bx, by = *GRIP_A, *GRIP_B
    t = max(0.0, min(1.0, ((p[0] - ax) * (bx - ax) + (p[1] - ay) * (by - ay)) / ((bx - ax) ** 2 + (by - ay) ** 2)))
    return math.hypot(p[0] - ax - t * (bx - ax), p[1] - ay - t * (by - ay)) <= GRIP_R


def part(p):
    if blade(p):
        return "blade"
    if grip(p):
        return "grip"
    return "ring" if RING_IN <= math.hypot(p[0] - RING_C[0], p[1] - RING_C[1]) <= RING_OUT else None


def draw():
    img = Image.new("RGBA", (16, 16))
    kinds, blade_t = {}, {}
    for y in range(16):
        for x in range(16):
            samples = [(x + (i + 0.5) / 4, y + (j + 0.5) / 4) for i in range(4) for j in range(4)]
            hits = [q for q in map(part, samples) if q]
            if len(hits) < 7:
                continue
            kind = max(set(hits), key=hits.count)
            if kind == "blade":
                found = [b for b in map(blade, samples) if b]
                t = sum(b[0] for b in found) / len(found)
                d = sum(b[1] for b in found) / len(found)
                # A soft bevel: the spine side darker, the edge side lighter.
                col = mix(fade(t), (60, 20, 80), 0.3 * max(0.0, 1 - 2 * d))
                col = mix(col, (255, 255, 255), 0.3 * max(0.0, 3 * d - 2))
                blade_t[x, y] = t
            elif kind == "grip":
                # The grip's upper side catches light, so the black reads as a rounded handle.
                dx, dy = GRIP_B[0] - GRIP_A[0], GRIP_B[1] - GRIP_A[1]
                side = ((x + 0.5 - GRIP_A[0]) * dy - (y + 0.5 - GRIP_A[1]) * dx) / math.hypot(dx, dy)
                col = (70, 70, 80) if side > 0.35 else (24, 24, 28)
            else:
                col = (84, 84, 96) if (x + 0.5 - RING_C[0]) + (y + 0.5 - RING_C[1]) < -2.4 else (42, 42, 48)
            img.putpixel((x, y), (*col, 255))
            kinds[x, y] = kind
    # Outline the blade (vanilla swords are outlined too), so it reads on any background.
    for (x, y), t in blade_t.items():
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in kinds:
                img.putpixel((nx, ny), (34, 16, 42, 255))
    return img


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.parent.mkdir(parents=True, exist_ok=True)
    draw().save(out)
    print("wrote", out)
