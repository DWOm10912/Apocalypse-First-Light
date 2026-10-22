# BR-51 concept: final shots into design/items/br51_m14/sheets/. The rifle carries the 20-round magazine; the last shot
# lines up the three magazines on their own (rifle hidden). Globals: ONLY (list of shot names), SAMPLES.
import os
exec(open(os.path.join(BASE, 'render.py')).read())
OUT = globals().get('OUTDIR') or os.path.normpath(os.path.join(BASE, '../../sheets')).replace('\\', '/') + '/'
os.makedirs(OUT, exist_ok=True)
setup(key=3.5, rim=25.0, top=6.0, fill=0.8, world=0.03)
MAGS = {n: bpy.data.objects[n] for n in ('MAG20', 'MAG30', 'MAG50')}


def show(rifle, mags):
    for o in bpy.data.collections['BR51'].objects:
        top = o.parent
        while top and top.parent:
            top = top.parent
        if top in MAGS.values() or o in MAGS.values():
            on = (top or o).name in mags
        else:
            on = rifle
        o.hide_render = not on


SHOTS = [
    ('br51_01_hero', (0.8, 0.45, 0.3), 2750, (2400, 1350), (0, 620, -50), 0.2),
    ('br51_02_right', (1.0, 0.0, 0.05), 3000, (2400, 1000), (0, 563, -62), 0.25),
    ('br51_03_left', (-1.0, 0.0, 0.05), 3000, (2400, 1000), (0, 563, -62), 0.25),
    ('br51_04_receiver_r', (0.85, -0.25, 0.45), 950, (1800, 1150), (0, 425, -30), 0.4),
    ('br51_05_receiver_l', (-0.85, -0.2, 0.4), 950, (1800, 1150), (0, 425, -30), 0.4),
    ('br51_06_gas_muzzle', (0.8, 0.35, 0.3), 1050, (1800, 1150), (0, 985, -12), 0.4),
    ('br51_07_pov', (0.09, -1.0, 0.17), 1250, (1800, 1150), (0, 760, 4), 0.3),
]
only = globals().get('ONLY')
for name, d, dist, res, tgt, card in SHOTS:
    if only and name not in only:
        continue
    show(True, ('MAG20',))
    shot(OUT + name + '.png', d, dist, 85, res, target=tgt, samples=SAMPLES, card=card)
    print('wrote', name)

if not only or 'br51_08_mags' in only:
    offs = {'MAG20': (0, -150, 0), 'MAG30': (0, 0, 0), 'MAG50': (0, 175, 0)}
    for n, e in MAGS.items():
        e.location = offs[n]
    show(False, tuple(MAGS))
    shot(OUT + 'br51_08_mags.png', (0.9, -0.3, 0.42), 1500, 85, (2000, 1250), (0, 490, -100), samples=SAMPLES, card=0.4)
    for e in MAGS.values():
        e.location = (0, 0, 0)
    print('wrote br51_08_mags')
if not only or 'br51_09_drum_l' in only:
    # drum, left face: the round-count window with the case heads behind it
    show(False, ('MAG50',))
    shot(OUT + 'br51_09_drum_l.png', (-0.9, -0.35, 0.02), 820, 85, (1800, 1300), (0, 482, -128), samples=SAMPLES, card=0.4)
    print('wrote br51_09_drum_l')
show(True, ('MAG20',))
