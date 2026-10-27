"""Concept mock of the AFL search page (the inventory page with the right column given to the container).
Writes two HTML files on the 480 x 270 canvas at 4x; headless Chrome turns them into PNGs."""
import base64, pathlib

HERE = pathlib.Path(__file__).parent
ITEMS = pathlib.Path(r"D:\Minecraft Modding\AFL-vanilla-rework\src\main\resources\assets\apocalypse_firstlight\textures\item")
FONT = pathlib.Path(r"D:\Minecraft Modding\AFL-vanilla-rework\src\main\resources\assets\apocalypse_firstlight\font\afl_sans_sc.ttf")


def b64(p):
    return base64.b64encode(pathlib.Path(p).read_bytes()).decode()


ICON = {n: "data:image/png;base64," + b64(ITEMS / f"{n}.png") for n in
        ("bandage", "flip_lighter", "folding_knife", "pickup_t15_key", "battery_9v_alkaline", "screwdriver", "splint",
         "crowbar", "canvas_backpack")}
BODY = "data:image/png;base64," + b64(HERE / "body_crop.png")
GUN = "data:image/png;base64," + b64(HERE / "gun_crop.png")
BG = "data:image/png;base64," + b64(HERE / "bg.png")

TEXT, DIM, DIS, ACCENT, WARN = "#E8E4DC", "#A8A49C", "#6C6A66", "#E0A84A", "#D98A72"
FREE = "127,194,122"
LINE = "232,228,220"
el = []


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


def keycap(key, x, y):
    w = max(10, len(key) * 4.2 + 6)
    box(x, y, w, 10, 2.5, "rgba(232,228,220,.92)", "transparent")
    el.append(f'<span style="left:{x}px;top:{y + 2}px;width:{w}px;text-align:center;font-size:6.4px;line-height:6.4px;'
              f'color:#16181A;font-weight:600">{key}</span>')
    return w


def caps(pairs):
    right = 470
    for key, word in pairs:
        ww = len(word) * 9
        rtext(word, right, 7.5, 9)
        w = max(10, len(key) * 4.2 + 6)
        keycap(key, right - ww - 4 - w, 6)
        right -= ww + 4 + w + 8


def magnifier(x, y):
    pat = [".RRR...", "RGGGR..", "RGGGR..", "RGGGR..", ".RRRH..", ".....H.", "......H"]
    for j, row in enumerate(pat):
        for i, c in enumerate(row):
            if c == ".":
                continue
            col = "rgba(232,228,220,.30)" if c == "G" else TEXT
            el.append(f'<div style="left:{x + i}px;top:{y + j}px;width:1px;height:1px;background:{col}"></div>')


