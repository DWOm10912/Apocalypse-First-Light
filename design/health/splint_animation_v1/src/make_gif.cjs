// Builds the concept GIFs from the rendered frames: third person (side | front) and first person, with phase captions.
const {execFileSync} = require('child_process');
const fs = require('fs'), path = require('path');
const D = __dirname;
const OUT = process.argv[2];
const LIMB = process.argv[3] || 'leg';
const DIRS = LIMB === 'arm' ? {a: 'arm_side', b: 'arm_front', fp: 'arm_fp'} : {a: 'side', b: 'front', fp: 'fp'};
const FONT = 'C\\:/Windows/Fonts/msyh.ttc';
const PHASES = LIMB === 'arm' ? [
    [0.0, 0.75, 'A 断的前臂抬到胸前'],
    [0.75, 1.95, 'B 两根木条：一根贴手心一侧，一根贴手背一侧'],
    [1.95, 6.1, 'C 三道布条（手腕到肘）：抬到嘴边咬住一头，右手拉紧'],
    [6.1, 6.9, 'D 动动手指，看血运'],
    [6.9, 8.11, 'E 托一下前臂，放下'],
] : [
    [0.0, 1.1, 'A 坐下（和坐下键是同一个动作）'],
    [1.1, 2.45, 'B 放夹板：两根木条分到小腿内外两侧'],
    [2.45, 6.25, 'C 三道布条：从腿下穿过，结打在外侧，双手拉紧'],
    [6.25, 7.0, 'D 动动脚尖，看血运'],
    [7.0, 8.11, 'E 起身（单独的动作：再按坐下键）'],
];
const LABEL = LIMB === 'arm' ? {tp: '第三人称：左侧 | 右前方', fp: '第一人称（导演镜头低头，身体的手）'} : {tp: '第三人称：侧面 | 正面', fp: '第一人称（导演镜头低头，身体的手）'};
function captions(w, extra) {
    const esc = s => s.replace(/:/g, '\\:').replace(/'/g, "\\'");
    const parts = PHASES.map(([a, b, txt]) => `drawtext=fontfile='${FONT}':text='${esc(txt)}':x=(w-text_w)/2:y=h-42:fontsize=${w < 800 ? 19 : 22}:fontcolor=white:box=1:boxcolor=black@0.7:boxborderw=8:enable='between(t,${a},${b})'`);
    parts.push(`drawtext=fontfile='${FONT}':text='${esc(extra)}':x=12:y=10:fontsize=18:fontcolor=white:box=1:boxcolor=black@0.75:boxborderw=6`);
    parts.push(`drawtext=fontfile='${FONT}':text='%{eif\\:t\\:d}.%{eif\\:mod(t*10\\,10)\\:d} s':x=w-text_w-12:y=10:fontsize=18:fontcolor=white:box=1:boxcolor=black@0.75:boxborderw=6`);
    return parts.join(',');
}
function run(args) { execFileSync('ffmpeg', ['-y', '-loglevel', 'error', ...args], {stdio: 'inherit'}); }
fs.mkdirSync(OUT, {recursive: true});
// third person: side and front next to each other, 960 x 360
const tpFilter = `[0]scale=480:360[a];[1]scale=480:360[b];[a][b]hstack=2,${captions(960, LABEL.tp)},split[s0][s1];[s0]palettegen=max_colors=128[p];[s1][p]paletteuse=dither=bayer:bayer_scale=4`;
fs.writeFileSync(path.join(D, 'tp.filter'), tpFilter);
run(['-framerate', '10', '-i', path.join(D, DIRS.a + '/%03d.png'), '-framerate', '10', '-i', path.join(D, DIRS.b + '/%03d.png'), '-filter_complex_script', path.join(D, 'tp.filter'), '-loop', '0', path.join(OUT, `splint_${LIMB}_concept_tp.gif`)]);
const fpFilter = `[0]scale=640:480,${captions(640, LABEL.fp)},split[s0][s1];[s0]palettegen=max_colors=128[p];[s1][p]paletteuse=dither=bayer:bayer_scale=4`;
fs.writeFileSync(path.join(D, 'fp.filter'), fpFilter);
run(['-framerate', '10', '-i', path.join(D, DIRS.fp + '/%03d.png'), '-filter_complex_script', path.join(D, 'fp.filter'), '-loop', '0', path.join(OUT, `splint_${LIMB}_concept_fp.gif`)]);
// a contact sheet of key frames (third person side + front, first person)
const keys = LIMB === 'arm' ? [6, 10, 13, 17, 22, 26, 29, 64, 72, 80] : [5, 10, 17, 22, 27, 30, 33, 66, 73, 80];
const inputs = [], f = [];
keys.forEach((k, i) => {
    const n = String(k).padStart(3, '0') + '.png';
    inputs.push('-i', path.join(D, DIRS.a, n), '-i', path.join(D, DIRS.b, n), '-i', path.join(D, DIRS.fp, n));
    f.push(`[${i * 3}]scale=320:240[s${i}];[${i * 3 + 1}]scale=320:240[f${i}];[${i * 3 + 2}]scale=320:240[p${i}];[s${i}][f${i}][p${i}]vstack=3,drawtext=fontfile='${FONT}':text='${(k / 10).toFixed(1)} s':x=8:y=8:fontsize=20:fontcolor=white:box=1:boxcolor=black@0.5:boxborderw=5[c${i}]`);
});
f.push(keys.map((k, i) => `[c${i}]`).join('') + `hstack=${keys.length}`);
fs.writeFileSync(path.join(D, 'sheet.filter'), f.join(';'));
run([...inputs, '-filter_complex_script', path.join(D, 'sheet.filter'), path.join(OUT, `splint_${LIMB}_concept_keys.png`)]);
for (const n of fs.readdirSync(OUT)) console.log(n, fs.statSync(path.join(OUT, n)).size);
