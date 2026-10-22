# Field Survival Guide V1 concept sheets (2026-10-21): the text and panels of the three sheets, in the mod's font
# (fsg_type). Writes out/sheet_<name>_over.svg and out/sheets.json; fsg_sheets.cjs puts the renders and pages under them.
#   python design/items/field_survival_guide_v1/fsg_sheets.py && node design/items/field_survival_guide_v1/fsg_sheets.cjs
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fsg_type import Doc, wrap  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'out')
CREAM, GREY, ORANGE, DARK = '#EDE6D6', '#9C978C', '#E06A2E', '#1C1E1B'
HERO = 'D:/Minecraft Modding/Apocalypse First Light/wiki/public/img/hero_forest.webp'


def notes(d, x, y, w, items, size=21, lead=1.55, num=True):
    for i, s in enumerate(items):
        if num:
            d.add(f'<circle cx="{x + 13}" cy="{y - size * 0.36:.1f}" r="13" fill="{ORANGE}"/>')
            d.text(x + 13, y - size * 0.36 + 6, str(i + 1), 17, '#FFFFFF', bold=True, anchor='middle')
        for j, line in enumerate(wrap(s, size, w - 40)):
            d.text(x + 40, y, line, size, CREAM)
            y += size * lead
        y += size * 0.6
    return y


