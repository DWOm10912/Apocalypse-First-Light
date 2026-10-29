# Blackridge .50 concept: final shots (FINISH in the file names).
import os
exec(open(os.path.join(BASE, 'render.py')).read())
OUT = globals().get('OUTDIR')
os.makedirs(OUT, exist_ok=True)
setup(key=3.5, rim=25.0, top=6.0, fill=0.8, world=0.03)
SHOTS = [
    ('01_hero', (-0.75, 0.55, 0.38), 980, (2400, 1350), (0, 138, 92), 0.2),
    ('02_left', (-1.0, 0.0, 0.06), 900, (2400, 1350), (0, 136, 82), 0.25),
    ('03_right', (1.0, 0.0, 0.06), 900, (2400, 1350), (0, 136, 82), 0.25),
    ('04_rear', (-0.7, -0.55, 0.5), 300, (1800, 1150), (0, 30, 128), 0.4),
    ('05_front', (0.75, 0.6, 0.3), 320, (1800, 1150), (0, 250, 130), 0.4),
    ('06_pov', (-0.12, -1.0, 0.32), 470, (1800, 1150), (0, 150, 150), 0.3),
]
for name, d, dist, res, tgt, card in SHOTS:
    if ONLY and name not in ONLY:
        continue
    shot(OUT + 'br50_%s_%s.png' % (FINISH, name), d, dist, 85, res, target=tgt, samples=SAMPLES, card=card)
    print('wrote', name)
