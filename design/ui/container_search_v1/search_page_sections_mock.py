"""Concept mock of the search page's container sections (user 2026-10-26: "定下来：都用 A，先补装备格，再出冰箱、储物柜、尸体的分区概念图").
Page A (search_page_mock.py) with the right column's container split into named sections, one layout per model: a fridge
(cold / door shelves / freezer, each with its temperature), a steel locker (top shelf / hooks / floor), a body (worn
clothes / pockets / backpack). Writes three HTML files at 4x; headless Chrome turns them into PNGs.
Usage: python search_page_sections_mock.py background.png
The player's side and the drawing helpers are search_page_mock.py's (its code up to the A page is run here); food icons
are read from the 1.20.1 client jar in the Gradle cache (AFL has none yet)."""
import base64, math, pathlib, sys, zipfile

HERE = pathlib.Path(__file__).parent
src = (HERE / "search_page_mock.py").read_text(encoding="utf-8")
shared = src[:src.index("# ------------------------------------------------------------------------------------------------ A:")]
shared = shared.replace('BG = "data:image/png;base64," + b64(HERE / "bg.png")', 'BG = "data:image/png;base64," + b64(sys.argv[1])')
exec(shared)
exec(src[src.index("def html(body):"):src.index('(HERE / "search_ui_a.html")')])

JAR = pathlib.Path.home() / ".gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar"
with zipfile.ZipFile(JAR) as jar:
    for n in ("bread", "apple", "egg", "carrot", "cooked_beef", "beef", "cod", "cooked_chicken", "potion", "honey_bottle",
              "glass_bottle", "sweet_berries", "packed_ice"):
        folder = "block" if n == "packed_ice" else "item"
        ICON[n] = "data:image/png;base64," + base64.b64encode(jar.read(f"assets/minecraft/textures/{folder}/{n}.png")).decode()
for n in ("clothing_jacket", "clothing_shirt", "clothing_jeans", "clothing_shoes", "hiking_backpack", "field_survival_guide",
          "screw_box", "wood_finish_can", "claw_hammer", "medical_alcohol", "compass"):
    ICON[n] = "data:image/png;base64," + b64(ITEMS / f"{n}.png")

X0, RIGHT = 318, 464


def hidden(x, y, s):
    box(x, y, s, s, 1.5, "rgba(8,9,10,.80)", rgba(LINE, .08))


def round_magnifier(x, y, s, progress):
    """SearchMagnifier (the in-game one), frozen at 45 degrees of its orbit, plus the slot's progress line."""
    box(x, y, s, s, 1.5, "rgba(30,33,36,.72)", rgba(LINE, .30))
    r, rim, orbit, offset = s * .17, s * .06, s * .09, s * .07
    cx = x + s / 2 - offset + math.cos(math.pi / 4) * orbit
    cy = y + s / 2 - offset + math.sin(math.pi / 4) * orbit
    outer = r + rim
    hw, hl = s * .085, s * .21
    el.append(f'<div style="left:{cx + outer - rim / 2}px;top:{cy - hw / 2}px;width:{hl + rim / 2}px;height:{hw}px;'
              f'border-radius:{hw / 2}px;background:#9AA0A6;transform-origin:{-(outer - rim / 2)}px {hw / 2}px;transform:rotate(45deg)"></div>')
    box(cx - outer, cy - outer, outer * 2, outer * 2, outer, "#D0D5DA", "transparent")
    box(cx - r, cy - r, r * 2, r * 2, r, "#1E2124", "transparent")
    box(cx - r, cy - r, r * 2, r * 2, r, "rgba(144,180,208,.30)", "transparent")
    g = r * .55
    box(cx - r * .62, cy - r * .62, g, g, g / 2, "rgba(242,244,246,.55)", "transparent")
    box(x + 2, y + s - 2, s - 4, .6, .3, rgba(LINE, .15), "transparent")
    box(x + 2, y + s - 2, (s - 4) * progress, .6, .3, rgba(LINE, .6), "transparent")


def container(build):
    """The container panel sized to what build() drew (it returns its last y): "take all" under it, the panel behind it.
    Returns where "nearby" starts."""
    mark = len(el)
    y = build()
    take_all(y + 4)
    bottom = y + 4 + 11 + 6
    panel(312, 20, 158, bottom - 20)
    el.insert(mark, el.pop())
    return bottom + 6


def section(name, y, note=None, note_color=DIM, line=True):
    """A section's name on the left, an optional reading on the right (temperature, filter), a hairline between."""
    text(name, X0, y, 5.2, DIM)
    if not line:
        return y + 7
    end = RIGHT
    if note:
        rtext(note, RIGHT, y, 5.2, note_color)
        end = RIGHT - len(note) * 3.1 - 3
    start = X0 + len(name) * 5.2 + 3
    box(start, y + 2.6, end - start, .35, 0, rgba(LINE, .10), "transparent")
    return y + 7


