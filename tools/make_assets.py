"""Generates every Shadock art asset at 32x ("Faithful-style", double resolution).

Usage:
    python tools/make_assets.py              # writes assets into src/main/resources and branding/
    python tools/make_assets.py preview.png  # also writes a magnified contact sheet

GUI sprites are drawn at twice their on-screen size, so at GUI scale 2+ they show twice the detail of
vanilla's 16x art, like the Faithful 32x resource pack. Nine-slice sprites get .mcmeta files whose
width/height/border are in GUI units, so Minecraft scales the 2x texture correctly.
All artwork is original and generated from code in this file and pixelkit.py.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pixelkit import (FOLDER, FOLDER_BACK, FONT, facet_rows, GLASS, GOLD, GREEN, IRON, RED, VIOLET, WHITE, Canvas,
                      Material, gear_radius, hexc, lerp, outline_of, text_rows)

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'shadock')
SPRITES = os.path.join(ASSETS, 'textures', 'gui', 'sprites')
GUI = os.path.join(ASSETS, 'textures', 'gui')
BRANDING = os.path.join(HERE, '..', 'branding')

SPECTRUM = ['ff4d4d', 'ff9a3c', 'ffe14d', '58e05a', '4fd8e8', '5b7cff', 'a66bff']

outputs = {}


def save(path, img, preview_scale=None):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    outputs[os.path.relpath(path, os.path.join(HERE, '..'))] = (img, preview_scale)


def save_sprite(name, canvas, mcmeta=None):
    path = os.path.join(SPRITES, name + '.png')
    save(path, canvas.image(), 6)
    meta_path = path + '.mcmeta'
    if mcmeta is not None:
        with open(meta_path, 'w', encoding='utf-8') as f:
            json.dump(mcmeta, f, indent=2)
    elif os.path.exists(meta_path):
        os.remove(meta_path)


def nine_slice(width, height, border):
    return {'gui': {'scaling': {'type': 'nine_slice', 'width': width, 'height': height, 'border': border}}}


# ---- toolbar icons (24x24, shown at 12x12) ----------------------------------------------------------

def icon_import():
    c = Canvas(24, 24)
    plus = c.rect(9, 3, 14, 20) | c.rect(3, 9, 20, 14)
    c.material(plus, GREEN)
    # A small sparkle on the top-right, hinting "new".
    c.put(c.rect(19, 2, 19, 4) | c.rect(18, 3, 20, 3), hexc('ffffff'))
    return c


def icon_folder():
    c = Canvas(24, 24)
    back = c.rect(2, 6, 21, 19) | c.rect(2, 4, 9, 6)
    c.material(back, FOLDER_BACK)
    front = c.polygon([(2, 10), (22, 10), (21, 19), (2, 19)])
    c.material(front, FOLDER, shadow=False)
    return c


def icon_key():
    c = Canvas(24, 24)
    ring = c.disc(7, 12, 5.6) & ~c.disc(7, 12, 2.3)
    shaft = c.rect(11, 10, 21, 13)
    teeth = c.rect(15, 14, 16, 16) | c.rect(19, 14, 20, 17)
    c.material(ring | shaft | teeth, GOLD)
    return c


def icon_settings():
    c = Canvas(24, 24)
    gear = c.polar(12, 12, gear_radius(8, 10.2, 7.6, 0.42)) & ~c.disc(12, 12, 3.3)
    c.material(gear, IRON)
    # Inner ring detail.
    c.put(outline_of(c.disc(12, 12, 3.3)) & gear, hexc('8a9097'))
    return c


def icon_sort_custom():
    c = Canvas(24, 24)
    bars = c.rect(2, 5, 11, 7) | c.rect(2, 11, 11, 13) | c.rect(2, 17, 11, 19)
    c.material(bars, WHITE)
    arrow = c.rect(16, 6, 18, 17) | c.polygon([(13, 7), (17, 2), (21, 7)]) | c.polygon([(13, 16), (17, 21), (21, 16)])
    c.material(arrow, WHITE)
    return c


def icon_sort_alpha():
    c = Canvas(24, 24)
    a = c.bitmap(['.#.', '#.#', '###', '#.#', '#.#'], 2, 1, 2)
    z = c.bitmap(['###', '..#', '.#.', '#..', '###'], 2, 13, 2)
    c.material(a | z, WHITE)
    arrow = c.rect(16, 3, 18, 16) | c.polygon([(13, 15), (17, 21), (21, 15)])
    c.material(arrow, GOLD)
    return c


def icon_close():
    c = Canvas(24, 24)
    x = c.line([(6, 6), (17, 17)], 4) | c.line([(17, 6), (6, 17)], 4)
    c.material(x, WHITE)
    return c


def prism_mark(c, ox, oy, size):
    """The Shadock mark (remastered): a light beam hits a faceted glass pyramid and leaves as a
    spectrum. The pyramid has a lit left face and a shaded right face. Fits a size x size box."""
    s = size / 24.0

    def p(x, y):
        return (ox + x * s, oy + y * s)

    glow = c.line([p(0, 13.5), p(10, 13.5)], max(3, int(round(4 * s))))
    c.blend(glow, (255, 255, 255, 60))
    beam = c.line([p(0, 13.5), p(10, 13.5)], max(2, int(round(1.6 * s))))
    c.put(beam, hexc('ffffff'))

    bands = len(SPECTRUM)
    for i, color in enumerate(SPECTRUM):
        y0 = 6 + i * (16 / bands)
        y1 = 6 + (i + 1) * (16 / bands)
        band = c.polygon([p(15.5, 12.4), p(15.5, 13.8), p(24, y1), p(24, y0)])
        c.put(band, hexc(color))
        # Brighter core near the prism, fading slightly outward.
        c.blend(band & c.polygon([p(15.5, 0), p(19, 0), p(19, 24), p(15.5, 24)]), (255, 255, 255, 70))

    left = c.polygon([p(12, 1.5), p(12, 21.5), p(2, 21.5)])
    right = c.polygon([p(12, 1.5), p(22, 21.5), p(12, 21.5)])
    whole = left | right
    c.put(outline_of(whole), hexc('0b2236'))
    for y in range(c.h):
        if left[y].any() or right[y].any():
            t = min(1.0, max(0.0, (y - oy) / max(1.0, size)))
            c.px[y][left[y]] = lerp(hexc('c8f7ff'), hexc('5fd2ea'), t)
            c.px[y][right[y]] = lerp(hexc('3fb4d8'), hexc('1d6f9a'), t)
    # Crisp ridge between the faces and a glint on the lit face.
    ridge = c.line([p(12, 2), p(12, 21)], max(1, int(round(0.6 * s))))
    c.put(ridge & whole, hexc('f2feff'))
    glint = c.line([p(10.5, 6), p(6, 15)], max(1, int(round(0.8 * s))))
    c.blend(glint & left, (255, 255, 255, 170))
    base = c.rect(int(ox + 2 * s), int(oy + 20.6 * s), int(ox + 22 * s), int(oy + 21.5 * s)) & whole
    c.put(base, hexc('17587e'))


def icon_logo():
    c = Canvas(24, 24)
    prism_mark(c, 0, 0, 24)
    return c


# ---- row icons (16x16, shown at 8x8) ----------------------------------------------------------------

def row_zip():
    c = Canvas(16, 16)
    box = c.rect(2, 2, 13, 13)
    c.material(box, VIOLET)
    zipper = c.rect(7, 2, 8, 13)
    c.put(zipper, hexc('3a1f80'))
    for y in range(3, 13, 2):
        c.put(c.rect(7, y, 7, y) | c.rect(8, y + 1, 8, y + 1), hexc('e3d1ff'))
    tag = c.rect(6, 6, 9, 9)
    c.material(tag, GOLD, shadow=False)
    return c


def row_folder():
    c = Canvas(16, 16)
    back = c.rect(1, 3, 14, 13) | c.rect(1, 2, 6, 3)
    c.material(back, FOLDER_BACK)
    front = c.polygon([(1, 6), (15, 6), (14, 13), (1, 13)])
    c.material(front, FOLDER, shadow=False)
    return c


def row_invalid():
    c = Canvas(16, 16)
    tri = c.polygon([(7.5, 0.5), (15, 14), (0, 14)])
    c.material(tri, RED)
    c.put(c.rect(7, 5, 8, 9) | c.rect(7, 11, 8, 12), hexc('ffffff'))
    return c


def row_grip():
    c = Canvas(16, 16)
    dots = c.mask()
    for x in (4, 9):
        for y in (2, 7, 12):
            dots |= c.rect(x, y, x + 2, y + 2)
    c.material(dots, WHITE)
    return c


def row_lock():
    c = Canvas(16, 16)
    shackle = (c.disc(8, 7, 5.2) & ~c.disc(8, 7, 2.8)) & c.rect(0, 0, 15, 7)
    shackle |= c.rect(3, 6, 5, 8) | c.rect(11, 6, 13, 8)
    c.material(shackle, IRON)
    body = c.rect(2, 7, 13, 14)
    c.material(body, GOLD)
    c.put(c.rect(7, 9, 8, 12), hexc('4a2f0b'))
    return c


def row_check():
    c = Canvas(18, 16)
    tick = c.line([(2, 8), (6, 12), (15, 3)], 3)
    c.material(tick, GREEN)
    return c


def spinner():
    """An 8-frame loading spinner (16x16 frames, shown at 8x8), animated through its .mcmeta."""
    frames = 8
    c = Canvas(16, 16 * frames)
    for f in range(frames):
        for i in range(8):
            angle = i / 8 * 2 * math.pi - math.pi / 2
            cx = 8 + math.cos(angle) * 5.2
            cy = 8 + f * 16 + math.sin(angle) * 5.2
            age = (f - i) % 8
            brightness = max(0.15, 1.0 - age * 0.13)
            dot = c.disc(cx, cy, 1.9)
            c.put(dot, lerp(hexc('1f6d96'), hexc('e4fdff'), brightness))
    return c


# ---- UI chrome (nine-slice, 2x) ---------------------------------------------------------------------

def rounded(c, inset, radius):
    """Rounded rectangle mask inset from the canvas edges."""
    yy, xx = np.mgrid[0:c.h, 0:c.w]
    x0, x1 = inset + radius, c.w - 1 - inset - radius
    y0, y1 = inset + radius, c.h - 1 - inset - radius
    dx = np.maximum(np.maximum(x0 - xx, xx - x1), 0)
    dy = np.maximum(np.maximum(y0 - yy, yy - y1), 0)
    return dx * dx + dy * dy <= (radius + 0.4) ** 2


def panel():
    """Faithful-style version of vanilla's popup frame: black edge, light rim, stone frame, dark inset."""
    c = Canvas(64, 64)
    outer = rounded(c, 0, 3)
    rim = rounded(c, 2, 2)
    frame = rounded(c, 4, 1)
    inner_line = c.rect(10, 10, 53, 53)
    fill = c.rect(12, 12, 51, 51)

    c.put(outer, hexc('000000'))
    c.put(rim, hexc('c6c6c6'))
    # Light top-left, slightly shaded bottom-right rim.
    c.put(rim & ~c.rect(4, 4, 63, 63), hexc('dedede'))
    c.put(rim & ~c.rect(0, 0, 59, 59), hexc('a8a8a8'))
    c.put(frame, hexc('555555'))
    c.noise(frame, 3, 11)
    c.put(frame & ~c.rect(5, 5, 63, 63), hexc('666666'))
    c.put(frame & ~c.rect(0, 0, 58, 58), hexc('474747'))
    c.put(inner_line, hexc('000000'))
    c.put(fill, hexc('303030'))
    c.noise(fill, 2, 12)
    return c


