"""Tiny pixel-art toolkit used to generate Shadock's 32x (double resolution) assets.

Shapes are boolean masks (numpy). A "material" paints a mask the way Faithful-style textures are shaded:
a lit top-left edge, a shaded bottom-right edge, a vertical body gradient, and a darker outline around it.
Everything is deterministic, so re-running the generator always gives the same pixels.
"""
import math

import numpy as np
from PIL import Image, ImageDraw


def hexc(value, alpha=255):
    value = value.lstrip('#')
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha)


class Material:
    def __init__(self, light, top, bottom, dark, outline):
        self.light = hexc(light)
        self.top = hexc(top)
        self.bottom = hexc(bottom)
        self.dark = hexc(dark)
        self.outline = hexc(outline)


# Palettes tuned to sit next to vanilla GUI art.
GOLD = Material('fff3a6', 'f7cd45', 'e0a224', 'a8661a', '4a2f0b')
GREEN = Material('a6f7a0', '4fdc63', '27b04a', '15773a', '0b3d1c')
IRON = Material('ffffff', 'd9dde2', 'b4bac2', '7d838c', '30333a')
GLASS = Material('e4fdff', '7fe3f2', '33a9cf', '1f6d96', '0f2f45')
VIOLET = Material('e3d1ff', 'a983ff', '7c4ee6', '4f2aa6', '210f4d')
FOLDER = Material('ffe7a0', 'f4c45a', 'df9f37', 'a86a1e', '4a2f0b')
FOLDER_BACK = Material('e8b85a', 'd29a3c', 'b8812c', '8a5a18', '4a2f0b')
RED = Material('ffb0b0', 'f25555', 'd63434', '8e1b1b', '3d0808')
WHITE = Material('ffffff', 'f4f4f8', 'd7d7e0', '9a9aa8', '26262c')
STONE_DARK = Material('5a5f68', '3d4149', '30333a', '22252b', '0c0d10')


