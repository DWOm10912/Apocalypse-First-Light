# Field Survival Guide V1 concept (2026-10-21): typesetting with the mod's own font. Every letter is drawn from
# src/main/resources/assets/apocalypse_firstlight/font/afl_sans_sc.ttf (AFL Sans SC, the game's font, user 2026-10-21:
# "不要改中文字体，英文用一样的也行"), as outlines, so widths and line breaks match what the game would measure. The font
# has one weight: bold is the game's way, the same glyph stroked. Characters the subset lacks are listed (MISSING) and
# drawn from Noto Sans SC, only so the concept shows the page.
import os
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
FONT = ROOT + '/src/main/resources/assets/apocalypse_firstlight/font/afl_sans_sc.ttf'
FALLBACK = 'C:/Windows/Fonts/NotoSansSC-VF.ttf'

MISSING = set()


class Face:
    def __init__(self, path):
        self.f = TTFont(path)
        self.cmap = self.f.getBestCmap()
        self.gs = self.f.getGlyphSet()
        self.upm = self.f['head'].unitsPerEm
        self.hmtx = self.f['hmtx']
        self.paths = {}

    def has(self, ch):
        return ord(ch) in self.cmap

    def adv(self, ch):
        return self.hmtx[self.cmap[ord(ch)]][0] / self.upm

    def path(self, ch):
        name = self.cmap[ord(ch)]
        if name not in self.paths:
            pen = SVGPathPen(self.gs)
            # glyph units -> 1 em, y down
            self.gs[name].draw(TransformPen(pen, (1 / self.upm, 0, 0, -1 / self.upm, 0, 0)))
            self.paths[name] = pen.getCommands()
        return name, self.paths[name]


MAIN = Face(FONT)
BACK = Face(FALLBACK)


def face(ch):
    if MAIN.has(ch):
        return MAIN
    if not ch.isspace():
        MISSING.add(ch)
    return BACK if BACK.has(ch) else MAIN


def width(s, size, track=0.0):
    w = 0.0
    for ch in s:
        if ch == '\u3000':
            w += size
            continue
        fc = face(ch)
        w += (fc.adv(ch) if fc.has(ch) else 0.5) * size + track
    return w


class Doc:
    """An SVG drawn in px; text becomes <use> of glyph paths kept once in <defs>."""

    def __init__(self, w, h):
        self.w, self.h = w, h
        self.body, self.defs, self.used = [], [], set()

    def add(self, s):
        self.body.append(s)
        return self

    def text(self, x, y, s, size, fill='#23211D', bold=False, anchor='start', track=0.0, op=1.0):
        w = width(s, size, track)
        if anchor == 'middle':
            x -= w / 2
        elif anchor == 'end':
            x -= w
        parts = []
        for ch in s:
            if ch in (' ', '\u3000'):
                x += (size if ch == '\u3000' else face(' ').adv(' ') * size) + track
                continue
            fc = face(ch)
            if not fc.has(ch):
                x += 0.5 * size
                continue
            name, d = fc.path(ch)
            gid = ('m' if fc is MAIN else 'b') + str(abs(hash(name)) % 10**9)
            if gid not in self.used:
                self.used.add(gid)
                self.defs.append(f'<path id="{gid}" d="{d}"/>')
            parts.append(f'<use href="#{gid}" transform="translate({x:.2f},{y:.2f}) scale({size:.3f})"/>')
            x += fc.adv(ch) * size + track
        stroke = f' stroke="{fill}" stroke-width="{0.045:.3f}" stroke-linejoin="round"' if bold else ''
        # the stroke width is in glyph space (1 em), scaled with each glyph
        self.add(f'<g fill="{fill}"{stroke} opacity="{op}">' + ''.join(parts) + '</g>')
        return w

    def svg(self, extra_defs=''):
        return (f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="{self.w}" '
                f'height="{self.h}" viewBox="0 0 {self.w} {self.h}"><defs>{extra_defs}{"".join(self.defs)}</defs>'
                + ''.join(self.body) + '</svg>')


# ------------------------------------------------------------------------------------------------ line breaking

NO_START = set('，。、；：！？）》」』”’…—·,.;:!?)]')
NO_END = set('（《「『“‘([')


def tokens(s):
    """Chinese breaks between characters; Latin words, numbers and their trailing punctuation stay whole."""
    out, cur = [], ''
    for ch in s:
        if ch == ' ':
            if cur:
                out.append(cur)
            out.append(' ')
            cur = ''
        elif ord(ch) < 0x2E80 and ch not in NO_START and ch not in '“”‘’—…·':
            cur += ch
        else:
            if cur:
                out.append(cur)
                cur = ''
            out.append(ch)
    if cur:
        out.append(cur)
    # glue: closing marks onto the token before, opening marks onto the token after
    glued = []
    for t in out:
        if glued and t in NO_START:
            glued[-1] += t
        elif glued and glued[-1] and glued[-1][-1] in NO_END and t != ' ':
            glued[-1] += t
        else:
            glued.append(t)
    return glued


def wrap(s, size, maxw, indent=0.0):
    lines, cur, w = [], '', indent
    for t in tokens(s):
        tw = width(t, size)
        if t == ' ' and not cur:
            continue
        if w + tw > maxw and cur.strip():
            lines.append(cur.rstrip())
            cur, w = ('' if t == ' ' else t), (0 if t == ' ' else tw)
        else:
            cur += t
            w += tw
    if cur.strip():
        lines.append(cur.rstrip())
    return lines
