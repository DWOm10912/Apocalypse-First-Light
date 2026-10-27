"""Concept mock r2 of the search page's container sections as grids (user 2026-10-27: "容器分区改成网格，未搜出的东西显示暗色
轮廓，先把冰箱、储物柜、尸体三张图按网格重画给我看"). Each section is a grid like the player's pockets and backpack: 18 px
cells, 8 across (the right column's width), items take their item_size footprint (data/apocalypse_firstlight/item_size/
core_v1.json) and draw like GridDrawing (icon floor(shorter side) x 16, turned a quarter when rotated). Searching goes item
by item, sections top to bottom, items by their top-left cell; an item not searched yet is a dark block of its footprint.
Usage: python search_page_grid_mock.py background.png
Helpers (player side, sections, the container panel, the magnifier, food icons) are search_page_sections_mock.py's."""
import math, pathlib, sys

HERE = pathlib.Path(__file__).parent
src = (HERE / "search_page_sections_mock.py").read_text(encoding="utf-8")
exec(src[:src.index("# ------------------------------------------------------------------------------------------------ fridge")])
for n in ("p9_01_inventory", "bandage"):
    ICON[n] = "data:image/png;base64," + b64(ITEMS / f"{n}.png")

C = 18                      # the grids' cell, as in the player's grids
COLS = 8                    # 8 x 18 = 144 of the right column's 146


def turned_icon(name, x, y, size):
    el.append(f'<img src="{ICON[name]}" style="left:{x}px;top:{y}px;width:{size}px;height:{size}px;transform:rotate(90deg)">')


def placed(y0, col, row, w, h, name=None, state="open", count=None, turned=False):
    """An item w x h cells (as it lies, after any rotation) with its top-left at (col, row) of the grid starting at y0."""
    x, y, pw, ph = X0 + col * C, y0 + row * C, w * C, h * C
    if state == "hidden":
        box(x + .5, y + .5, pw - 1, ph - 1, 2, "rgb(7,8,9)", rgba(LINE, .34))
        return
    if state == "current":
        box(x + .5, y + .5, pw - 1, ph - 1, 2, "rgba(30,33,36,.80)", rgba(LINE, .34))
        round_magnifier_bare(x + (pw - C) / 2, y + (ph - C) / 2, C)
        box(x + 2, y + ph - 2.5, pw - 4, .6, .3, rgba(LINE, .15), "transparent")
        box(x + 2, y + ph - 2.5, (pw - 4) * .45, .6, .3, rgba(LINE, .6), "transparent")
        return
    entry(x, y, pw, ph)
    size = max(1, math.floor(min(w, h))) * 16
    if turned:
        turned_icon(name, x + (pw - size) / 2, y + (ph - size) / 2, size)
    else:
        icon(name, x + (pw - size) / 2, y + (ph - size) / 2, size)
    if count:
        rtext(str(count), x + pw - 1.2, y + ph - 6.2, 5.6, "#FFFFFF", "text-shadow:0.6px 0.6px 0 #3f3f3f;")


def round_magnifier_bare(x, y, s):
    """The magnifier alone (round_magnifier without its cell box and progress line)."""
    mark = len(el)
    round_magnifier(x, y, s, 0)
    del el[mark]                                   # its cell box
    del el[-2:]                                    # its progress line


