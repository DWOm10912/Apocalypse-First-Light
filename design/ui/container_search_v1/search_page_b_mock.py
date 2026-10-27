"""Concept mock of search page B (user 2026-10-26: "搜索还是在当前容器里面搜索，但是给上背包（如果有）、胸挂、口袋的显示"):
only the player's grids and the container, no gear column, weapon positions or bindings. Same 480 x 270 canvas, colours
and sizes as search_page_mock.py (A). Writes two HTML files at 4x; headless Chrome turns them into PNGs.
Usage: python search_page_b_mock.py [background.png] (a blurred game frame; plain dark without it)."""
import base64, pathlib, sys

HERE = pathlib.Path(__file__).parent
ITEMS = pathlib.Path(r"D:\Minecraft Modding\AFL-vanilla-rework\src\main\resources\assets\apocalypse_firstlight\textures\item")
FONT = pathlib.Path(r"D:\Minecraft Modding\AFL-vanilla-rework\src\main\resources\assets\apocalypse_firstlight\font\afl_sans_sc.ttf")


def b64(p):
    return base64.b64encode(pathlib.Path(p).read_bytes()).decode()


ICON = {n: "data:image/png;base64," + b64(ITEMS / f"{n}.png") for n in
        ("bandage", "flip_lighter", "folding_knife", "pickup_t15_key", "battery_9v_alkaline", "screwdriver", "splint",
         "crowbar", "canvas_backpack")}
BG = "data:image/png;base64," + b64(sys.argv[1]) if len(sys.argv) > 1 else ""

TEXT, DIM, DIS = "#E8E4DC", "#A8A49C", "#6C6A66"
FREE = "127,194,122"
LINE = "232,228,220"
el = []

# B layout: the player's grids (left) and the container (right), centred on the canvas
P_X, P_W = 74, 150           # "身上": pockets, rig, backpack
C_X, C_W = 230, 176          # the container, then "附近" under it
PAD = 6


def rgba(rgb, a):
    return f"rgba({rgb},{a})"


def box(x, y, w, h, r, fill, line, shadow=False, extra=""):
    sh = "box-shadow:0 1px 3px rgba(0,0,0,.22);" if shadow else ""
    el.append(f'<div style="left:{x}px;top:{y}px;width:{w}px;height:{h}px;border-radius:{r}px;background:{fill};'
              f'border:0.35px solid {line};{sh}{extra}"></div>')


def panel(x, y, w, h):
    box(x, y, w, h, 4, "rgba(16,16,16,.62)", rgba(LINE, .28), True)


def slot(x, y, w, h):
    box(x, y, w, h, 3, "rgba(16,16,16,.45)", rgba(LINE, .22))


def faint(x, y, w, h, a=0.25, la=0.12):
    box(x, y, w, h, 3, f"rgba(16,16,16,{a})", rgba(LINE, la))


def cell(x, y, s=18):
    box(x, y, s, s, 1.5, "rgba(16,16,16,.45)", rgba(LINE, .14))


def entry(x, y, w, h):
    box(x + .5, y + .5, w - 1, h - 1, 2, "rgba(42,45,48,.88)", rgba(LINE, .30))


def text(s, x, y, size, color=TEXT, extra=""):
    el.append(f'<span style="left:{x}px;top:{y}px;font-size:{size}px;line-height:{size}px;color:{color};{extra}">{s}</span>')


def rtext(s, right, y, size, color=TEXT, extra=""):
    el.append(f'<span style="right:{480 - right}px;top:{y}px;font-size:{size}px;line-height:{size}px;color:{color};{extra}">{s}</span>')


def label(s, x, box_y):
    text(s, x, box_y - 6.2, 5.2, DIM)


def icon(name, x, y, size=16, rot=0):
    t = f"transform:rotate({rot}deg);" if rot else ""
    el.append(f'<img src="{ICON[name]}" style="left:{x}px;top:{y}px;width:{size}px;height:{size}px;{t}">')


def item(name, x, y, w, h, count=None, size=None):
    s = size or (16 if min(w, h) >= 18 else 14)
    icon(name, x + (w - s) / 2, y + (h - s) / 2, s)
    if count:
        rtext(str(count), x + w - 1.2, y + h - 6.2, 5.6, "#FFFFFF", "text-shadow:0.6px 0.6px 0 #3f3f3f;")


