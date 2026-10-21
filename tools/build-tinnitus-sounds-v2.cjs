// Tinnitus V2 episode sounds (docs/gameplay/tinnitus_v2_plan.md 4): the user's four Firefly takes (2026-10-21, E:/Download,
// 10 s each, 48 kHz stereo), copied under ASCII names into the folder given:
//   a_steady.wav <- 稳定高音.wav   a ~1.5 kHz tone, own fade in, silent after 7.8 s
//   b_waver.wav  <- 起伏高音.wav   a 650-800 Hz tone pulsing in strength, loud
//   c_thin.wav   <- 更高更细.wav   a faint ~2.9 kHz whine, no fade in, only hiss after 7 s
//   d_low.wav    <- 偏低带沙.wav   a 200-300 Hz hum with a hiss, loud
// Each is cut to where it sounds, given a fade in and out, mixed to mono (the client sets it beside one ear, so it must be
// positional) and brought to the same body loudness (RMS between the fades, BODY_DB), limited to -1 dBFS; the game sets the
// volume per stage. Output sounds/hearing/tinnitus_1..2.ogg (Vorbis).
// Usage, from the D worktree root: node tools/build-tinnitus-sounds-v2.cjs <takes folder>
const {execFileSync} = require('child_process');
const fs = require('fs'), path = require('path');
const src = process.argv[2];
if (!src) throw new Error('usage: node tools/build-tinnitus-sounds-v2.cjs <takes folder>');
const out = path.join(process.cwd(), 'src/main/resources/assets/apocalypse_firstlight/sounds/hearing');
fs.mkdirSync(out, {recursive: true});

const BODY_DB = -20;
// take: [from s, to s, fade in s, fade out s]
const TAKES = {
    // a_steady and d_low dropped (user 2026-10-21: "有点奇怪"); only the wavering and the thin take
    tinnitus_1: ['b_waver.wav', 0.2, 9.6, 0.6, 1.0],
    tinnitus_2: ['c_thin.wav', 0.0, 7.0, 1.0, 1.2],
};

function pcm(file, from, to) {
    const raw = execFileSync('ffmpeg', ['-v', 'error', '-i', file, '-af', `atrim=start=${from}:end=${to}`, '-ac', '1', '-ar', '48000',
        '-f', 'f32le', '-'], {maxBuffer: 1 << 28});
    return new Float32Array(raw.buffer, raw.byteOffset, raw.length / 4);
}
for (const [name, [file, from, to, fin, fout]] of Object.entries(TAKES)) {
    const input = path.join(src, file);
    // body loudness: between the fades
    const body = pcm(input, from + fin, to - fout);
    let s = 0;
    for (const v of body) s += v * v;
    const rms = 20 * Math.log10(Math.sqrt(s / body.length));
    const gain = BODY_DB - rms, len = to - from;
    const filter = `atrim=start=${from}:end=${to},asetpts=PTS-STARTPTS,volume=${gain.toFixed(2)}dB,afade=t=in:d=${fin},` +
        `afade=t=out:st=${(len - fout).toFixed(3)}:d=${fout},alimiter=limit=0.89:level=false`;
    execFileSync('ffmpeg', ['-v', 'error', '-y', '-i', input, '-af', filter, '-ac', '1', '-ar', '48000', '-c:a', 'libvorbis', '-q:a', '6',
        path.join(out, name + '.ogg')]);
    console.log(name, file, len.toFixed(1) + ' s', 'body ' + rms.toFixed(1) + ' dBFS, gain ' + gain.toFixed(1) + ' dB');
}
