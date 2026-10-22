# Field Survival Guide V1 game assets (2026-10-22): the pieces the reader and the item model use, from the concept's own
# drawing code (fsg_pages.py). Writes out/game/*.svg; fsg_game_assets.cjs rasterises them into the mod.
#   python design/items/field_survival_guide_v1/fsg_game_assets.py && node design/items/field_survival_guide_v1/fsg_game_assets.cjs
# The page text is not here: the game sets it from the language file (docs/gameplay/field_survival_guide_v1_design.md).
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fsg_pages import Doc, PW, PH, paper, paper_defs, bow_drill, cover_front, cover_wrap, edges, wear  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'out', 'game')
os.makedirs(OUT, exist_ok=True)


def save(name, d, defs=''):
    with open(os.path.join(OUT, name + '.svg'), 'w', encoding='utf-8') as fh:
        fh.write(d.svg(defs))


# a blank page: paper tone, blotches and grain, no gutter
d = Doc(PW, PH)
paper(d, False, gutter=False)
save('paper', d, paper_defs(3))
# the bow drill line drawing, numbered callouts, no names (920 x 560 design units at 1 : 1)
d = Doc(920, 560)
bow_drill(d, 0, 0, 920, 560, None, None)
save('fig_bow_drill', d)
# the cover as the reader shows it, per language
for lang in ('zh_cn', 'en_us'):
    save('cover_' + lang, cover_front(lang), wear(1))
# the item model: the English wrap (back, 6 mm spine, front) and the page edges
save('model_cover', cover_wrap(60), wear(2))
save('model_edges', edges())
print('ok')