def button(state):
    """20x20 GUI-unit button (40x40 texture), shaded like vanilla's stone button at double detail."""
    c = Canvas(40, 40)
    body = c.rect(0, 0, 39, 39)
    face = c.rect(2, 2, 37, 37)
    if state == 'disabled':
        c.put(body, hexc('000000'))
        c.put(face, hexc('2c2c2c'))
        c.noise(face, 2, 3)
        return c
    outline = hexc('ffffff') if state == 'highlighted' else hexc('000000')
    fill = {'normal': '6f6f6f', 'highlighted': '7a7a7a', 'pressed': '5c5c5c'}[state]
    light = {'normal': 'aaaaaa', 'highlighted': 'b5b5b5', 'pressed': '4a4a4a'}[state]
    shade = {'normal': '565656', 'highlighted': '5e5e5e', 'pressed': '7c7c7c'}[state]
    c.put(body, outline)
    c.put(face, hexc(fill))
    c.noise(face, 4, {'normal': 1, 'highlighted': 2, 'pressed': 3}[state])
    if state == 'pressed':
        # Inset: dark top-left, lighter bottom-right; the face looks pushed in.
        c.put(c.rect(2, 2, 37, 3) | c.rect(2, 2, 3, 37), hexc(light))
        c.put(c.rect(2, 36, 37, 37) | c.rect(36, 2, 37, 37), hexc(shade))
    else:
        c.put(c.rect(2, 2, 37, 3) | c.rect(2, 2, 3, 35), hexc(light))
        c.put(c.rect(2, 2, 37, 2) | c.rect(2, 2, 2, 35), hexc('c8c8c8' if state == 'normal' else 'd2d2d2'))
        c.put(c.rect(2, 34, 37, 37) | c.rect(35, 2, 37, 37), hexc(shade))
        c.put(c.rect(2, 36, 37, 37), hexc('4c4c4c' if state == 'normal' else '545454'))
    return c