class Canvas:
    def __init__(self, width, height):
        self.w = width
        self.h = height
        self.px = np.zeros((height, width, 4), dtype=np.uint8)

    # ---- masks ---------------------------------------------------------------------------------

    def mask(self):
        return np.zeros((self.h, self.w), dtype=bool)

    def rect(self, x0, y0, x1, y1):
        """Inclusive rectangle."""
        m = self.mask()
        m[max(0, y0):min(self.h, y1 + 1), max(0, x0):min(self.w, x1 + 1)] = True
        return m

    def disc(self, cx, cy, r):
        yy, xx = np.mgrid[0:self.h, 0:self.w]
        return (xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2 <= r * r

    def polygon(self, points):
        img = Image.new('1', (self.w, self.h), 0)
        ImageDraw.Draw(img).polygon(points, fill=1)
        return np.array(img, dtype=bool)

    def line(self, points, width):
        img = Image.new('1', (self.w, self.h), 0)
        ImageDraw.Draw(img).line(points, fill=1, width=width, joint='curve')
        return np.array(img, dtype=bool)

    def polar(self, cx, cy, radius_fn):
        yy, xx = np.mgrid[0:self.h, 0:self.w]
        dx = xx + 0.5 - cx
        dy = yy + 0.5 - cy
        return np.hypot(dx, dy) <= np.vectorize(radius_fn)(np.arctan2(dy, dx))

    def bitmap(self, rows, x, y, scale=1):
        m = self.mask()
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch != '.' and ch != ' ':
                    m[y + j * scale:y + (j + 1) * scale, x + i * scale:x + (i + 1) * scale] = True
        return m

    # ---- painting ------------------------------------------------------------------------------

    def put(self, mask, color):
        self.px[mask] = color

    def blend(self, mask, color):
        a = color[3] / 255.0
        sel = self.px[mask].astype(float)
        src = np.array(color[:3], dtype=float)
        out_a = a + sel[:, 3] / 255.0 * (1 - a)
        rgb = (src * a + sel[:, :3] * (sel[:, 3:4] / 255.0) * (1 - a)) / np.maximum(out_a[:, None], 1e-6)
        sel[:, :3] = rgb
        sel[:, 3] = out_a * 255
        self.px[mask] = sel.astype(np.uint8)

    def material(self, mask, mat, outline=True, shadow=True, highlight=True):
        """Paints a shaded shape: gradient body, lit top-left edge, dark bottom-right edge, outline."""
        if shadow:
            sh = np.zeros_like(mask)
            sh[1:, 1:] = mask[:-1, :-1]
            ring = outline_of(mask) if outline else np.zeros_like(mask)
            self.blend(sh & ~mask & ~ring, (0, 0, 0, 90))
        if outline:
            self.put(outline_of(mask), mat.outline)

        ys = np.where(mask.any(axis=1))[0]
        top, bottom = (ys.min(), ys.max()) if len(ys) else (0, 1)
        for y in range(self.h):
            row = mask[y]
            if not row.any():
                continue
            t = (y - top) / max(1, bottom - top)
            self.px[y][row] = lerp(mat.top, mat.bottom, t)

        up = np.zeros_like(mask)
        up[1:] = mask[:-1]
        left = np.zeros_like(mask)
        left[:, 1:] = mask[:, :-1]
        down = np.zeros_like(mask)
        down[:-1] = mask[1:]
        right = np.zeros_like(mask)
        right[:, :-1] = mask[:, 1:]
        dark_edge = mask & (~down | ~right)
        light_edge = mask & (~up | ~left) & ~dark_edge
        self.put(dark_edge, mat.dark)
        if highlight:
            self.put(light_edge, mat.light)

    def noise(self, mask, amount, seed):
        """Subtle per-pixel brightness noise, like Faithful's stone and wood."""
        rng = np.random.default_rng(seed)
        delta = rng.integers(-amount, amount + 1, size=(self.h, self.w))
        for c in range(3):
            channel = self.px[:, :, c].astype(int)
            channel[mask] = np.clip(channel[mask] + delta[mask], 0, 255)
            self.px[:, :, c] = channel

    def image(self):
        return Image.fromarray(self.px, 'RGBA')


def outline_of(mask):
    """Pixels outside the mask that touch it (4-neighbourhood)."""
    grown = mask.copy()
    grown[1:] |= mask[:-1]
    grown[:-1] |= mask[1:]
    grown[:, 1:] |= mask[:, :-1]
    grown[:, :-1] |= mask[:, 1:]
    return grown & ~mask


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def gear_radius(teeth, outer, inner, tooth_width):
    def radius(angle):
        phase = (angle / (2 * math.pi) * teeth) % 1.0
        return outer if abs(phase - 0.5) < tooth_width / 2 else inner
    return radius


# 5x7 pixel font for the wordmark and tagline (original glyphs).
FONT = {
    'A': ['.###.', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'],
    'C': ['.####', '#....', '#....', '#....', '#....', '#....', '.####'],
    'D': ['####.', '#...#', '#...#', '#...#', '#...#', '#...#', '####.'],
    'E': ['#####', '#....', '#....', '####.', '#....', '#....', '#####'],
    'G': ['.####', '#....', '#....', '#.###', '#...#', '#...#', '.####'],
    'H': ['#...#', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'],
    'I': ['###', '.#.', '.#.', '.#.', '.#.', '.#.', '###'],
    'K': ['#...#', '#..#.', '#.#..', '##...', '#.#..', '#..#.', '#...#'],
    'M': ['#...#', '##.##', '#.#.#', '#.#.#', '#...#', '#...#', '#...#'],
    'N': ['#...#', '##..#', '#.#.#', '#.#.#', '#..##', '#...#', '#...#'],
    'O': ['.###.', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'],
    'P': ['####.', '#...#', '#...#', '####.', '#....', '#....', '#....'],
    'R': ['####.', '#...#', '#...#', '####.', '#.#..', '#..#.', '#...#'],
    'S': ['.####', '#....', '#....', '.###.', '....#', '....#', '####.'],
    'T': ['#####', '..#..', '..#..', '..#..', '..#..', '..#..', '..#..'],
    'W': ['#...#', '#...#', '#...#', '#.#.#', '#.#.#', '##.##', '#...#'],
    'Z': ['#####', '....#', '...#.', '..#..', '.#...', '#....', '#####'],
    '.': ['.', '.', '.', '.', '.', '.', '#'],
    ' ': ['..', '..', '..', '..', '..', '..', '..'],
}


# "Facet": Shadock's display face for the wordmark (original). 8x9 glyphs, 2 px strokes,
# 45-degree chamfered corners like a cut gem.
FACET = {
    'S': ['..######', '.#######', '##......', '##......', '.######.', '......##', '......##', '#######.', '######..'],
    'H': ['##....##', '##....##', '##....##', '##....##', '########', '########', '##....##', '##....##', '##....##'],
    'A': ['..####..', '.######.', '###..###', '##....##', '########', '########', '##....##', '##....##', '##....##'],
    'D': ['######..', '#######.', '##...###', '##....##', '##....##', '##....##', '##...###', '#######.', '######..'],
    'O': ['..####..', '.######.', '###..###', '##....##', '##....##', '##....##', '###..###', '.######.', '..####..'],
    'C': ['..######', '.#######', '###.....', '##......', '##......', '##......', '###.....', '.#######', '..######'],
    'K': ['##...###', '##..###.', '##.###..', '#####...', '####....', '#####...', '##.###..', '##..###.', '##...###'],
}


def facet_rows(text, spacing=1):
    rows = [''] * 9
    for i, ch in enumerate(text):
        glyph = FACET[ch]
        for r in range(9):
            rows[r] += glyph[r] + ('.' * spacing if i < len(text) - 1 else '')
    return rows


def text_rows(text, spacing=1):
    """Combines glyphs into one bitmap (list of 7 strings)."""
    rows = [''] * 7
    for i, ch in enumerate(text):
        glyph = FONT[ch]
        for r in range(7):
            rows[r] += glyph[r] + ('.' * spacing if i < len(text) - 1 else '')
    return rows