def panel(d, x, y, w, h, op=0.74, r=10):
    d.add(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="#121411" fill-opacity="{op}"/>')


def keycap(d, x, y, key, label):
    kw = max(38, 18 + len(key) * 13)
    d.add(f'<rect x="{x}" y="{y - 27}" width="{kw}" height="36" rx="6" fill="none" stroke="{CREAM}" stroke-width="1.6"/>')
    d.text(x + kw / 2, y - 2, key, 18, CREAM, bold=True, anchor='middle')
    w = d.text(x + kw + 10, y - 2, label, 20, CREAM)
    return x + kw + 10 + w + 34


def hints(d, cx, y, page):
    items = [('A', '上一页'), ('D', '下一页'), ('Tab', '目录'), ('Esc', '合上')]
    # measure by drawing into a scratch doc
    scratch = Doc(1, 1)
    x = 0
    for k, l in items:
        x = keycap(scratch, x, 0, k, l)
    pw = x - 34 + 60 + 230
    x0 = cx - pw / 2
    panel(d, x0, y - 46, pw, 62)
    d.text(x0 + 30, y - 6, page, 20, GREY)
    x = x0 + 30 + 200
    for k, l in items:
        x = keycap(d, x, y - 4, k, l)


def toast(d, x, y):
    panel(d, x, y, 350, 112, op=0.8)
    d.add(f'<rect x="{x}" y="{y}" width="5" height="112" rx="2" fill="{ORANGE}"/>')
    d.text(x + 26, y + 34, '已学会配方', 19, GREY)
    d.text(x + 26, y + 68, '弓钻取火器', 26, CREAM, bold=True)
    d.text(x + 26, y + 98, '可在工作台制作', 18, GREY)


def caption(d, x, y, s):
    d.text(x, y, s, 19, GREY)


sheets = {}

# ------------------------------------------------------------------------------------------------ 1. the book
d = Doc(2400, 1500)
d.text(80, 92, '野外求生指南  书本概念 V1', 44, CREAM, bold=True)
d.text(80, 132, 'FIELD SURVIVAL GUIDE · 2026-10-21 概念稿，未进游戏', 21, GREY)
caption(d, 80, 1066, '封面（中文界面打开时）')
caption(d, 660, 1066, '封面（英文；物品模型和世界里的书用这张）')
caption(d, 1260, 580, '合上：正面')
caption(d, 1800, 580, '合上：背面和书脊')
caption(d, 1260, 1360, '摊开：第 18–19 页（第三章 火 · 弓钻）')
notes(d, 80, 1130, 1140, [
    '口袋平装 11.5 × 18 厘米，48 页。概念图的书脊画了 2 厘米，偏厚；48 页实际约 5 毫米，做模型时改薄',
    '作者哈伦·普鲁伊特（Harlan Pruitt，虚构），“做了二十六年林区巡护员”；林线出版社（Timberline Press，候选）',
    '全书用模组自带的 AFL Sans SC，中英文同一套字体',
    '书页文字全部进语言文件；插图不带字，部件名由游戏按语言叠上去',
], size=20, lead=1.5)
open(os.path.join(OUT, 'sheet_book_over.svg'), 'w', encoding='utf-8').write(d.svg())
sheets['book'] = {'w': 2400, 'h': 1500, 'bg': DARK, 'out': 'concept_book.png', 'layers': [
    {'img': 'cover_front_zh_cn.png', 'x': 80, 'y': 160, 'w': 560, 'h': 876},
    {'img': 'cover_front_en_us.png', 'x': 660, 'y': 160, 'w': 560, 'h': 876},
    {'img': 'r_closed.png', 'x': 1260, 'y': 160, 'w': 520, 'h': 390},
    {'img': 'r_back.png', 'x': 1800, 'y': 160, 'w': 520, 'h': 390},
    {'img': 'r_open.png', 'x': 1260, 'y': 600, 'w': 1060, 'h': 729},
    {'svg': 'sheet_book_over.svg', 'x': 0, 'y': 0}]}

# ------------------------------------------------------------------------------------------------ 2. reading view A
d = Doc(1920, 1080)
# the spread: 2 x 550 x 860 at (410, 60); gutter shade and a hairline round it
d.add('<defs><linearGradient id="gut" x1="0" x2="1"><stop offset="0" stop-color="#000" stop-opacity="0"/>'
      '<stop offset="0.5" stop-color="#000" stop-opacity="0.22"/><stop offset="1" stop-color="#000" stop-opacity="0"/>'
      '</linearGradient></defs>')
d.add('<rect x="925" y="60" width="70" height="860" fill="url(#gut)"/>')
panel(d, 30, 60, 350, 330, op=0.66)
d.text(54, 104, '阅读界面 A  2D 跨页', 24, CREAM, bold=True)
notes(d, 54, 152, 310, [
    '跨页平铺在屏幕中间，字约 18 像素，和视野设置无关',
    '背后的世界模糊变暗；站着读，疾跑、受伤、开火就合上',
    '在有配方的那页停留约 3 秒算学会，右上角提示一次；书不消耗',
    '翻页、合上有纸声',
], size=18, lead=1.5)
toast(d, 1540, 60)
hints(d, 960, 1030, '第 18–19 页 / 48')
open(os.path.join(OUT, 'sheet_a_over.svg'), 'w', encoding='utf-8').write(d.svg())
sheets['a'] = {'w': 1920, 'h': 1080, 'out': 'concept_read_a.png', 'layers': [
    {'img': HERO, 'x': 0, 'y': 0, 'w': 1920, 'h': 1080, 'blur': 16, 'dim': 0.5},
    {'shadow': True, 'x': 410, 'y': 60, 'w': 1100, 'h': 860},
    {'img': 'page18_zh_cn.png', 'x': 410, 'y': 60, 'w': 550, 'h': 860},
    {'img': 'page19_zh_cn.png', 'x': 960, 'y': 60, 'w': 550, 'h': 860},
    {'svg': 'sheet_a_over.svg', 'x': 0, 'y': 0}]}

# ------------------------------------------------------------------------------------------------ 3. reading view B
d = Doc(1920, 1080)
panel(d, 30, 40, 400, 255, op=0.66)
d.text(54, 84, '阅读界面 B  第一人称捧书', 24, CREAM, bold=True)
notes(d, 54, 132, 360, [
    '双手把书举到面前，视野收窄到约 36°，书页在屏幕上和 A 版差不多大',
    '书页亮度跟着世界光照，夜里要有灯才能读',
    '其余规则和 A 版一样',
], size=18, lead=1.5)
toast(d, 1540, 40)
hints(d, 960, 1050, '第 18–19 页 / 48')
open(os.path.join(OUT, 'sheet_b_over.svg'), 'w', encoding='utf-8').write(d.svg())
sheets['b'] = {'w': 1920, 'h': 1080, 'out': 'concept_read_b.png', 'layers': [
    {'img': HERO, 'x': 0, 'y': 0, 'w': 1920, 'h': 1080, 'blur': 2.5, 'dim': 0.8},
    {'img': 'r_held_zh_cn.png', 'x': 0, 'y': 0, 'w': 1920, 'h': 1080},
    {'svg': 'sheet_b_over.svg', 'x': 0, 'y': 0}]}

json.dump(sheets, open(os.path.join(OUT, 'sheets.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('ok')
