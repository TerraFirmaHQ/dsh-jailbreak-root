#!/usr/bin/env python3
"""Default launcher from the DSH-TUI pixel whale girl.

Source: app/tool/pet_src/whale-girl/sheets/idle.png, first frame only
(x=13..61). The next frame starts at x=81 and is not included.
Art terms: ASSETS.md — copyright ccch1mneyyy, not AGPL-3.0.

The glyph is scaled uniformly with nearest-neighbor and kept inside the
adaptive safe circle (66/108). Head and tail stay inside circle and
rounded-square masks.
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SHEET = ROOT / "tool/pet_src/whale-girl/sheets/idle.png"
RES = ROOT / "android/app/src/main/res"
FRAME = (13, 0, 62, 66)  # first idle frame; do not extend to the next frame


def load_frame() -> Image.Image:
    sheet = Image.open(SHEET).convert("RGBA")
    frame = sheet.crop(FRAME)
    return frame.crop(frame.getbbox())


def radius_of(im: Image.Image) -> float:
    w, h = im.size
    cx, cy = (w - 1) / 2, (h - 1) / 2
    px = im.load()
    md = 0.0
    for y in range(h):
        for x in range(w):
            if px[x, y][3] > 20:
                d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                if d > md:
                    md = d
    return md


def sky(size: int) -> Image.Image:
    im = Image.new("RGBA", (size, size))
    px = im.load()
    for y in range(size):
        t = y / max(1, size - 1)
        col = (
            int(0xC5 + (0x7E - 0xC5) * t),
            int(0xE9 + (0xC8 - 0xE9) * t),
            int(0xFC + (0xF2 - 0xFC) * t),
            255,
        )
        for x in range(size):
            px[x, y] = col
    return im


def fit(frame: Image.Image, canvas: int, diameter_frac: float) -> Image.Image:
    limit = canvas * diameter_frac / 2
    scale = limit / radius_of(frame)
    w = max(1, round(frame.size[0] * scale))
    h = max(1, round(frame.size[1] * scale))
    spr = frame.resize((w, h), Image.NEAREST)
    layer = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    layer.alpha_composite(spr, ((canvas - w) // 2, (canvas - h) // 2))
    return layer


def circle(im: Image.Image) -> Image.Image:
    m = Image.new("L", im.size, 0)
    ImageDraw.Draw(m).ellipse((1, 1, im.size[0] - 2, im.size[1] - 2), fill=255)
    out = im.copy()
    out.putalpha(m)
    return out


def main() -> int:
    frame = load_frame()
    if frame.size[0] > 60:
        print("frame includes more than one sprite", frame.size, file=sys.stderr)
        return 1
    fg_sizes = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
    legacy = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for dens, size in fg_sizes.items():
        fg = fit(frame, size, 0.60)
        fg.save(RES / f"mipmap-{dens}/ic_launcher_foreground.png")
        mono = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        mp, sp = mono.load(), fg.load()
        for y in range(size):
            for x in range(size):
                if sp[x, y][3] > 20:
                    mp[x, y] = (255, 255, 255, 255)
        mono.save(RES / f"mipmap-{dens}/ic_launcher_whale_mono.png")
    def circle(im: Image.Image) -> Image.Image:
        m = Image.new("L", im.size, 0)
        ImageDraw.Draw(m).ellipse((1, 1, im.size[0] - 2, im.size[1] - 2), fill=255)
        out = im.copy()
        out.putalpha(m)
        return out

    def squircle(im: Image.Image) -> Image.Image:
        m = Image.new("L", im.size, 0)
        ImageDraw.Draw(m).rounded_rectangle(
            (0, 0, im.size[0] - 1, im.size[1] - 1), radius=int(im.size[0] * 0.22), fill=255
        )
        out = im.copy()
        out.putalpha(m)
        return out

    for dens, size in legacy.items():
        fg = fit(frame, size, 0.82)
        bg = sky(size)
        full = bg.copy()
        full.alpha_composite(fg)
        flat = bg.copy()
        flat.alpha_composite(squircle(full))
        flat.convert("RGB").save(RES / f"mipmap-{dens}/ic_launcher.png")
        flatc = bg.copy()
        flatc.alpha_composite(circle(full))
        flatc.convert("RGB").save(RES / f"mipmap-{dens}/ic_launcher_round.png")
    print("wrote launcher icons from", SHEET)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
