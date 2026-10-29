# White-tailed deer D0 concept sheets: composes the Blender renders in r1/ into three labelled review images.
#   python deer_compose.py
import json, os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
R = os.path.join(HERE, "r1")
FONT = "C:/Windows/Fonts/msyh.ttc"
BG = (52, 54, 58)
INK = (232, 230, 225)
DIM = (170, 170, 165)


def font(n, bold=False):
    return ImageFont.truetype("C:/Windows/Fonts/msyhbd.ttc" if bold else FONT, n)


def im(name):
    return Image.open(os.path.join(R, name)).convert("RGB")


def title(d, xy, text, sub=None):
    d.text(xy, text, font=font(40, True), fill=INK)
    if sub:
        d.text((xy[0], xy[1] + 56), sub, font=font(24), fill=DIM)


def grid(rows, cell, header, gap=16, label_h=44, top=140):
    """rows: list of (row label, [(image name, caption)])."""
    cols = max(len(r[1]) for r in rows)
    W = gap + cols * (cell + gap)
    H = top + len(rows) * (cell + label_h + gap) + gap
    out = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(out)
    title(d, (gap + 8, 24), *header)
    y = top
    for label, cells in rows:
        for i, (name, cap) in enumerate(cells):
            x = gap + i * (cell + gap)
            out.paste(im(name).resize((cell, cell), Image.LANCZOS), (x, y))
            d.text((x + 4, y + cell + 6), f"{label}  ·  {cap}" if i == 0 else cap, font=font(24), fill=INK)
        y += cell + label_h + gap
    return out


def sheet_views():
    views = [("side", "左侧"), ("front34", "左前 3/4"), ("front", "正面"), ("back34", "右后 3/4")]
    rows = [("雄鹿", [(f"buck_{v}.png", c) for v, c in views]), ("雌鹿", [(f"doe_{v}.png", c) for v, c in views])]
    out = grid(rows, 600, ("白尾鹿 D0 概念图 r1：雄鹿和雌鹿",
                           "秋季冬毛（灰褐），雄鹿 8 叉硬角；方块身体 + 连续关节环 + 128 手绘贴图（每模型像素 2×2）；雌鹿按雄鹿 0.92 倍，无角、脖子细"))
    out.save(os.path.join(HERE, "deer_d0_r1_views.png"))


def sheet_scale():
    meta = json.load(open(os.path.join(R, "lineup.json")))
    a, b = im("lineup.png"), im("lineup_sil.png")
    W, Hh = a.size
    top, gap = 140, 24
    out = Image.new("RGB", (W + 2 * gap, top + 2 * Hh + 3 * gap + 40), BG)
    d = ImageDraw.Draw(out)
    title(d, (gap + 8, 24), "和玩家比大小（侧面，正交，同一比例）",
          "玩家 1.88 m（模型 32 px），雄鹿肩高 85 cm（14.5 px），雌鹿肩高 78 cm；1 px = 5.86 cm")
    for k, img in enumerate((a, b)):
        oy = top + k * (Hh + gap)
        out.paste(img, (gap, oy))
        pxm = Hh / meta["height"]
        row = lambda z: oy + Hh / 2 - (z - meta["cz"]) * pxm
        col = lambda y: gap + W / 2 + (y - meta["cy"]) * pxm
        for who, h, label, color in (("buck", meta["heights"]["buck"], "雄鹿肩高 0.85 m", (232, 178, 92)),
                                     ("doe", meta["heights"]["doe"], "雌鹿肩高 0.78 m", (232, 178, 92)),
                                     ("player", meta["heights"]["player"], "玩家 1.88 m", (120, 190, 232))):
            yy = row(h)
            cx = col(meta["y"][who])
            x0, x1 = cx - 0.75 * pxm, cx + 0.95 * pxm
            for x in range(int(x0), int(x1), 14):
                d.line([(x, yy), (min(x + 8, x1), yy)], fill=color, width=3)
            if k == 0:
                if x1 + 260 > gap + W:                     # no room on the right: above the line's end
                    d.text((x1 - 200, yy - 40), label, font=font(24), fill=color)
                else:
                    d.text((x1 + 8, yy - 18), label, font=font(24), fill=color)
        g = row(0)
        d.line([(gap, g), (gap + W, g)], fill=(150, 150, 145) if k == 0 else (90, 90, 90), width=2)
    d.text((gap + 8, top + 2 * Hh + 2 * gap + 4), "下：剪影（雄鹿靠角、雌鹿靠大耳朵和细长脖子认出来）", font=font(24), fill=DIM)
    out.save(os.path.join(HERE, "deer_d0_r1_scale.png"))


def sheet_poses():
    rows = [("姿态", [("pose_graze.png", "吃草（雌鹿）：低头 5–20 秒，鼻子贴地"),
                      ("pose_alert.png", "警戒（雄鹿）：脖子竖直、耳朵向前、尾巴半抬、前蹄跺地"),
                      ("pose_flee.png", "逃跑（雄鹿）：跳跃式奔跑，扬尾露出白色下面")]),
            ("头部", [("buck_head.png", "雄鹿：黑鼻、鼻后白环、眼圈浅色、额头深、喉部白斑"),
                      ("doe_head.png", "雌鹿：额头不深，耳朵显得更大")])]
    cell, gap, top, lh = 720, 16, 140, 44
    W = gap + 3 * (cell + gap)
    H = top + 2 * (cell + lh + gap) + gap
    out = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(out)
    title(d, (gap + 8, 24), "三个关键姿态和头部特写", "骨架 25 根（骨盆、腰、胸、颈 2 节、头、双耳、尾，四条腿各 4 节）；姿态直接在骨架上摆，没有改模型")
    y = top
    for label, cells in rows:
        for i, (name, cap) in enumerate(cells):
            x = gap + i * (cell + gap)
            img = im(name)
            s = cell / max(img.size)
            img = img.resize((round(img.width * s), round(img.height * s)), Image.LANCZOS)
            tile = Image.new("RGB", (cell, cell), BG)
            tile.paste(img, ((cell - img.width) // 2, (cell - img.height) // 2))
            out.paste(tile, (x, y))
            d.text((x + 4, y + cell + 6), cap, font=font(22), fill=INK)
        y += cell + lh + gap
    out.save(os.path.join(HERE, "deer_d0_r1_poses.png"))


if __name__ == "__main__":
    sheet_views()
    sheet_scale()
    sheet_poses()
    print("sheets written")