# ------------------------------------------------------------------------------------------------ the player's side
def player_side(carrying=False):
    text("Dev", 10, 6.5, 9)
    cw = 2 * 7 + 10
    box(10 + 17 + 6, 4.5, cw, 12, 3, "rgba(16,16,16,.62)", rgba(LINE, .28))
    text("人物", 10 + 17 + 6 + 5, 7.3, 7)
    caps([("E", "关闭"), ("R", "旋转")] if carrying else [("E", "关闭"), ("4–9", "绑定")])

    panel(10, 20, 140, 242)
    el.append(f'<img src="{BODY}" style="left:48px;top:29.4px;width:64px;height:133.1px;'
              f'-webkit-mask-image:radial-gradient(ellipse 60% 56% at 50% 50%,#000 78%,transparent 100%)">')
    text("右", 71.5, 163.5, 5.2, DIM)
    text("左", 85.5, 163.5, 5.2, DIM)
    text("生命值 31 / 100", 60.5, 170.5, 6.2)
    left = [("头部", 1), ("面部", 0), ("耳部", 1), ("手表", 1), ("腿部", 1)]
    right = [("打底衣", 1), ("外套", 1), ("防弹衣", 1), ("手套", 0), ("脚部", 1)]
    for i in range(5):
        y = 30 + i * 31
        for (name, real), x in ((left[i], 15), (right[i], 121)):
            label(name, x, y)
            (slot if real else faint)(x, y, 24, 24)
    label("主武器", 15, 188)
    slot(15, 188, 63, 28)
    box(15, 188, 63, 28, 3, rgba("217,138,114", .12), rgba("217,138,114", .7))
    el.append(f'<img src="{GUN}" style="left:35.6px;top:196.4px;width:21.8px;height:11.3px;'
              f'-webkit-mask-image:radial-gradient(ellipse 55% 55% at 50% 50%,#000 70%,transparent 100%)">')
    text("1", 17, 189.5, 5, DIM)
    label("手上", 82, 188)
    faint(82, 188, 63, 28, .30, .16)
    text("拖到此处拿在手中", 82 + (63 - 8 * 4.8) / 2, 188 + 14 - 2.4, 4.8, DIS)
    label("手枪", 15, 227)
    slot(15, 227, 41, 24)
    box(15, 227, 41, 24, 3, rgba("217,138,114", .12), rgba("217,138,114", .7))
    text("2", 17, 228.5, 5, DIM)
    label("枪套", 60, 227)
    slot(60, 227, 24, 24)
    label("近战", 105, 227)
    slot(105, 227, 40, 24)
    text("3", 107, 228.5, 5, DIM)
    text("负重 4.6 / 30.0 kg", 15, 255.8, 6, DIM)
    rtext("舒适 ≥ 13 °C", 145, 255.8, 6, DIM)

    panel(156, 20, 150, 210)
    label("口袋", 161, 31)
    for i in range(4):
        cell(161 + i * 20, 31)
    entry(161, 31, 18, 18); item("flip_lighter", 161, 31, 18, 18)
    entry(181, 31, 18, 18); item("bandage", 181, 31, 18, 18, 2)
    label("胸挂", 161, 63)
    slot(161, 63, 28, 28)
    label("背包", 161, 105)
    slot(161, 105, 28, 28)
    item("canvas_backpack", 161, 105, 28, 28, size=24)
    for cy in range(6):
        for cx in range(5):
            cell(195 + cx * 18, 105 + cy * 18)
    entry(195, 105, 18, 18); item("battery_9v_alkaline", 195, 105, 18, 18, 4)
    entry(195, 123, 18, 36); icon("splint", 195 + 1, 123 + 10, 16)
    entry(213, 105, 18, 18); item("bandage", 213, 105, 18, 18, 3)
    entry(267, 105, 18, 18); item("pickup_t15_key", 267, 105, 18, 18)
    if carrying:
        box(231, 141, 18, 36, 2, rgba(FREE, .18), rgba(FREE, .75))

    panel(156, 234, 150, 28)
    text("绑定", 160, 244, 6, DIM)
    for i in range(6):
        x = 180 + i * 20.5
        faint(x, 237, 19, 22, .2, .12)
        text(str(4 + i), x + 1.5, 238.2, 4.6, DIS)


def nearby(y, rows):
    panel(312, y, 158, 262 - y)
    label("附近", 318, y + 11)
    for i, (name, item_name, count) in enumerate(rows):
        ry = y + 11 + i * 20
        faint(316, ry, 150, 19, .3, .12)
        item(item_name, 317, ry + .5, 18, 18)
        text(name, 317 + 18 + 4, ry + 6.6, 6)
        if count:
            rtext(f"×{count}", 316 + 150 - 4, ry + 7, 5.6, DIM)


def container_head(title, status, done, total):
    text(title, 318, 25, 7.5)
    rtext(status, 464, 26.5, 5.4, DIM)
    box(318, 35.5, 146, 1, .5, rgba(LINE, .12), "transparent")
    box(318, 35.5, 146 * done / total, 1, .5, rgba(LINE, .6), "transparent")


