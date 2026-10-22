# Save the bake images (full resolution, lossless; PRE = br51_<atlas>_bake_) to BAKE_DIR and 1024 previews to PREVIEW_DIR (optional).
PRE = globals().get('PRE', 'p9_bake_')
import bpy, os

os.makedirs(BAKE_DIR, exist_ok=True)
for name in globals().get('SAVE_PASSES', ('color', 'rough', 'mat', 'nfull', 'ngeo', 'ao')):
    im = bpy.data.images.get(PRE + name)
    if not im:
        continue
    float_img = im.is_float
    im.filepath_raw = os.path.join(BAKE_DIR, PRE + name + ('.exr' if float_img else '.png'))
    im.file_format = 'OPEN_EXR' if float_img else 'PNG'
    im.save()
    pd = globals().get('PREVIEW_DIR')
    if pd:
        cp = im.copy()
        cp.scale(1024, 1024)
        cp.filepath_raw = os.path.join(pd, 'prev_' + name + '.png')
        cp.file_format = 'PNG'
        cp.save()
        bpy.data.images.remove(cp)
    print('saved', name)
