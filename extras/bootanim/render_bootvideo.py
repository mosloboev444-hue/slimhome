# -*- coding: utf-8 -*-
import os, math, zipfile
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = os.path.dirname(os.path.abspath(__file__))
W, H = 724, 224
FPS = 20
FRAMES = 102

F_BOLD = r"C:\Windows\Fonts\segoeuib.ttf"
LINES = ["DESIGN BY", "ANDREY", "MASLOBOEV"]
PALETTE = [(23,163,152),(46,134,222),(108,92,231),(192,57,43),(230,126,34),
           (196,41,123),(39,174,96),(27,79,114),(142,68,173),(211,84,0)]

TOTAL_LETTERS = sum(len(l) for l in LINES)

def bg():
    im = Image.new("RGB", (W, H), (6, 8, 14))
    d = ImageDraw.Draw(im)
    for y in range(H):
        t = y / H
        d.line([(0, y), (W, y)], fill=(int(8+12*t), int(11+18*t), int(20+28*t)))
    glow = Image.new("L", (W, H), 0)
    dg = ImageDraw.Draw(glow)
    dg.ellipse([W//2-260, H//2-120, W//2+260, H//2+120], fill=58)
    glow = glow.filter(ImageFilter.GaussianBlur(70))
    tint = Image.new("RGB", (W, H), (55, 100, 175))
    return Image.composite(tint, im, glow.point(lambda v: int(v*0.45)))

def ease_out(t):
    return 1 - (1 - t) ** 3

def draw_all(base, p):
    d = ImageDraw.Draw(base)
    size = 34
    f = ImageFont.truetype(F_BOLD, size)
    line_h = int(size * 1.30)
    block_h = line_h * len(LINES)
    top = H // 2 - block_h // 2 + size
    stagger = 0.050
    idx = 0
    for li, line in enumerate(LINES):
        full = d.textlength(line, font=f)
        x = W / 2 - full / 2
        y = top + li * line_h
        for ch in line:
            app = max(0.0, min(1.0, (p - idx * stagger) / 0.18))
            cw = d.textlength(ch, font=f)
            if ch != " " and app > 0:
                col = PALETTE[idx % len(PALETTE)]
                s = 0.75 + 0.25 * ease_out(app)
                fs = max(1, int(size * s))
                f2 = ImageFont.truetype(F_BOLD, fs)
                tw = d.textlength(ch, font=f2)
                d.text((x + cw/2 - tw/2, y + (size - fs) * 0.5), ch, font=f2,
                       fill=(col[0], col[1], col[2]))
            x += cw
            idx += 1
    return block_h

def shine(base, phase):
    if phase <= 0 or phase >= 1:
        return base
    ov = Image.new("RGBA", (W, H), (0,0,0,0))
    d = ImageDraw.Draw(ov)
    sx = -W*0.3 + phase * W * 1.6
    for i in range(70):
        a = int(70 * math.sin(math.pi * i / 70))
        d.line([(sx + i*3, 0), (sx + i*3 - 60, H)], fill=(255,255,255,a), width=3)
    return Image.alpha_composite(base.convert("RGBA"), ov).convert("RGB")

partdir = os.path.join(OUT, "bootvideo_frames")
os.makedirs(partdir, exist_ok=True)
for f in os.listdir(partdir):
    os.remove(os.path.join(partdir, f))

# 62 кадра появления букв + 40 кадров блика
INTRO = 62
for i in range(FRAMES):
    base = bg()
    if i < INTRO:
        p = i / (INTRO - 1)
        draw_all(base, min(1.0, p * 1.12))
    else:
        draw_all(base, 1.0)
    ph = (i - INTRO) / (FRAMES - INTRO)
    if ph > 0:
        base = shine(base, ph)
    base.convert("RGBA").save(os.path.join(partdir, "volume_%03d.png" % (i + 1)))

zp = os.path.join(OUT, "bootvideo_slimhome.zip")
with zipfile.ZipFile(zp, "w", zipfile.ZIP_STORED) as z:
    z.writestr("desc.txt", "%d %d %d\nc 0 0 android\n" % (W, H, FPS))
    for f in sorted(os.listdir(partdir)):
        z.write(os.path.join(partdir, f), "android/" + f)
print("ok:", zp, round(os.path.getsize(zp)/1048576, 2), "MB, kadrov:", FRAMES, W, "x", H)