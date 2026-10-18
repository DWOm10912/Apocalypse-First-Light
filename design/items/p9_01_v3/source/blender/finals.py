# P9 v3 concept: final shots into the D worktree design folder.
import os
exec(open(os.path.join(BASE, 'render.py')).read())
OUT = globals().get('OUTDIR') or 'D:/Minecraft Modding/AFL-survivor-v0/design/items/p9_01_v3/'
os.makedirs(OUT, exist_ok=True)
setup(key=3.5, rim=25.0, top=6.0, fill=0.8, world=0.03)
SHOTS = [
    ('p9_v3_01_hero', (-0.75, 0.55, 0.38), 620, (2400, 1350), (0, 10, 70), 0.2),
    ('p9_v3_02_left', (-1.0, 0.0, 0.06), 720, (2400, 1350), (0, 26, 62), 0.25),
    ('p9_v3_03_right', (1.0, 0.0, 0.06), 720, (2400, 1350), (0, 26, 62), 0.25),
    ('p9_v3_04_rear', (-0.7, -0.55, 0.5), 230, (1800, 1150), (0, -40, 100), 0.4),
    ('p9_v3_05_front', (0.75, 0.6, 0.3), 260, (1800, 1150), (0, 100, 98), 0.4),
    ('p9_v3_06_grip', (-0.85, 0.3, 0.08), 330, (1800, 1150), (0, -30, 55), 0.5),
    ('p9_v3_07_pov', (-0.12, -1.0, 0.32), 370, (1800, 1150), (0, 36, 104), 0.3),
]
only = globals().get('ONLY')
for name, d, dist, res, tgt, card in SHOTS:
    if only and name not in only:
        continue
    shot(OUT + name + '.png', d, dist, 85, res, target=tgt, samples=SAMPLES, card=card)
    print('wrote', name)
