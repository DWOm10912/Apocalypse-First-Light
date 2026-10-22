# Field Survival Guide V1 concept (2026-10-21): the cover (English on the item's model, the game language on the page you
# open) and three pages (contents, 18, 19) in zh_cn and en_us, at 100 px per cm of a 11.5 x 18 cm pocket book.
#   python design/items/field_survival_guide_v1/fsg_pages.py   (then node fsg_raster.cjs)
import math
import os
import random
from fsg_type import Doc, wrap, width, MISSING

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'out')
os.makedirs(OUT, exist_ok=True)
PW, PH = 1150, 1800
INK, GREY, PAPER = '#23211D', '#6E675C', '#EEE6D3'
ORANGE, GREEN, CREAM, LINE = '#D9632B', '#2E4734', '#E9DFC6', '#4C6A4F'

# ------------------------------------------------------------------------------------------------ the text

T = {
    'zh_cn': {
        'title': '野外求生指南', 'sub': '住处　火　水　食物　求救　急救', 'edition': '修订第四版',
        'author': '哈伦·普鲁伊特　著', 'bio': '做了二十六年林区巡护员', 'press': '林线出版社',
        'head_l': '野外求生指南', 'head_r': '第三章　火',
        'toc': '目录',
        'toc_rows': [('前言', '怎么用这本书', 1), ('第一章', '先停下来', 4), ('第二章', '住处', 10), ('第三章', '火', 18),
                     ('第四章', '水', 26), ('第五章', '食物', 32), ('第六章', '方向和求救', 38), ('第七章', '受伤和生病', 42),
                     ('附录', '常用绳结', 46)],
        'ch_label': '第三章', 'ch_title': '火',
        'p18': [
            ('p', '火能给你热量、开水和光，还能让人定下心来。在野外出事的人，很多不是不会生火，而是拖到天黑、手冻僵了才想起来要生。'),
            ('p', '趁天还亮、手还暖，先把火生起来。'),
            ('h', '生火要备齐三样东西'),
            ('b', '引火物', '一点火星就能着的东西：干草、桦树皮、带松脂的木屑、香蒲的绒毛。'),
            ('b', '细柴', '比铅笔还细的干树枝。折的时候要“啪”地一声脆断；弯而不断的，还是湿的。'),
            ('b', '粗柴', '手腕粗的木头，等火烧稳了再一根一根加。'),
            ('p', '三样都备好，而且要备够你觉得够用的两倍，再划火柴。火柴就那么几根。'),
            ('tip', '下雨天找干柴', '站着的枯树比倒在地上的木头干。把外面一层劈掉，里面常常是干的。'),
        ],
        'p19_title': '弓钻',
        'p19_intro': '火柴用完了，还可以用弓钻。它慢，也费力气，但只要木头够干，总能钻出火来。',
        'fig': '图 3-2　弓钻的四个部件',
        'fig_labels': ['弓', '钻杆', '火板', '手压块', '缺口'],
        'need': '你需要',
        'items': [('弓', '一根稍微有点弯的树枝，大约一条胳膊长，两头拴上绳子。鞋带、伞绳都行。'),
                  ('钻杆', '一根笔直的干木棍，拇指粗，二十厘米左右长。'),
                  ('火板', '一块干燥的软木板，两厘米左右厚。椴木、柳木、雪松最好；松木也能用，只是松脂多，容易打滑。'),
                  ('手压块', '手掌大的一块硬木或石头，底下挖个浅窝，压住钻杆的上端。')],
        'next': '做法见下一页',
    },
    'en_us': {
        'title': 'FIELD SURVIVAL GUIDE', 'sub': 'SHELTER · FIRE · WATER · FOOD · SIGNALS · FIRST AID',
        'edition': 'REVISED FOURTH EDITION', 'author': 'HARLAN PRUITT', 'bio': 'Twenty-six years a backcountry ranger',
        'press': 'TIMBERLINE PRESS',
        'head_l': 'FIELD SURVIVAL GUIDE', 'head_r': '3 · FIRE',
        'toc': 'Contents',
        'toc_rows': [('', 'How to Use This Book', 1), ('1', 'Stop First', 4), ('2', 'Shelter', 10), ('3', 'Fire', 18),
                     ('4', 'Water', 26), ('5', 'Food', 32), ('6', 'Direction and Signals', 38),
                     ('7', 'Injury and Illness', 42), ('', 'Appendix: Knots', 46)],
        'ch_label': 'CHAPTER 3', 'ch_title': 'Fire',
        'p18': [
            ('p', 'A fire gives you heat, boiled water and light, and it settles your nerves. Plenty of people who get '
                  'into trouble out here could have built one. They waited until dark, when their hands were too cold.'),
            ('p', 'Build it while there is still light and your hands are still warm.'),
            ('h', 'Get three things ready'),
            ('b', 'Tinder', 'anything that catches from a spark: dry grass, birch bark, pitchy pine shavings, cattail fluff.'),
            ('b', 'Kindling', 'dry twigs thinner than a pencil. They should snap clean. If they bend, they are still wet.'),
            ('b', 'Fuel', 'wood as thick as your wrist. Add it one piece at a time once the fire is steady.'),
            ('p', 'Gather all three, twice as much as you think you need, before you strike a match. You only have so '
                  'many matches.'),
            ('tip', 'Dry wood in the rain', 'A standing dead tree is drier than anything lying on the ground. Split off '
                                            'the outside; the wood inside is often dry.'),
        ],
        'p19_title': 'The Bow Drill',
        'p19_intro': 'When the matches are gone, there is the bow drill. It is slow and it is hard work, but with dry '
                     'enough wood it will give you fire.',
        'fig': 'Fig. 3-2  The four parts of a bow drill',
        'fig_labels': ['Bow', 'Spindle', 'Fireboard', 'Handhold', 'Notch'],
        'need': 'What you need',
        'items': [('Bow', 'a slightly curved stick about as long as your arm, with cord tied to both ends. A bootlace '
                          'or parachute cord will do.'),
                  ('Spindle', 'a straight, dry stick as thick as your thumb and about 8 inches long.'),
                  ('Fireboard', 'a dry board of soft wood about 3/4 inch thick. Basswood, willow and cedar are best; '
                                'pine works, but the pitch makes it slip.'),
                  ('Handhold', 'a palm-sized piece of hardwood or a stone with a shallow socket underneath, to hold '
                               'the top of the spindle.')],
        'next': 'Steps on the next page',
    },
}