def grid(y, cols, rows, s, stock, state=lambda k: "open"):
    """cols x rows cells of size s from X0; stock: {index: (icon, count)}; state(k): open / current / hidden."""
    for k in range(cols * rows):
        x, yy = X0 + (k % cols) * s, y + (k // cols) * s
        st = state(k)
        if st == "hidden":
            hidden(x, yy, s)
        elif st == "current":
            round_magnifier(x, yy, s, .45)
        else:
            cell(x, yy, s)
            if k in stock:
                n, c = stock[k]
                entry(x, yy, s, s)
                item(n, x, yy, s, s, c)
    return y + rows * s


def tall(x, y, w, h, name, stock=None):
    """A larger slot for one worn or hung piece (clothes), labelled like the player's gear slots."""
    label(name, x, y)
    slot(x, y, w, h)
    if stock:
        entry(x, y, w, h)
        item(stock, x, y, w, h, size=min(w, h) - 4)


# ------------------------------------------------------------------------------------------------ fridge, powered, searched
def page_fridge():
    el.clear()
    player_side()
    nearby_y = container(fridge)
    nearby(nearby_y, [])
    text("无", 320, nearby_y + 16, 6, DIS)
    return el[:]


def fridge():
    container_head("冰箱", "已搜完", 1, 1)
    y = section("冷藏室", 40, "4 °C")
    y = grid(y, 9, 3, 16, {0: ("cooked_beef", 2), 1: ("carrot", 4), 2: ("egg", 6), 9: ("apple", 3), 10: ("bread", None),
                           18: ("sweet_berries", 8)})
    y = section("门架", y + 4, "只放瓶罐")
    y = grid(y, 9, 1, 16, {0: ("potion", None), 1: ("potion", None), 2: ("honey_bottle", None), 4: ("glass_bottle", 2)})
    y = section("冷冻室", y + 4, "−18 °C")
    return grid(y, 9, 2, 16, {0: ("beef", 3), 1: ("cod", 2), 2: ("cooked_chicken", None), 9: ("packed_ice", 4)})


# ------------------------------------------------------------------------------------------------ steel locker, searching
def page_locker():
    el.clear()
    player_side()
    nearby(container(locker), [("羊角锤", "claw_hammer", None)])
    return el[:]


def locker():
    order = 9 + 4 + 18                                    # top shelf, hooks, floor: searched top to bottom
    container_head("储物柜", "搜索中　15 / 31", 15.45, order)
    y = section("上层隔板", 40)
    y = grid(y, 9, 1, 16, {0: ("flip_lighter", None), 1: ("battery_9v_alkaline", 2), 3: ("field_survival_guide", None),
                           6: ("screw_box", None)})
    y = section("挂钩", y + 4, "只挂衣物")
    for i, n in enumerate(("clothing_jacket", "clothing_shirt", None, None)):
        tall(X0 + i * 28, y + 6.2, 24, 30, "", n)
    y += 6.2 + 30
    y = section("柜底", y + 4)
    return grid(y, 9, 2, 16, {0: ("clothing_shoes", None), 1: ("crowbar", None)},
                lambda k: "open" if k < 2 else "current" if k == 2 else "hidden")


# ------------------------------------------------------------------------------------------------ body: clothes, pockets, backpack
def page_body():
    el.clear()
    player_side()
    nearby_y = container(body)
    nearby(nearby_y, [])
    text("无", 320, nearby_y + 16, 6, DIS)
    return el[:]


def body():
    container_head("感染者尸体", "搜索中　11 / 29", 11.45, 29)
    y = section("穿着", 40)
    for i, (name, n) in enumerate((("外套", "clothing_jacket"), ("打底衣", "clothing_shirt"), ("裤子", "clothing_jeans"),
                                   ("鞋", "clothing_shoes"), ("背包", "hiking_backpack"))):
        tall(X0 + i * 29.5, y + 6.2, 24, 24, name, n)
    y += 6.2 + 24
    y = section("上衣口袋", y + 4, line=False)
    grid(y, 2, 1, 18, {0: ("folding_knife", None)})
    text("裤袋", X0 + 44, y - 7, 5.2, DIM)
    for i in range(4):
        x = X0 + 44 + i * 18
        cell(x, y, 18)
    entry(X0 + 44, y, 18, 18); item("pickup_t15_key", X0 + 44, y, 18, 18)
    entry(X0 + 62, y, 18, 18); item("medical_alcohol", X0 + 62, y, 18, 18)
    y += 18
    y = section("背包", y + 4, "徒步背包")
    return grid(y, 9, 2, 16, {}, lambda k: "current" if k == 0 else "hidden")


for name, page in (("fridge", page_fridge), ("locker", page_locker), ("body", page_body)):
    (HERE / f"search_sections_{name}.html").write_text(html(page()), encoding="utf-8")
print("ok")