def empty_grid(y, cols, rows, x0=None):
    x0 = X0 if x0 is None else x0
    for k in range(cols * rows):
        cell(x0 + (k % cols) * C, y + (k // cols) * C)
    return y + rows * C


def nothing_nearby(y):
    nearby(y, [])
    text("无", 320, y + 16, 6, DIS)


# ------------------------------------------------------------------------------------------------ fridge, powered, searching the door
def page_fridge():
    el.clear()
    player_side()
    nothing_nearby(container(fridge))
    return el[:]


def fridge():
    container_head("冰箱", "搜索中　9 / 15", 9.45, 15)
    y = section("冷藏室", 40, "4 °C")
    empty_grid(y, COLS, 3)
    for col, row, n, c in ((0, 0, "cooked_beef", 2), (1, 0, "carrot", 4), (2, 0, "egg", 6), (0, 1, "apple", 3),
                           (1, 1, "bread", None), (0, 2, "sweet_berries", 8)):
        placed(y, col, row, 1, 1, n, count=c)
    y = section("门架", y + 3 * C + 4, "只放瓶罐")
    empty_grid(y, COLS, 2)
    placed(y, 0, 0, 1, 2, "potion")
    placed(y, 1, 0, 1, 2, "potion")
    placed(y, 3, 0, 1, 1, "honey_bottle")
    placed(y, 4, 0, 1, 2, state="current")
    placed(y, 6, 1, 1, 1, state="hidden")
    y = section("冷冻室", y + 2 * C + 4, "−18 °C")
    empty_grid(y, COLS, 2)
    for col, row in ((0, 0), (1, 0), (2, 0), (0, 1)):
        placed(y, col, row, 1, 1, state="hidden")
    return y + 2 * C


# ------------------------------------------------------------------------------------------------ steel locker, searching the hooks
def page_locker():
    el.clear()
    player_side()
    nearby(container(locker), [("羊角锤", "claw_hammer", None)])
    return el[:]


def locker():
    container_head("储物柜", "搜索中　5 / 10", 5.45, 10)
    y = section("上层隔板", 40)
    empty_grid(y, COLS, 2)
    placed(y, 0, 0, 1, 1, "flip_lighter")
    placed(y, 1, 0, 1, 1, "battery_9v_alkaline", count=2)
    placed(y, 3, 0, 1, 2, "field_survival_guide")
    placed(y, 5, 0, 1, 1, "screw_box")
    y = section("挂钩", y + 2 * C + 4, "只挂衣物")
    empty_grid(y, COLS, 3)
    placed(y, 0, 0, 2, 3, "clothing_jacket")
    placed(y, 2, 0, 2, 2, state="current")
    y = section("柜底", y + 3 * C + 4)
    empty_grid(y, COLS, 2)
    placed(y, 0, 0, 2, 2, state="hidden")          # shoes
    placed(y, 2, 0, 3, 1, state="hidden")          # a crowbar lying down (1 x 3 turned)
    placed(y, 2, 1, 2, 1, state="hidden")          # a claw hammer lying down
    placed(y, 6, 0, 1, 1, state="hidden")          # a can of wood finish
    return y + 2 * C


# ------------------------------------------------------------------------------------------------ body: clothes seen at once, pockets searched, backpack being searched
def page_body():
    el.clear()
    player_side()
    nothing_nearby(container(body))
    return el[:]


def body():
    container_head("感染者尸体", "搜索中　3 / 9", 3.45, 9)
    y = section("穿着", 40, "一眼可见")
    for i, (name, n) in enumerate((("外套", "clothing_jacket"), ("打底衣", "clothing_shirt"), ("裤子", "clothing_jeans"),
                                   ("鞋", "clothing_shoes"), ("背包", "hiking_backpack"))):
        tall(X0 + i * 29.5, y + 6.2, 24, 24, name, n)
    y += 6.2 + 24
    y = section("上衣口袋", y + 4, line=False)
    text("裤袋", X0 + 44, y - 7, 5.2, DIM)
    empty_grid(y, 2, 1)
    placed(y, 0, 0, 1, 1, "folding_knife")
    empty_grid(y, 4, 1, X0 + 44)
    for i, n in enumerate(("pickup_t15_key", "medical_alcohol")):
        x = X0 + 44 + i * C
        entry(x, y, C, C)
        item(n, x, y, C, C)
    y = section("背包", y + C + 4, "徒步背包")
    empty_grid(y, COLS, 3)
    placed(y, 0, 0, 1, 2, state="current")         # field survival guide
    placed(y, 1, 0, 2, 1, state="hidden")          # splint
    placed(y, 3, 0, 1, 2, state="hidden")          # screwdriver
    placed(y, 4, 0, 1, 1, state="hidden")          # 9 V battery
    placed(y, 5, 0, 1, 1, state="hidden")          # bandage
    placed(y, 1, 1, 2, 1, state="hidden")          # P9-01
    return y + 3 * C


for name, page in (("fridge", page_fridge), ("locker", page_locker), ("body", page_body)):
    (HERE / f"search_grid_{name}.html").write_text(html(page()), encoding="utf-8")
print("ok")