# ------------------------------------------------------------------------------------------------ paper


def paper_defs(seed):
    return (f'<filter id="grain" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" '
            f'baseFrequency="0.9" numOctaves="2" seed="{seed}" result="n"/><feColorMatrix in="n" type="matrix" '
            f'values="0 0 0 0 0.35  0 0 0 0 0.30  0 0 0 0 0.22  0 0 0 0.10 0"/></filter>'
            f'<filter id="blotch" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" '
            f'baseFrequency="0.006" numOctaves="3" seed="{seed + 7}" result="n"/><feColorMatrix in="n" type="matrix" '
            f'values="0 0 0 0 0.55  0 0 0 0 0.45  0 0 0 0 0.28  0 0 0 0.16 -0.04"/></filter>')


def paper(d, left, gutter=True):
    gx1, gx2 = (PW, PW - 140) if left else (0, 140)
    d.add(f'<rect width="{PW}" height="{PH}" fill="{PAPER}"/>')
    d.add(f'<rect width="{PW}" height="{PH}" filter="url(#blotch)"/>')
    d.add(f'<rect width="{PW}" height="{PH}" filter="url(#grain)"/>')
    if not gutter:   # the game's page: the reader draws the gutter shade itself, on either side
        return
    # the gutter: the page curves into the spine
    d.add(f'<linearGradient id="gut" x1="{gx1}" y1="0" x2="{gx2}" y2="0" gradientUnits="userSpaceOnUse">'
          f'<stop offset="0" stop-color="#5A4A30" stop-opacity="0.30"/><stop offset="1" stop-color="#5A4A30" stop-opacity="0"/>'
          f'</linearGradient><rect width="{PW}" height="{PH}" fill="url(#gut)"/>')


