/* Run inside the existing Blockbench MCP JS context. Returns assets, never writes
 * or changes the accepted bbmodel. Save returned geo/animation to their runtime
 * paths; display belongs in models/item/service_pistol_in_hand.json (not the
 * V0.4.1 GUI routing model). Then run verify-service-pistol.ps1. */
(() => {
    if (Format.id !== 'geckolib_model' || !Project.save_path.endsWith('service_pistol_v03_8_fire_slide_cleanup.bbmodel'))
        throw new Error('Open the accepted V0.3.8 GeckoLib source first');
    const source = JSON.parse(require('fs').readFileSync(Project.save_path, 'utf8'));
    const stripUI = value => JSON.stringify(value, (key, val) =>
        ['selected', 'primary_selected', 'isOpen', '_static'].includes(key) ? undefined : val);
    // Use the codec's serialized precision, the same five-decimal geometry
    // representation stored on disk, rather than comparing raw editor floats.
    const live = JSON.parse(Codecs.project.compile());
    for (const key of ['elements', 'groups', 'animations', 'outliner', 'display', 'textures']) {
        if (stripUI(source[key]) !== stripUI(live[key])) throw new Error('Unsaved source difference: ' + key);
    }
    const nativeExports = [];
    const exportFunction = Blockbench.export;
    try {
        Blockbench.export = options => nativeExports.push(options.content);
        BarItems.export_geckolib_model.onClick();
        BarItems.export_geckolib_display.onClick();
    } finally {
        Blockbench.export = exportFunction;
    }
    if (nativeExports.length !== 2) throw new Error('Native exporters did not provide both assets');
    const geo = JSON.parse(nativeExports[0]);
    const display = JSON.parse(nativeExports[1]);
    const animation = Animator.buildFile(null, Project.animations.map(a => a.name));
    // All fields in this exporter output are the 1.12 cube/bone/UV subset.
    const allowedCube = ['origin', 'size', 'pivot', 'rotation', 'uv', 'inflate', 'mirror'];
    for (const bone of geo['minecraft:geometry'][0].bones) {
        for (const cube of bone.cubes || []) {
            if (Object.keys(cube).some(k => !allowedCube.includes(k))) throw new Error('Unsupported geometry field');
        }
    }
    geo.format_version = '1.12.0';
    for (const a of source.animations) {
        const output = animation.animations[a.name];
        if (!output || output.animation_length !== a.length) throw new Error('Animation mismatch');
        for (const bone of Object.values(a.animators)) {
            for (const channel of ['position', 'rotation', 'scale']) {
                const keys = (bone.keyframes || []).filter(k => k.channel === channel).sort((x, y) => x.time - y.time);
                if (!keys.length) continue;
                const values = Object.entries(output.bones[bone.name][channel])
                    .sort((x, y) => Number(x[0]) - Number(y[0])).map(pair => pair[1]);
                if (keys.length !== values.length) throw new Error('Exporter lost keys');
                const track = {};
                keys.forEach((key, i) => {
                    const expected = ['x', 'y', 'z'].map(axis => Number(key.data_points[0][axis]));
                    if (channel !== 'scale') expected[0] *= -1;
                    if (channel === 'rotation') expected[1] *= -1;
                    if (expected.some((v, axis) => Math.abs(v - values[i].vector[axis]) > 1e-7))
                        throw new Error('Exported vector differs from source');
                    if (!['linear', 'step'].includes(key.interpolation)) throw new Error('Unsupported interpolation');
                    if (i > 0 && keys[i - 1].interpolation === 'step') values[i].easing = 'afl_hold';
                    track[String(key.time)] = values[i];
                });
                output.bones[bone.name][channel] = track;
            }
        }
    }
    const bones = geo['minecraft:geometry'][0].bones;
    if (bones.some(b => b.name.includes('arm_reference')) || bones.reduce((n, b) => n + (b.cubes || []).length, 0) !== 77)
        throw new Error('Reference exclusion or geometry count differs');
    display.textures = {particle: 'apocalypse_firstlight:item/service_pistol'};
    return {geo, animation, display};
})()