def caps(pairs):
    right = 470
    for key, word in pairs:
        ww = len(word) * 9
        rtext(word, right, 7.5, 9)
        w = max(10, len(key) * 4.2 + 6)
        box(right - ww - 4 - w, 6, w, 10, 2.5, "rgba(232,228,220,.92)", "transparent")
        el.append(f'<span style="left:{right - ww - 4 - w}px;top:8px;width:{w}px;text-align:center;font-size:6.4px;'
                  f'line-height:6.4px;color:#16181A;font-weight:600">{key}</span>')
        right -= ww + 4 + w + 8


def magnifier(x, y, c=18, turn=0.7):
    """SearchMagnifier: round lens, pale rim, glint, 45 degree handle; fractions of the cell as in the Java."""
    import math
    r, rim, orbit, off = c * .17, c * .06, c * .09, c * .07
    cx, cy = x + c / 2 - off + math.cos(turn) * orbit, y + c / 2 - off + math.sin(turn) * orbit
    hw, hl, outer = c * .085, c * .21, r + rim
    start = outer - rim * .5
    el.append(f'<div style="left:{cx + start}px;top:{cy - hw / 2}px;width:{hl + rim * .5}px;height:{hw}px;border-radius:{hw / 2}px;'
              f'background:#9AA0A6;transform-origin:{-start}px 50%;transform:rotate(45deg)"></div>')
    el.append(f'<div style="left:{cx - outer}px;top:{cy - outer}px;width:{outer * 2}px;height:{outer * 2}px;border-radius:50%;background:#D0D5DA"></div>')
    el.append(f'<div style="left:{cx - r}px;top:{cy - r}px;width:{r * 2}px;height:{r * 2}px;border-radius:50%;'
              f'background:linear-gradient(rgba(144,180,208,.30),rgba(144,180,208,.30)),#1E2124"></div>')
    g = r * .55
    el.append(f'<div style="left:{cx - r * .62}px;top:{cy - r * .62}px;width:{g}px;height:{g}px;border-radius:50%;background:rgba(242,244,246,.55)"></div>')


# ------------------------------------------------------------------------------------------------ the player's grids
def player_grids(carrying=False):
    caps([("E", "关闭"), ("R", "旋转")] if carrying else [("E", "关闭"), ("4–9", "绑定")])
    panel(P_X, 20, P_W, 242)
    text("身上", P_X + PAD, 25, 7.5)
    bx = P_X + 5
    label("口袋", bx, 46)
    for i in range(4):
        cell(bx + i * 20, 46)
    entry(bx, 46, 18, 18); item("flip_lighter", bx, 46, 18, 18)
    entry(bx + 20, 46, 18, 18); item("bandage", bx + 20, 46, 18, 18, 2)
    label("胸挂", bx, 78)
    faint(bx, 78, 28, 28, .30, .16)
    text("没穿胸挂", bx + 34, 78 + 11.5, 5.4, DIS)
    label("背包", bx, 120)
    slot(bx, 120, 28, 28)
    item("canvas_backpack", bx, 120, 28, 28, size=24)
    gx = bx + 34
    for cy in range(6):
        for cx in range(5):
            cell(gx + cx * 18, 120 + cy * 18)
    entry(gx, 120, 18, 18); item("battery_9v_alkaline", gx, 120, 18, 18, 4)
    entry(gx, 138, 18, 36); icon("splint", gx + 1, 138 + 10, 16)
    entry(gx + 18, 120, 18, 18); item("bandage", gx + 18, 120, 18, 18, 3)
    entry(gx + 72, 120, 18, 18); item("pickup_t15_key", gx + 72, 120, 18, 18)
    if carrying:
        box(gx + 36, 156, 18, 36, 2, rgba(FREE, .18), rgba(FREE, .75))
    text("负重 4.6 / 30.0 kg", P_X + PAD, 252.5, 6, DIM)


def container_head(title, status, done, total):
    x0, right = C_X + PAD, C_X + C_W - PAD
    text(title, x0, 25, 7.5)
    rtext(status, right, 26.5, 5.4, DIM)
    box(x0, 35.5, right - x0, 1, .5, rgba(LINE, .12), "transparent")
    box(x0, 35.5, (right - x0) * done / total, 1, .5, rgba(LINE, .6), "transparent")


def take_all(y):
    w = 4 * 6 + 10
    right = C_X + C_W - PAD
    box(right - w, y, w, 11, 3, "rgba(16,16,16,.62)", rgba(LINE, .28))
    text("全部拿走", right - w + 5, y + 2.8, 6)