def running(d, lang, left, num):
    t = T[lang]
    inner, outer = (110, PW - 120) if left else (120, PW - 110)
    if left:
        d.text(inner, 96, t['head_l'], 26, GREY, track=2)
        d.text(inner, PH - 86, str(num), 30, GREY)
    else:
        d.text(outer, 96, t['head_r'], 26, GREY, anchor='end', track=2)
        d.text(outer, PH - 86, str(num), 30, GREY, anchor='end')
    d.add(f'<line x1="{inner}" y1="118" x2="{outer}" y2="118" stroke="{GREY}" stroke-width="1.5" opacity="0.6"/>')


# ------------------------------------------------------------------------------------------------ body blocks

class Flow:
    def __init__(self, d, lang, x, y, w):
        self.d, self.lang, self.x, self.y, self.w = d, lang, x, y, w
        self.zh = lang == 'zh_cn'
        self.size = 38 if self.zh else 35
        self.lh = 66 if self.zh else 50

    def para(self, s, indent=True, gap=16):
        ind = 2 * self.size if (self.zh and indent) else 0
        for i, line in enumerate(wrap(s, self.size, self.w, ind)):
            self.d.text(self.x + (ind if i == 0 else 0), self.y + self.size, line, self.size, INK)
            self.y += self.lh
        self.y += gap

    def head(self, s):
        self.y += 8
        self.d.text(self.x, self.y + 42, s, 42, INK, bold=True)
        self.y += 42 + 22

    def bullet(self, lead, s):
        lw = self.d.text(self.x + 30, self.y + self.size, lead + ('　' if self.zh else ' — '), self.size, INK, bold=True)
        self.d.add(f'<rect x="{self.x + 4}" y="{self.y + self.size - 18}" width="10" height="10" fill="{INK}"/>')
        first = wrap(s, self.size, self.w - 30 - lw)[0] if s else ''
        rest = s[len(first):].lstrip()
        self.d.text(self.x + 30 + lw, self.y + self.size, first, self.size, INK)
        self.y += self.lh
        for line in wrap(rest, self.size, self.w - 30):
            self.d.text(self.x + 30, self.y + self.size, line, self.size, INK)
            self.y += self.lh
        self.y += 10

    def tip(self, title, s):
        z = 34 if self.zh else 32
        lines = wrap(s, z, self.w - 70)
        h = 34 + 46 + len(lines) * (56 if self.zh else 46) + 24
        self.y += 12
        self.d.add(f'<rect x="{self.x}" y="{self.y}" width="{self.w}" height="{h}" fill="#5A4A30" opacity="0.08"/>')
        self.d.add(f'<rect x="{self.x}" y="{self.y}" width="8" height="{h}" fill="{ORANGE}" opacity="0.85"/>')
        self.d.text(self.x + 40, self.y + 34 + 30, title, 36, INK, bold=True)
        yy = self.y + 34 + 46
        for line in lines:
            self.d.text(self.x + 40, yy + z, line, z, INK)
            yy += 56 if self.zh else 46
        self.y += h + 20


# ------------------------------------------------------------------------------------------------ the bow drill figure

