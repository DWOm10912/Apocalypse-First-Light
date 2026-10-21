// Builds the concept GIFs from the frames rendered in Blockbench (20 fps): third person (side | front) and first person,
// with phase captions, plus a key-frame contact sheet. node make_gif.cjs <frames dir> <out dir>
const {execFileSync} = require('child_process');
const fs = require('fs'), path = require('path');
const [FR, OUT] = process.argv.slice(2);
const FONT = 'C\\:/Windows/Fonts/msyh.ttc';
const CLIPS = {
    fall_over: {keys: [0, 4, 7, 9, 12, 20, 34, 41, 47, 56], phases: [
        [0.0, 0.12, 'A 落地，膝盖没撑住'],
        [0.12, 0.45, 'B 向前扑，双手先伸出去'],
        [0.45, 0.6, 'C 左手先着地：手臂骨折只在这一下判定，断的就是这只手'],
        [0.6, 1.6, 'D 跪趴在地上，缓一口气'],
        [1.6, 2.3, 'E 一只脚踩到前面，双手撑膝盖'],
        [2.3, 3.2, 'F 站起来'],
    ]},
    land_hard: {keys: [0, 2, 4, 6, 9, 12], phases: [
        [0.0, 0.12, 'A 双脚落地，屈膝屈髋缓冲'],
        [0.12, 0.9, 'B 站稳，起身（腿骨折只在落地这一下判定）'],
    ]},
};
function captions(w, phases, extra) {
    const esc = s => s.replace(/:/g, '\\:').replace(/'/g, "\\'");
    const parts = phases.map(([a, b, txt]) => `drawtext=fontfile='${FONT}':text='${esc(txt)}':x=(w-text_w)/2:y=h-42:fontsize=${w < 800 ? 18 : 22}:fontcolor=white:box=1:boxcolor=black@0.7:boxborderw=8:enable='between(t,${a},${b})'`);
    parts.push(`drawtext=fontfile='${FONT}':text='${esc(extra)}':x=12:y=10:fontsize=18:fontcolor=white:box=1:boxcolor=black@0.75:boxborderw=6`);
    parts.push(`drawtext=fontfile='${FONT}':text='%{eif\\:t\\:d}.%{eif\\:mod(t*10\\,10)\\:d} s':x=w-text_w-12:y=10:fontsize=18:fontcolor=white:box=1:boxcolor=black@0.75:boxborderw=6`);
    return parts.join(',');
}
function run(args) { execFileSync('ffmpeg', ['-y', '-loglevel', 'error', ...args], {stdio: 'inherit'}); }
fs.mkdirSync(OUT, {recursive: true});
const tmp = path.join(FR, '_filter.txt');
for (const [clip, C] of Object.entries(CLIPS)) {
    const d = s => path.join(FR, clip + '_' + s, '%03d.png');
    fs.writeFileSync(tmp, `[0]scale=480:360[a];[1]scale=480:360[b];[a][b]hstack=2,${captions(960, C.phases, '第三人称：侧面 | 左前方')},split[s0][s1];[s0]palettegen=max_colors=128[p];[s1][p]paletteuse=dither=bayer:bayer_scale=4`);
    run(['-framerate', '20', '-i', d('side'), '-framerate', '20', '-i', d('front'), '-filter_complex_script', tmp, '-loop', '0', path.join(OUT, `${clip}_concept_tp.gif`)]);
    fs.writeFileSync(tmp, `[0]scale=640:480,${captions(640, C.phases, '第一人称（镜头在摆好姿势的眼睛上）')},split[s0][s1];[s0]palettegen=max_colors=128[p];[s1][p]paletteuse=dither=bayer:bayer_scale=4`);
    run(['-framerate', '20', '-i', d('fp'), '-filter_complex_script', tmp, '-loop', '0', path.join(OUT, `${clip}_concept_fp.gif`)]);
    // contact sheet: three rows (side, front, first person), one column per key frame, time stamped
    const inputs = [], rows = [];
    for (const s of ['side', 'front', 'fp']) for (const k of C.keys) inputs.push('-i', path.join(FR, clip + '_' + s, String(k).padStart(3, '0') + '.png'));
    const n = C.keys.length;
    let f = '';
    ['side', 'front', 'fp'].forEach((s, r) => {
        for (let i = 0; i < n; i++) f += `[${r * n + i}]scale=320:240,drawtext=fontfile='${FONT}':text='${(C.keys[i] / 20).toFixed(2)} s':x=6:y=6:fontsize=16:fontcolor=white:box=1:boxcolor=black@0.7:boxborderw=4[c${r}_${i}];`;
        f += C.keys.map((_, i) => `[c${r}_${i}]`).join('') + `hstack=${n}[r${r}];`;
        rows.push(`[r${r}]`);
    });
    f += rows.join('') + 'vstack=3';
    fs.writeFileSync(tmp, f);
    run([...inputs, '-filter_complex_script', tmp, path.join(OUT, `${clip}_concept_keys.png`)]);
}
fs.unlinkSync(tmp);
console.log('written', OUT);