def scroller():
    c = Canvas(12, 64)
    c.put(c.rect(0, 0, 11, 63), hexc('c0c0c0'))
    c.put(c.rect(0, 0, 11, 0) | c.rect(0, 0, 0, 63), hexc('e0e0e0'))
    c.put(c.rect(10, 0, 11, 63) | c.rect(0, 62, 11, 63), hexc('808080'))
    c.noise(c.rect(1, 1, 9, 61), 3, 5)
    return c


def scroller_background():
    c = Canvas(12, 64)
    c.put(c.rect(0, 0, 11, 63), hexc('000000'))
    c.put(c.rect(2, 2, 9, 61), hexc('141414'))
    return c


# ---- wordmark, logo and branding --------------------------------------------------------------------

def wordmark(c, x, y, scale, depth=None, deep=False):
    """'SHADOCK' in Facet, Shadock's own display face: a spectrum gradient across the word
    (cyan, blue, violet, pink), a diagonal light glint, a lit top bevel and a deep extrusion.
    deep=True uses darker tones that stay readable on light backgrounds."""
    rows = facet_rows('SHADOCK')
    face = c.bitmap(rows, x, y, scale)
    depth = depth if depth is not None else max(1, scale // 2)
    extrude = np.zeros_like(face)
    for d in range(1, depth + 1):
        shifted = np.zeros_like(face)
        shifted[d:, d // 2:] = face[:-d, :face.shape[1] - d // 2] if d // 2 else face[:-d, :]
        extrude |= shifted
    extrude &= ~face
    c.put(outline_of(face | extrude), hexc('0e0820'))
    ys, xs = np.where(extrude)
    for yy, xx in zip(ys, xs):
        c.px[yy, xx] = lerp(hexc('3b1f7a'), hexc('1c0f40'), (yy - y) / max(1, 9 * scale + depth))

    stops = ['1f8fb8', '3d63d6', '6a3fd6', 'b0399e'] if deep else ['8ff1ff', '7fa6ff', 'b58bff', 'ff8ad8']
    ys, xs = np.where(face)
    left, right = xs.min(), xs.max()
    top, bottom = ys.min(), ys.max()
    for yy, xx in zip(ys, xs):
        t = (xx - left) / max(1, right - left) * (len(stops) - 1)
        i = min(int(t), len(stops) - 2)
        col = lerp(hexc(stops[i]), hexc(stops[i + 1]), t - i)
        # Slightly darker toward the bottom of each letter, for volume.
        shade = (yy - top) / max(1, bottom - top)
        col = lerp(col, (int(col[0] * 0.72), int(col[1] * 0.72), int(col[2] * 0.72), 255), shade * 0.6)
        c.px[yy, xx] = col

    # Lit top bevel.
    up = np.zeros_like(face)
    up[1:] = face[:-1]
    lit = face & ~up
    for _ in range(max(1, scale // 3) - 1):
        grow = np.zeros_like(lit)
        grow[1:] = lit[:-1]
        lit |= grow & face
    c.blend(lit, (255, 255, 255, 150))

    # Diagonal glint band sweeping across the word.
    yy, xx = np.mgrid[0:c.h, 0:c.w]
    width_px = 62 * scale
    band = np.abs((xx - x) - (yy - y) * 0.9 - width_px * 0.32) < max(1.5, scale * 0.9)
    c.blend(face & band, (255, 255, 255, 110))
    return face


def wordmark_sprite():
    """In-game wordmark: 62x9 GUI units, stored at 4x for crisp rendering at any GUI scale."""
    c = Canvas(62 * 4 + 6, 9 * 4 + 6)
    wordmark(c, 1, 1, 4, depth=2)
    return c


def mod_icon():
    """128x128 mod icon, drawn on a 64x64 grid and scaled 2x (32x style)."""
    c = Canvas(64, 64)
    tile = rounded(c, 1, 8)
    c.put(outline_of(tile), hexc('060709'))
    for y in range(64):
        row = tile[y]
        if row.any():
            c.px[y][row] = lerp(hexc('2e3440'), hexc('14171d'), y / 63)
    c.noise(tile, 3, 21)
    c.put(tile & ~rounded(c, 2, 7) & c.rect(0, 0, 63, 31), hexc('4a5262'))

    prism_mark(c, 8, 4, 44)

    # The "dock": a hotbar-like strip of five slots, the middle one selected.
    bar = c.rect(9, 47, 54, 58)
    c.put(bar, hexc('101114'))
    for i in range(5):
        x0 = 11 + i * 9
        slot = c.rect(x0, 49, x0 + 6, 56)
        c.put(slot, hexc('8b8b8b'))
        c.put(c.rect(x0, 49, x0 + 6, 49) | c.rect(x0, 49, x0, 56), hexc('373737'))
        c.put(c.rect(x0 + 1, 56, x0 + 6, 56) | c.rect(x0 + 6, 50, x0 + 6, 56), hexc('ffffff'))
        gem = c.rect(x0 + 2, 51, x0 + 4, 54)
        c.put(gem, hexc(SPECTRUM[(i * 2) % len(SPECTRUM)]))
    sel = c.rect(27, 47, 37, 58) & ~c.rect(28, 48, 36, 57)
    c.put(sel, hexc('ffffff'))
    return c.image().resize((128, 128), Image.NEAREST)


def logo(width, height, scale, background=None, light=False):
    c = Canvas(width, height)
    if background is not None:
        c.put(rounded(c, 0, 6), hexc(background))
    # Shrink the letters if mark + text would not fit the requested width.
    while True:
        mark = 9 * scale + 6
        pad = (height - mark) // 2
        if pad + mark + scale * 2 + 62 * scale + scale // 2 + pad <= width or scale <= 2:
            break
        scale -= 1
    prism_mark(c, pad, pad, mark)
    text_x = pad + mark + scale * 2
    text_y = (height - 9 * scale) // 2 - scale // 3
    wordmark(c, text_x, text_y, scale, deep=light)
    return c.image()


def banner():
    """1200x400 Modrinth/CurseForge banner: drawn at 300x100 and scaled 4x."""
    w, h = 300, 100
    c = Canvas(w, h)
    for y in range(h):
        c.px[y, :] = lerp(hexc('1d2230'), hexc('0d0f15'), y / (h - 1))
    rng = np.random.default_rng(7)
    for _ in range(70):
        x, y = rng.integers(0, w), rng.integers(0, 60)
        c.px[y, x] = lerp(hexc('3a4256'), hexc('c9d6ff'), rng.random())

    # Blocky hills along the bottom (original silhouette).
    heights = []
    level = 86
    for x in range(0, w, 6):
        level = int(max(76, min(92, level + rng.integers(-3, 4))))
        heights.append(level)
    for i, top in enumerate(heights):
        c.put(c.rect(i * 6, top, i * 6 + 5, h - 1), hexc('151922'))
        c.put(c.rect(i * 6, top, i * 6 + 5, top), hexc('262c3a'))

    # Spectrum rays sweeping across the banner behind the title.
    for i, color in enumerate(SPECTRUM):
        band = c.polygon([(64, 48), (64, 50), (w, 62 + i * 4 + 4), (w, 62 + i * 4)])
        r, g, b, _ = hexc(color)
        c.blend(band, (r, g, b, 34))
    prism_mark(c, 12, 18, 62)

    wordmark(c, 84, 24, 3, depth=2)
    tagline = c.bitmap(text_rows('SWITCH. ORGANIZE. IMPORT.'), 86, 64, 1)
    c.put(outline_of(tagline) & c.rect(0, 0, w - 1, h - 1), hexc('0d0f15'))
    c.put(tagline, hexc('d7deec'))
    return c.image().resize((1200, 400), Image.NEAREST)


def main():
    sprites = {
        'import': icon_import, 'folder': icon_folder, 'key': icon_key, 'settings': icon_settings,
        'sort_custom': icon_sort_custom, 'sort_alpha': icon_sort_alpha, 'close': icon_close, 'logo': icon_logo,
        'zip': row_zip, 'pack_folder': row_folder, 'invalid': row_invalid, 'grip': row_grip, 'lock': row_lock,
        'check': row_check, 'wordmark': wordmark_sprite,
    }
    for name, fn in sprites.items():
        save_sprite(name, fn())

    save_sprite('spinner', spinner(), {'animation': {'frametime': 2}})
    save_sprite('panel', panel(), nine_slice(32, 32, 6))
    for state in ('normal', 'highlighted', 'pressed', 'disabled'):
        save_sprite('button_' + state, button(state), nine_slice(20, 20, 3))
    save_sprite('scroller', scroller(), nine_slice(6, 32, 1))
    save_sprite('scroller_background', scroller_background(), nine_slice(6, 32, 1))

    save(os.path.join(ASSETS, 'icon.png'), mod_icon(), 2)
    save(os.path.join(GUI, 'shadock_logo.png'), logo(320, 64, 6), 1)
    save(os.path.join(GUI, 'shadock_banner.png'), banner(), 1)
    save(os.path.join(BRANDING, 'shadock_logo.png'), logo(640, 128, 12), 1)
    save(os.path.join(BRANDING, 'shadock_logo_dark.png'), logo(640, 128, 12, background='14171d'), 1)
    save(os.path.join(BRANDING, 'shadock_logo_light.png'), logo(640, 128, 12, background='eef0f4', light=True), 1)
    save(os.path.join(BRANDING, 'shadock_logo_icon_only.png'), mod_icon().resize((256, 256), Image.NEAREST), 1)
    save(os.path.join(BRANDING, 'shadock_logo_banner.png'), logo(600, 150, 14, background='14171d'), 1)
    save(os.path.join(BRANDING, 'shadock_banner.png'), banner(), 1)
    save(os.path.join(BRANDING, 'shadock_icon.png'), mod_icon(), 1)

    if len(sys.argv) > 1:
        write_preview(sys.argv[1])


def write_preview(path):
    """Contact sheet: GUI sprites magnified on vanilla-button gray, then the branding below."""
    pad = 10
    sprite_tiles = []
    for rel, (img, scale) in outputs.items():
        if 'sprites' not in rel:
            continue
        frame = img.crop((0, 0, img.width, min(img.height, 40)))
        big = frame.resize((frame.width * scale // 2, frame.height * scale // 2), Image.NEAREST)
        tile = Image.new('RGBA', (big.width + 8, big.height + 8), (0x6f, 0x6f, 0x6f, 255))
        tile.paste(big, (4, 4), big)
        sprite_tiles.append(tile)
    rows = []
    row, width = [], 0
    for tile in sprite_tiles:
        if width + tile.width > 1400 and row:
            rows.append(row)
            row, width = [], 0
        row.append(tile)
        width += tile.width + pad
    rows.append(row)
    branding = [outputs[k][0] for k in outputs if k.startswith('branding') and 'icon_only' not in k and not k.endswith('shadock_icon.png')]
    sheet_h = sum(max(t.height for t in r) + pad for r in rows) + sum(b.height // 2 + pad for b in branding) + pad * 2
    sheet = Image.new('RGBA', (1440, sheet_h), (0x20, 0x20, 0x20, 255))
    y = pad
    for r in rows:
        x = pad
        for tile in r:
            sheet.paste(tile, (x, y))
            x += tile.width + pad
        y += max(t.height for t in r) + pad
    for b in branding:
        small = b.resize((b.width // 2, b.height // 2), Image.NEAREST)
        sheet.paste(small, (pad, y), small)
        y += small.height + pad
    sheet.save(path)


if __name__ == '__main__':
    main()