def bow_drill(d, ox, oy, w, h, labels, lang):
    """Ink line drawing, 920 x 560 design units scaled to w x h."""
    s = w / 920
    g = [f'<g transform="translate({ox},{oy}) scale({s})" fill="none" stroke="{INK}" stroke-linecap="round" stroke-linejoin="round">']
    wood = '#D9CCB0'
    # fireboard in perspective, with its thickness
    g.append(f'<path d="M70 470 L700 470 L800 410 L170 410 Z" fill="{wood}" stroke-width="4"/>')
    g.append('<path d="M70 470 L70 494 L700 494 L700 470 Z" fill="#C7B793" stroke-width="4"/>')
    g.append('<path d="M700 470 L700 494 L800 434 L800 410 Z" fill="#B8A77F" stroke-width="4"/>')   # the end grain
    for i in range(6):   # grain on the board
        y = 420 + i * 9
        g.append(f'<path d="M{185 - i * 17} {y} C 330 {y - 6}, 520 {y + 6}, {780 - i * 17} {y}" stroke-width="1.6" opacity="0.55"/>')
    # the notch and the socket at the near edge, ember pile on a bark chip under it
    g.append('<path d="M452 470 L470 446 L488 470" fill="#4A3B28" stroke-width="3"/>')
    g.append('<ellipse cx="478" cy="441" rx="20" ry="7" fill="#6B5435" stroke-width="3"/>')
    g.append('<path d="M410 512 C 440 500, 520 500, 548 512 C 520 522, 440 522, 410 512 Z" fill="#B59A70" stroke-width="3"/>')
    g.append('<path d="M450 508 C 462 492, 490 492, 504 508 Z" fill="#2B241B" stroke-width="2.5"/>')
    for x, y in ((470, 486), (482, 480), (494, 488)):   # smoke
        g.append(f'<path d="M{x} {y} c -8 -14, 10 -22, 0 -38" stroke-width="1.8" opacity="0.6"/>')
    # spindle: from the socket up to the handhold
    g.append('<path d="M468 440 L462 150 L492 150 L488 440 Z" fill="#E4D8BE" stroke-width="4"/>')
    for i in range(3):
        x = 470 + i * 7
        g.append(f'<path d="M{x} 160 L{x + 2} 430" stroke-width="1.4" opacity="0.5"/>')
    # handhold block, socket underneath
    g.append(f'<path d="M412 150 C 410 112, 430 96, 478 96 C 530 96, 548 112, 546 150 Z" fill="{wood}" stroke-width="4"/>')
    g.append('<path d="M425 128 C 450 118, 505 118, 534 128" stroke-width="1.6" opacity="0.55"/>')
    # bow: a crooked branch, a stub of a twig, the cord wrapped once round the spindle
    g.append('<path d="M120 330 C 230 250, 420 214, 600 222 C 700 226, 790 250, 846 286" stroke-width="12" stroke="#5A4630"/>')
    g.append('<path d="M120 330 C 230 250, 420 214, 600 222 C 700 226, 790 250, 846 286" stroke-width="6" stroke="#B39A72"/>')
    g.append('<path d="M330 240 l -26 -30" stroke-width="7" stroke="#5A4630"/>')
    g.append(f'<path d="M126 326 L462 298" stroke-width="2.4"/>')
    g.append(f'<path d="M462 298 C 476 292, 496 296, 492 306 C 488 314, 470 316, 462 312" stroke-width="2.4"/>')
    g.append(f'<path d="M492 312 L842 290" stroke-width="2.4"/>')
    # the notch close-up: the board seen from above
    g.append('<circle cx="140" cy="140" r="92" fill="#EEE6D3" stroke-width="3" stroke-dasharray="8 7"/>')
    g.append(f'<path d="M78 200 L78 104 L214 104 L214 200" fill="{wood}" stroke-width="3"/>')
    g.append('<circle cx="146" cy="150" r="22" fill="#6B5435" stroke-width="3"/>')
    g.append('<path d="M134 172 L146 200 L158 172" fill="#2B241B" stroke-width="3"/>')
    g.append('<path d="M232 196 L452 456" stroke-width="1.6" stroke-dasharray="6 6" opacity="0.7"/>')
    g.append('</g>')
    d.add(''.join(g))
    # callouts: number in a circle, leader line, the part's name
    pts = [((690, 214), (720, 140)), ((496, 330), (600, 360)), ((260, 456), (210, 540)), ((546, 118), (640, 70))]
    for i, ((px, py), (lx, ly)) in enumerate(pts):
        X, Y = ox + px * s, oy + py * s
        LX, LY = ox + lx * s, oy + ly * s
        d.add(f'<line x1="{X}" y1="{Y}" x2="{LX}" y2="{LY}" stroke="{INK}" stroke-width="1.6"/>'
              f'<circle cx="{LX}" cy="{LY}" r="19" fill="{PAPER}" stroke="{INK}" stroke-width="2"/>')
        d.text(LX, LY + 9, str(i + 1), 26, INK, anchor='middle')
        if labels:   # None: the game lays the names over the drawing in the reader's language
            d.text(LX + 30, LY + 11, labels[i], 30, INK)
    if labels:
        d.text(ox + 140 * s, oy + 262 * s, labels[4], 28, INK, anchor='middle')


# ------------------------------------------------------------------------------------------------ pages

