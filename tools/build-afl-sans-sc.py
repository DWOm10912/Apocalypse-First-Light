"""Build AFL Sans SC, the game-wide UI font (docs/ui/afl_font_v1.md), from Noto Sans SC.

Input: the variable font NotoSansSC[wght].ttf (Google Fonts, ofl/notosanssc, SIL OFL 1.1). Not kept in the repository.
Output: one static weight, subset to the characters the game shows, renamed (the OFL Reserved Font Name 'Source' and
the upstream family name stay off the modified version).

Character set:
  - GB2312 (all 7445 code points: 6763 hanzi plus punctuation, kana, Greek, Cyrillic, pinyin)
  - ASCII, Latin-1, Latin Extended-A, General Punctuation, currency signs, CJK punctuation, full-width forms
  - every character of the given language files (AFL en_us / zh_cn, vanilla zh_cn)
  minus box drawing and block elements (U+2500..U+259F): those stay on Minecraft's own glyphs.

Usage:
  python -I tools/build-afl-sans-sc.py --src <NotoSansSC[wght].ttf> --weight 400 --out <file.ttf>
         [--lang <file.json> ...]

Needs fontTools (pip install fonttools).
"""
import argparse
import json
import os
import sys

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_LANGS = [
    os.path.join(REPO, "src/main/resources/assets/apocalypse_firstlight/lang/en_us.json"),
    os.path.join(REPO, "src/main/resources/assets/apocalypse_firstlight/lang/zh_cn.json"),
]
RANGES = [
    (0x0020, 0x007E),  # ASCII
    (0x00A0, 0x00FF),  # Latin-1
    (0x0100, 0x017F),  # Latin Extended-A
    (0x2000, 0x206F),  # General Punctuation
    (0x20A0, 0x20CF),  # currency
    (0x3000, 0x303F),  # CJK symbols and punctuation
    (0xFF00, 0xFFEF),  # half- and full-width forms
]
EXCLUDE = [(0x2500, 0x259F)]  # box drawing, block elements
WEIGHT_NAMES = {100: "Thin", 200: "ExtraLight", 300: "Light", 400: "Regular", 500: "Medium", 600: "SemiBold",
                700: "Bold", 800: "ExtraBold", 900: "Black"}


def gb2312():
    out = set()
    for hi in range(0xA1, 0xF8):
        for lo in range(0xA1, 0xFF):
            try:
                out.update(ord(c) for c in bytes([hi, lo]).decode("gb2312"))
            except UnicodeDecodeError:
                pass
    return out


def lang_chars(path):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    out = set()
    for key, value in data.items():
        out.update(ord(c) for c in key)
        out.update(ord(c) for c in str(value))
    return out


def rename(font, weight):
    style = WEIGHT_NAMES[weight]
    family = "AFL Sans SC" if weight == 400 else "AFL Sans SC " + style
    full = "AFL Sans SC " + style
    postscript = "AFLSansSC-" + style
    name = font["name"]
    keep = {0, 13, 14}
    name.names = [n for n in name.names if n.nameID in keep]
    for platform, enc, lang in ((3, 1, 0x409), (1, 0, 0)):
        name.setName(family, 1, platform, enc, lang)
        name.setName("Regular", 2, platform, enc, lang)
        name.setName("AFL;" + postscript, 3, platform, enc, lang)
        name.setName(full, 4, platform, enc, lang)
        name.setName("Version 1.000; subset of Noto Sans SC 2.004 (wght " + str(weight) + ")", 5, platform, enc, lang)
        name.setName(postscript, 6, platform, enc, lang)
        name.setName("Subset of Noto Sans SC (Google / Adobe) for Apocalypse First Light; modified under the SIL Open "
                     "Font License 1.1.", 10, platform, enc, lang)
    font["OS/2"].usWeightClass = weight
    font["OS/2"].fsSelection = (font["OS/2"].fsSelection & ~0b1100001) | 0b1000000  # REGULAR only
    font["head"].macStyle = 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--src", required=True)
    parser.add_argument("--weight", type=int, default=400)
    parser.add_argument("--out", required=True)
    parser.add_argument("--lang", action="append", default=[])
    args = parser.parse_args()
    if args.weight not in WEIGHT_NAMES:
        sys.exit("weight must be one of " + ", ".join(str(w) for w in WEIGHT_NAMES))

    wanted = gb2312()
    for lo, hi in RANGES:
        wanted.update(range(lo, hi + 1))
    for path in DEFAULT_LANGS + args.lang:
        wanted |= lang_chars(path)
    wanted = {c for c in wanted if not any(lo <= c <= hi for lo, hi in EXCLUDE)}
    wanted = {c for c in wanted if c >= 0x20 and not (0xE000 <= c <= 0xF8FF)}

    font = TTFont(args.src)
    font = instancer.instantiateVariableFont(font, {"wght": args.weight})
    cmap = font.getBestCmap()
    present = sorted(c for c in wanted if c in cmap)
    missing = sorted(c for c in wanted if c not in cmap)

    options = subset.Options()
    options.layout_features = []
    options.hinting = False
    options.notdef_outline = True
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    options.drop_tables += ["GSUB", "GPOS", "GDEF", "BASE", "STAT", "vhea", "vmtx", "HVAR", "MVAR", "DSIG", "meta"]
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=present)
    subsetter.subset(font)
    rename(font, args.weight)
    font.save(args.out)

    hanzi = sum(1 for c in present if 0x4E00 <= c <= 0x9FFF)
    print("wrote", args.out, os.path.getsize(args.out), "bytes;", len(present), "code points,", hanzi, "hanzi;",
          len(missing), "not in Noto Sans SC")
    if missing:
        print("missing:", " ".join("%04X" % c for c in missing[:60]), "..." if len(missing) > 60 else "")


if __name__ == "__main__":
    main()
