// Health V1a medical sounds (docs/gameplay/survival_health_v1a.md 7.6): cuts the user's Firefly takes (2026-10-19) into the
// pieces health/MedicalItem plays on the actions' keyframes. Mono Vorbis (positional), each take's gain set once so the
// pieces keep their loudness against each other. Takes (E:/Download, copied under ASCII names into the folder given):
//   bandage.wav  <- 绷带.wav (one 5 s take of the whole wrap)
//   twist.wav    <- 拧一下瓶盖.wav (two turns and a click)
//   cap_pop.wav  <- 盖子离开  盖回瓶口.wav
//   pour.wav     <- 倾倒.wav
// The bottle picked up / put down and the liquid shifting as it tips stay silent (user 2026-10-19: three rounds, none usable).
// Usage, from the D worktree root: node tools/build-medical-sounds-v1a.cjs <folder with the takes>
const {execFileSync} = require('child_process');
const fs = require('fs'), path = require('path');
const src = process.argv[2];
if (!src) throw new Error('usage: node tools/build-medical-sounds-v1a.cjs <takes folder>');
const out = path.join(process.cwd(), 'src/main/resources/assets/apocalypse_firstlight/sounds/medical');
fs.mkdirSync(out, {recursive: true});
// take: gain dB for the whole take (bandage peaks +2.8 dBFS, pour -20 dBFS)
const GAIN = {bandage: -4, twist: -1, cap_pop: 0, pour: 12};
// name: [take, from s, to s, fade in s, fade out s, gain dB on top of the take's] (the cloth rustles sit ~28 dB under the pats)
const CUTS = {
    bandage_grab: ['bandage', 0.08, 0.70, 0.04, 0.10, 12],
    bandage_press: ['bandage', 1.58, 1.82, 0.005, 0.06, -4],
    bandage_wrap_1: ['bandage', 0.80, 1.58, 0.05, 0.10, 14],
    bandage_wrap_2: ['bandage', 1.82, 2.45, 0.05, 0.10, 10],
    bandage_wrap_3: ['bandage', 3.35, 3.98, 0.05, 0.10, 14],
    bandage_lay: ['bandage', 3.06, 3.32, 0.01, 0.06, 8],
    bandage_tear: ['bandage', 3.99, 4.18, 0.005, 0.05, 0],
    bandage_pat: ['bandage', 4.47, 4.64, 0.005, 0.05, 4],
    bandage_lower: ['bandage', 4.64, 4.97, 0.02, 0.12, 12],
    alcohol_twist_1: ['twist', 0.01, 0.27, 0.005, 0.06, 0],
    alcohol_twist_2: ['twist', 0.40, 0.71, 0.005, 0.06, 3],
    alcohol_thread_click: ['twist', 0.73, 0.92, 0.002, 0.08, -2],
    alcohol_cap_off: ['cap_pop', 0.02, 0.17, 0.003, 0.05, 0],
    alcohol_cap_on: ['cap_pop', 0.21, 0.37, 0.003, 0.05, 6],
    alcohol_pour: ['pour', 0.62, 1.32, 0.06, 0.15, 6],
};
for (const [name, [take, from, to, fin, fout, gain]] of Object.entries(CUTS)) {
    const len = to - from;
    const filter = `atrim=start=${from}:end=${to},asetpts=PTS-STARTPTS,volume=${GAIN[take] + gain}dB,` +
        `afade=t=in:d=${fin},afade=t=out:st=${(len - fout).toFixed(3)}:d=${fout},alimiter=limit=0.89:level=false`;
    execFileSync('ffmpeg', ['-v', 'error', '-y', '-i', path.join(src, take + '.wav'), '-af', filter, '-ac', '1', '-ar', '48000',
        '-c:a', 'libvorbis', '-q:a', '6', path.join(out, name + '.ogg')]);
    console.log(name, len.toFixed(2) + ' s');
}
