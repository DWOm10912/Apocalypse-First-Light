# Background entry: blender -b -P run_concept.py -- <finish> <out_dir> [samples] [shots...]
# Builds the materials and the gun into an empty scene, saves the .blend (black only), renders the shots.
import sys, os, bpy
BASE = os.path.dirname(os.path.abspath(__file__)) + '/'
argv = sys.argv[sys.argv.index('--') + 1:]
FINISH, OUTDIR = argv[0], argv[1].rstrip('/\\') + '/'
SAMPLES = int(argv[2]) if len(argv) > 2 else 256
ONLY = argv[3:] or None
for o in list(bpy.data.objects):
    bpy.data.objects.remove(o)
bpy.context.scene.unit_settings.scale_length = 0.001
exec(open(BASE + 'materials.py').read())
exec(open(BASE + os.environ.get('BR50_GUN', 'gun.py')).read())   # BR50_GUN=gun_r3.py: round 3
if FINISH == 'black':
    bpy.ops.wm.save_as_mainfile(filepath=BASE + os.environ.get('BR50_GUN', 'gun.py').replace('gun', 'br50_concept').replace('.py', '.blend'))
exec(open(BASE + 'finals.py').read())