def nearby(y, rows):
    panel(C_X, y, C_W, 262 - y)
    label("附近", C_X + PAD, y + 11)
    if not rows:
        text("无", C_X + 8, y + 16, 6, DIS)
    for i, (name, item_name, count) in enumerate(rows):
        ry = y + 11 + i * 20
        faint(C_X + 4, ry, C_W - 8, 19, .3, .12)
        item(item_name, C_X + 5, ry + .5, 18, 18)
        text(name, C_X + 5 + 18 + 4, ry + 6.6, 6)
        if count:
            rtext(f"×{count}", C_X + C_W - 8, ry + 7, 5.6, DIM)


# ------------------------------------------------------------------------------------------------ corpse, searching
def page_corpse():
    el.clear()
    player_grids()
    panel(C_X, 20, C_W, 81)
    container_head("感染者尸体", "搜索中　3 / 9", 3.45, 9)
    x0, y0 = C_X + PAD, 41
    for k in range(9):
        x, y = x0 + (k % 3) * 18, y0 + (k // 3) * 18
        if k < 3:
            cell(x, y)
        elif k == 3:
            box(x, y, 18, 18, 1.5, "rgba(30,33,36,.72)", rgba(LINE, .30))
            box(x + 2, y + 16, 14, .6, .3, rgba(LINE, .15), "transparent")
            box(x + 2, y + 16, 6, .6, .3, rgba(LINE, .6), "transparent")
            magnifier(x, y)
        else:
            box(x, y, 18, 18, 1.5, "rgba(8,9,10,.80)", rgba(LINE, .08))
    entry(x0, y0, 18, 18); item("folding_knife", x0, y0, 18, 18)
    entry(x0 + 36, y0, 18, 18); item("battery_9v_alkaline", x0 + 36, y0, 18, 18, 2)
    take_all(84)
    nearby(107, [("撬棍", "crowbar", None)])
    return el[:]


# ------------------------------------------------------------------------------------------------ 9 x 3 locker, searched, dragging
def page_locker():
    el.clear()
    player_grids(carrying=True)
    panel(C_X, 20, C_W, 96)
    container_head("储物柜", "已搜完", 1, 1)
    x0, y0 = C_X + (C_W - 162) / 2, 41
    for k in range(27):
        cell(x0 + (k % 9) * 18, y0 + (k // 9) * 18)
    stock = {0: ("crowbar", None), 2: ("battery_9v_alkaline", 4), 3: ("bandage", 3), 9: ("splint", None),
             14: ("pickup_t15_key", None), 19: ("flip_lighter", None)}
    for k, (n, c) in stock.items():
        x, y = x0 + (k % 9) * 18, y0 + (k // 9) * 18
        entry(x, y, 18, 18)
        item(n, x, y, 18, 18, c)
    take_all(99)
    nearby(122, [])
    gx = P_X + 5 + 34
    el.append(f'<img src="{ICON["screwdriver"]}" style="left:{gx + 29}px;top:158px;width:32px;height:32px;transform:rotate(-45deg);'
              f'filter:drop-shadow(0 1px 1px rgba(0,0,0,.5))">')
    return el[:]


def html(body):
    font = b64(FONT)
    bg = f"background:url({BG}) center/cover;filter:blur(10px)" if BG else "background:#202326"
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>
@font-face{{font-family:afl;src:url(data:font/ttf;base64,{font});}}
html,body{{margin:0;width:1920px;height:1080px;overflow:hidden;background:#101010}}
#bg{{position:absolute;inset:-40px;{bg}}}
#dim{{position:absolute;inset:0;background:linear-gradient(rgba(16,16,16,.70),rgba(16,16,16,.80))}}
#c{{position:absolute;left:0;top:0;width:480px;height:270px;transform:scale(4);transform-origin:0 0;font-family:afl,sans-serif}}
#c div,#c span,#c img{{position:absolute;box-sizing:border-box;white-space:nowrap}}
#c img{{image-rendering:pixelated}}
</style></head><body><div id="bg"></div><div id="dim"></div><div id="c">{''.join(body)}</div></body></html>"""


(HERE / "search_ui_b_corpse.html").write_text(html(page_corpse()), encoding="utf-8")
(HERE / "search_ui_b_locker.html").write_text(html(page_locker()), encoding="utf-8")
print("ok")