def take_all(y):
    w = 4 * 6 + 10
    box(464 - w, y, w, 11, 3, "rgba(16,16,16,.62)", rgba(LINE, .28))
    text("全部拿走", 464 - w + 5, y + 2.8, 6)


# ------------------------------------------------------------------------------------------------ A: corpse, searching
def page_a():
    el.clear()
    player_side()
    panel(312, 20, 158, 81)
    container_head("感染者尸体", "搜索中　3 / 9", 3.45, 9)
    x0, y0 = 318, 41
    for k in range(9):
        x, y = x0 + (k % 3) * 18, y0 + (k // 3) * 18
        if k < 3:
            cell(x, y)
        elif k == 3:
            box(x, y, 18, 18, 1.5, "rgba(30,33,36,.72)", rgba(LINE, .30))
            magnifier(x + 4.5, y + 3.5)
            box(x + 2, y + 16, 14, .6, .3, rgba(LINE, .15), "transparent")
            box(x + 2, y + 16, 6, .6, .3, rgba(LINE, .6), "transparent")
        else:
            box(x, y, 18, 18, 1.5, "rgba(8,9,10,.80)", rgba(LINE, .08))
    entry(x0, y0, 18, 18); item("folding_knife", x0, y0, 18, 18)
    entry(x0 + 36, y0, 18, 18); item("battery_9v_alkaline", x0 + 36, y0, 18, 18, 2)
    take_all(84)
    nearby(107, [("撬棍", "crowbar", None)])
    return el[:]


# ------------------------------------------------------------------------------------------------ B: 9 x 3 locker, searched, dragging
def page_b():
    el.clear()
    player_side(carrying=True)
    panel(312, 20, 158, 89)
    container_head("储物柜", "已搜完", 1, 1)
    x0, y0 = 319, 41
    for k in range(27):
        cell(x0 + (k % 9) * 16, y0 + (k // 9) * 16, 16)
    stock = {0: ("crowbar", None), 2: ("battery_9v_alkaline", 4), 3: ("bandage", 3), 9: ("splint", None),
             14: ("pickup_t15_key", None), 19: ("flip_lighter", None)}
    for k, (n, c) in stock.items():
        x, y = x0 + (k % 9) * 16, y0 + (k // 9) * 16
        entry(x, y, 16, 16)
        item(n, x, y, 16, 16, c)
    take_all(93)
    nearby(115, [])
    text("无", 320, 115 + 16, 6, DIS)
    # the screwdriver on the cursor, over the backpack's green footprint
    el.append(f'<img src="{ICON["screwdriver"]}" style="left:224px;top:143px;width:32px;height:32px;transform:rotate(-45deg);'
              f'filter:drop-shadow(0 1px 1px rgba(0,0,0,.5))">')
    return el[:]


def html(body):
    font = b64(FONT)
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>
@font-face{{font-family:afl;src:url(data:font/ttf;base64,{font});}}
html,body{{margin:0;width:1920px;height:1080px;overflow:hidden;background:#101010}}
#bg{{position:absolute;inset:-40px;background:url({BG}) center/cover;filter:blur(10px)}}
#dim{{position:absolute;inset:0;background:linear-gradient(rgba(16,16,16,.70),rgba(16,16,16,.80))}}
#c{{position:absolute;left:0;top:0;width:480px;height:270px;transform:scale(4);transform-origin:0 0;font-family:afl,sans-serif}}
#c div,#c span,#c img{{position:absolute;box-sizing:border-box;white-space:nowrap}}
#c img{{image-rendering:pixelated}}
</style></head><body><div id="bg"></div><div id="dim"></div><div id="c">{''.join(body)}</div></body></html>"""


(HERE / "search_ui_a.html").write_text(html(page_a()), encoding="utf-8")
(HERE / "search_ui_b.html").write_text(html(page_b()), encoding="utf-8")
print("ok")