def page18(lang):
    t, d = T[lang], Doc(PW, PH)
    paper(d, True)
    running(d, lang, True, 18)
    x, w = 110, PW - 230
    d.text(x, 230, t['ch_label'], 34, ORANGE, bold=True, track=4)
    d.text(x - 4, 360, t['ch_title'], 120, INK, bold=True)
    d.add(f'<rect x="{x}" y="398" width="120" height="8" fill="{ORANGE}"/>')
    f = Flow(d, lang, x, 448, w)
    for b in t['p18']:
        if b[0] == 'p':
            f.para(b[1])
        elif b[0] == 'h':
            f.head(b[1])
        elif b[0] == 'b':
            f.bullet(b[1], b[2])
        else:
            f.tip(b[1], b[2])
    return d, f.y


def page19(lang):
    t, d = T[lang], Doc(PW, PH)
    paper(d, False)
    running(d, lang, False, 19)
    x, w = 120, PW - 230
    d.text(x, 230, t['p19_title'], 64, INK, bold=True)
    f = Flow(d, lang, x, 262, w)
    f.para(t['p19_intro'], indent=True, gap=10)
    fy = f.y
    fw = w if lang == 'zh_cn' else w * 0.9
    bow_drill(d, x + (w - fw) / 2, fy, fw, fw * 560 / 920, t['fig_labels'], lang)
    fy += fw * 560 / 920 + 6
    d.text(x + w / 2, fy + 30, t['fig'], 28, GREY, anchor='middle')
    f.y = fy + 62
    f.head(t['need'])
    z = 34 if lang == 'zh_cn' else 32
    f.size, f.lh = z, (58 if lang == 'zh_cn' else 46)
    for i, (lead, s) in enumerate(t['items']):
        num = '①②③④'[i]
        lw = d.text(x, f.y + z, num + ' ' + lead + ('　' if lang == 'zh_cn' else ' — '), z, INK, bold=True)
        first = wrap(s, z, w - lw)[0]
        d.text(x + lw, f.y + z, first, z, INK)
        f.y += f.lh
        for line in wrap(s[len(first):].lstrip(), z, w - 52):
            d.text(x + 52, f.y + z, line, z, INK)
            f.y += f.lh
        f.y += 6
    d.text(PW - 110, PH - 140, t['next'] + ' →', 30, GREY, anchor='end')
    return d, f.y


def toc(lang):
    t, d = T[lang], Doc(PW, PH)
    paper(d, False)
    x, w = 140, PW - 280
    d.text(x, 300, t['toc'], 72, INK, bold=True)
    d.add(f'<rect x="{x}" y="336" width="120" height="8" fill="{ORANGE}"/>')
    y = 470
    for a, b, n in t['toc_rows']:
        lw = 0
        if a:
            lw = d.text(x, y, a, 38, ORANGE if lang == 'en_us' else GREY) + 24
            if lang == 'en_us':
                lw = 70
        tw = d.text(x + lw, y, b, 38, INK)
        nw = width(str(n), 38)
        d.text(x + w, y, str(n), 38, INK, anchor='end')
        d.add(f'<line x1="{x + lw + tw + 16}" y1="{y - 8}" x2="{x + w - nw - 16}" y2="{y - 8}" stroke="{GREY}" '
              f'stroke-width="3" stroke-dasharray="2 12" stroke-linecap="round"/>')
        y += 104
    return d, y


# ------------------------------------------------------------------------------------------------ cover

def contours(cx, cy, seed, clip):
    rnd = random.Random(seed)
    ph = [rnd.uniform(0, 6.28) for _ in range(6)]
    out = []
    for k in range(1, 22):
        r0 = 48 * k
        pts = []
        for i in range(0, 181):
            a = i / 180 * 2 * math.pi
            r = r0 * (1 + 0.16 * math.sin(3 * a + ph[0] + k * 0.12) + 0.08 * math.sin(5 * a + ph[1] - k * 0.09)
                      + 0.05 * math.sin(2 * a + ph[2]))
            pts.append(f'{cx + r * math.cos(a) * 1.15:.1f},{cy + r * math.sin(a) * 0.85:.1f}')
        sw = 3.2 if k % 5 == 0 else 1.8
        out.append(f'<polyline points="{" ".join(pts)}" fill="none" stroke="{LINE}" stroke-width="{sw}"/>')
    return f'<g clip-path="url(#{clip})">' + ''.join(out) + '</g>'


def compass(d, cx, cy, r, letters):
    d.add(f'<circle cx="{cx}" cy="{cy + 8}" r="{r}" fill="#000" opacity="0.25"/>'
          f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{CREAM}"/>'
          f'<circle cx="{cx}" cy="{cy}" r="{r * 0.80}" fill="{GREEN}"/>'
          f'<circle cx="{cx}" cy="{cy}" r="{r * 0.80}" fill="none" stroke="{CREAM}" stroke-width="3"/>')
    for i in range(72):
        a = i * 5 / 180 * math.pi
        l = 0.10 if i % 6 == 0 else 0.05
        x1, y1 = cx + math.sin(a) * r * 0.97, cy - math.cos(a) * r * 0.97
        x2, y2 = cx + math.sin(a) * r * (0.97 - l), cy - math.cos(a) * r * (0.97 - l)
        d.add(f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" stroke="{GREEN}" stroke-width="{4 if i % 6 == 0 else 2}"/>')
    for i, ch in enumerate(letters):
        a = i * math.pi / 2
        d.text(cx + math.sin(a) * r * 0.66, cy - math.cos(a) * r * 0.66 + 20, ch, 56, CREAM, bold=True, anchor='middle')
    # the needle, a touch off north
    a = math.radians(14)
    tipx, tipy = cx + math.sin(a) * r * 0.62, cy - math.cos(a) * r * 0.62
    tlx, tly = cx - math.sin(a) * r * 0.62, cy + math.cos(a) * r * 0.62
    px, py = math.cos(a) * r * 0.08, math.sin(a) * r * 0.08
    d.add(f'<path d="M{tipx:.1f} {tipy:.1f} L{cx + px:.1f} {cy + py:.1f} L{cx - px:.1f} {cy - py:.1f} Z" fill="{ORANGE}"/>'
          f'<path d="M{tlx:.1f} {tly:.1f} L{cx + px:.1f} {cy + py:.1f} L{cx - px:.1f} {cy - py:.1f} Z" fill="{CREAM}"/>'
          f'<circle cx="{cx}" cy="{cy}" r="{r * 0.05}" fill="{CREAM}" stroke="{GREEN}" stroke-width="3"/>')


def pine_mark(d, x, y, s, fill):
    d.add(f'<g transform="translate({x},{y}) scale({s})" fill="{fill}"><path d="M0 -40 L18 -12 L8 -12 L24 12 L-24 12 '
          f'L-8 -12 L-18 -12 Z"/><rect x="-4" y="12" width="8" height="12"/><rect x="-34" y="26" width="68" height="4"/></g>')


def wear(seed):
    return (f'<filter id="wear{seed}" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" '
            f'baseFrequency="0.035" numOctaves="4" seed="{seed}" result="n"/><feColorMatrix in="n" type="matrix" '
            f'values="0 0 0 0 0.95  0 0 0 0 0.92  0 0 0 0 0.85  0 0 0 5 -3.55"/></filter>'
            f'<filter id="rub{seed}" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" '
            f'baseFrequency="0.02" numOctaves="3" seed="{seed + 5}" result="n"/><feColorMatrix in="n" type="matrix" '
            f'values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 3 -1.1" result="m"/><feComposite in="SourceGraphic" '
            f'in2="m" operator="in"/></filter>'
            f'<filter id="tooth{seed}" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" '
            f'baseFrequency="1.1" numOctaves="1" seed="{seed + 3}" result="n"/><feColorMatrix in="n" type="matrix" '
            f'values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 0.12 0"/></filter>')


def front(d, ox, lang):
    """The front cover at x = ox in d."""
    t = T[lang]
    band = 640
    d.add(f'<rect x="{ox}" y="0" width="{PW}" height="{PH}" fill="{GREEN}"/>')
    d.add(f'<clipPath id="lower{ox}"><rect x="{ox}" y="{band}" width="{PW}" height="{PH - band}"/></clipPath>')
    d.add(contours(ox + 640, 1260, 11, f'lower{ox}'))
    d.add(f'<rect x="{ox}" y="0" width="{PW}" height="{band}" fill="{ORANGE}"/>')
    d.add(f'<rect x="{ox}" y="{band}" width="{PW}" height="10" fill="#1B1B18"/>')
    if lang == 'zh_cn':
        d.text(ox + 90, 150, t['edition'], 40, '#1B1B18', bold=True, track=8)
        d.text(ox + 82, 420, '野外', 230, '#1B1B18', bold=True)
        d.text(ox + 82, 600, '求生指南', 150, '#1B1B18', bold=True, track=6)
        d.text(ox + 90, 740, t['sub'], 44, CREAM, track=6)
    else:
        d.text(ox + 90, 150, t['edition'], 36, '#1B1B18', bold=True, track=8)
        sz = 970 / width('SURVIVAL', 1, 0.01)
        d.text(ox + 84, 330, 'FIELD', sz, '#1B1B18', bold=True, track=sz * 0.01)
        d.text(ox + 84, 330 + sz * 0.86, 'SURVIVAL', sz, '#1B1B18', bold=True, track=sz * 0.01)
        d.text(ox + PW - 90, 612, 'GUIDE', 64, '#1B1B18', bold=True, anchor='end', track=24)
        sub = 'SHELTER · FIRE · WATER · FOOD · SIGNALS · FIRST AID'
        ss = min(34, 970 / width(sub, 1, 0.08))
        d.text(ox + 92, 740, sub, ss, CREAM, track=ss * 0.08)
    compass(d, ox + 575, 1140, 250, 'NESW')   # a drawing: its letters stay as printed
    d.text(ox + 90, PH - 210, t['bio'], 34 if lang == 'zh_cn' else 32, CREAM, op=0.85)
    d.text(ox + 90, PH - 130, t['author'], 64, CREAM, bold=True, track=6 if lang == 'en_us' else 2)
    pine_mark(d, ox + PW - 150, PH - 190, 1.4, CREAM)
    d.text(ox + PW - 150, PH - 100, t['press'], 26, CREAM, anchor='middle', track=3)


def back(d, ox):
    t = T['en_us']
    d.add(f'<rect x="{ox}" y="0" width="{PW}" height="{PH}" fill="{GREEN}"/>')
    d.add(f'<rect x="{ox}" y="0" width="{PW}" height="230" fill="{ORANGE}"/><rect x="{ox}" y="230" width="{PW}" height="10" fill="#1B1B18"/>')
    d.text(ox + 90, 150, 'FIELD SURVIVAL GUIDE', 70, '#1B1B18', bold=True, track=4)
    y = 360
    blurb = ('Lost, hurt, or caught out after dark: most people who die in the backcountry die in the first two '
             'days. This book is about those two days.')
    for line in wrap(blurb, 46, PW - 180):
        d.text(ox + 90, y, line, 46, CREAM)
        y += 70
    y += 30
    for s in ('Shelter from wind, rain and snow', 'Fire without matches', 'Safe water from creeks and ponds',
              'Snares, fishing and wild plants', 'Finding your way, getting found', 'Splints, burns and cold injury'):
        d.add(f'<rect x="{ox + 96}" y="{y - 26}" width="14" height="14" fill="{ORANGE}"/>')
        d.text(ox + 136, y, s, 38, CREAM)
        y += 64
    y += 40
    for line in wrap('Harlan Pruitt spent twenty-six years as a backcountry ranger and has walked out more lost '
                     'hunters than he can count.', 34, PW - 180):
        d.text(ox + 90, y, line, 34, CREAM, op=0.85)
        y += 52
    d.text(ox + 90, PH - 300, 'Printed on water-resistant stock', 30, CREAM, op=0.8)
    # price and a barcode box
    bx, by = ox + PW - 470, PH - 380
    d.add(f'<rect x="{bx}" y="{by}" width="380" height="260" fill="#F4EFE2"/>')
    rnd = random.Random(5)
    x = bx + 30
    while x < bx + 350:
        bw = rnd.choice((3, 3, 5, 7))
        d.add(f'<rect x="{x}" y="{by + 30}" width="{bw}" height="170" fill="#111"/>')
        x += bw + rnd.choice((3, 4, 6))
    d.text(bx + 190, by + 238, '$6.95', 32, '#111', anchor='middle')
    pine_mark(d, ox + 150, PH - 190, 1.4, CREAM)
    d.text(ox + 150, PH - 100, t['press'], 26, CREAM, anchor='middle', track=3)


def spine(d, ox, sw):
    # the type scales with the spine: the concept's 2 cm spine (200) or the model's 6 mm one (60)
    k = min(1.0, sw / 200 * 1.6)
    d.add(f'<rect x="{ox}" y="0" width="{sw}" height="{PH}" fill="{GREEN}"/>')
    d.add(f'<rect x="{ox}" y="0" width="{sw}" height="640" fill="{ORANGE}"/><rect x="{ox}" y="640" width="{sw}" height="10" fill="#1B1B18"/>')
    # spine text reads top to bottom
    g = Doc(PH, sw)
    g.text(60, sw / 2 + 26 * k, 'FIELD SURVIVAL GUIDE', 72 * k, '#1B1B18', bold=True, track=4 * k)
    g.text(1000, sw / 2 + 20 * k, 'PRUITT', 54 * k, CREAM, bold=True, track=6 * k)
    d.defs.extend(x for x in g.defs if x not in d.defs)
    d.add(f'<g transform="translate({ox + sw},0) rotate(90)">' + ''.join(g.body) + '</g>')
    pine_mark(d, ox + sw / 2, PH - 150, 1.1 * k, CREAM)


def cover_front(lang):
    d = Doc(PW, PH)
    front(d, 0, lang)
    d.add(f'<rect width="{PW}" height="{PH}" filter="url(#wear1)" opacity="0.45"/>')
    d.add(f'<rect x="0" y="0" width="{PW}" height="{PH}" fill="none" stroke="{CREAM}" stroke-width="22" filter="url(#rub1)" opacity="0.7"/>')
    d.add(f'<rect width="{PW}" height="{PH}" filter="url(#tooth1)"/>')
    return d


def cover_wrap(sw=200):
    d = Doc(PW * 2 + sw, PH)
    back(d, 0)
    spine(d, PW, sw)
    front(d, PW + sw, 'en_us')
    d.add(f'<rect width="{d.w}" height="{PH}" filter="url(#wear2)" opacity="0.45"/>')
    for x0, ww in ((0, PW), (PW, sw), (PW + sw, PW)):
        d.add(f'<rect x="{x0}" y="0" width="{ww}" height="{PH}" fill="none" stroke="{CREAM}" stroke-width="22" filter="url(#rub2)" opacity="0.7"/>')
    d.add(f'<rect width="{d.w}" height="{PH}" filter="url(#tooth2)"/>')
    return d


def edges():
    """The page block's edges: stacked sheets."""
    d = Doc(1024, 256)
    d.add(f'<rect width="1024" height="256" fill="#E8DFCB"/>')
    rnd = random.Random(9)
    for i in range(0, 256, 2):
        d.add(f'<rect x="0" y="{i}" width="1024" height="1" fill="#8C7F66" opacity="{rnd.uniform(0.08, 0.3):.2f}"/>')
    return d


def save(name, d, defs=''):
    with open(os.path.join(OUT, name + '.svg'), 'w', encoding='utf-8') as fh:
        fh.write(d.svg(defs))


if __name__ == '__main__':
    for lang in ('zh_cn', 'en_us'):
        d, y = page18(lang)
        save(f'page18_{lang}', d, paper_defs(3))
        print(lang, 'page 18 ends at', round(y), 'of', PH - 150)
        d, y = page19(lang)
        save(f'page19_{lang}', d, paper_defs(4))
        print(lang, 'page 19 ends at', round(y), 'of', PH - 150)
        d, y = toc(lang)
        save(f'toc_{lang}', d, paper_defs(5))
        save(f'cover_front_{lang}', cover_front(lang), wear(1))
    save('cover_wrap_en_us', cover_wrap(), wear(2))
    save('cover_wrap_model', cover_wrap(60), wear(2))   # the item model's 6 mm spine (fsg_game_assets.py)
    save('page_edges', edges())
    print('MISSING from afl_sans_sc:', ''.join(sorted(MISSING)) or 'none')
