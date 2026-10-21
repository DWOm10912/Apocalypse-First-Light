// Fracture and fall sounds V1 (docs/gameplay/fall_landing_v1_plan.md 5): the user's Firefly takes (2026-10-20, E:/Download,
// copied under ASCII names into the folder given):
//   bone.wav         <- Firefly_audio_Close-up_foley_of_a_human_bone_snapping_inside_fle_variation2.wav (1 s)
//   fall_1..4.wav    <- Firefly_audio_Foley_of_a_person_falling_hard_onto_dirt_ground__h_variation1..4.wav (1 s each)
// The bone take is a run of cracks over 0.12-0.82 s, slow for a bone giving (user: "速度有点慢"): trimmed to the cracks and
// time-compressed by BONE_TEMPO with rubberband (pitch kept, transients crisp), so the cracks stay sharp. The four falls
// sound alike (user: "听起来都差不多") so all four are random variants of one event, each trimmed of its tail.
// Mono Vorbis (positional). With --preview <folder> it also writes the bone at the other tempos as wav for listening.
// Usage, from the D worktree root: node tools/build-fracture-sounds-v1.cjs <takes folder> [--preview <folder>]
const {execFileSync} = require('child_process');
const fs = require('fs'), path = require('path');
const src = process.argv[2];
if (!src) throw new Error('usage: node tools/build-fracture-sounds-v1.cjs <takes folder> [--preview <folder>]');
const pi = process.argv.indexOf('--preview'), preview = pi > 0 ? process.argv[pi + 1] : null;
const out = path.join(process.cwd(), 'src/main/resources/assets/apocalypse_firstlight/sounds/health');
fs.mkdirSync(out, {recursive: true});

const BONE_FROM = 0.11, BONE_TO = 0.90, BONE_TEMPO = 2.0;
// fall take: [to s (before the tail), gain dB]; each starts on its impact at 0.00-0.03 s
const FALLS = {fall_1: [0.80, -3], fall_2: [0.78, -4], fall_3: [0.85, -3], fall_4: [0.92, -4]};

function ffmpeg(input, filter, file, ogg) {
    const codec = ogg ? ['-c:a', 'libvorbis', '-q:a', '6'] : ['-c:a', 'pcm_s16le'];
    execFileSync('ffmpeg', ['-v', 'error', '-y', '-i', input, '-af', filter, '-ac', '1', '-ar', '48000', ...codec, file]);
}
function bone(tempo) {
    const len = (BONE_TO - BONE_FROM) / tempo;
    return `atrim=start=${BONE_FROM}:end=${BONE_TO},asetpts=PTS-STARTPTS,afade=t=in:d=0.004,` +
        `rubberband=tempo=${tempo}:transients=crisp:detector=percussive,volume=-2dB,` +
        `afade=t=out:st=${(len - 0.06).toFixed(3)}:d=0.06,alimiter=limit=0.89:level=false`;
}
ffmpeg(path.join(src, 'bone.wav'), bone(BONE_TEMPO), path.join(out, 'fracture_crack.ogg'), true);
console.log('fracture_crack', ((BONE_TO - BONE_FROM) / BONE_TEMPO).toFixed(2) + ' s');
for (const [name, [to, gain]] of Object.entries(FALLS)) {
    const filter = `atrim=start=0:end=${to},asetpts=PTS-STARTPTS,volume=${gain}dB,afade=t=in:d=0.003,` +
        `afade=t=out:st=${(to - 0.2).toFixed(3)}:d=0.2,alimiter=limit=0.89:level=false`;
    ffmpeg(path.join(src, name + '.wav'), filter, path.join(out, name + '.ogg'), true);
    console.log(name, to.toFixed(2) + ' s');
}
if (preview) {
    fs.mkdirSync(preview, {recursive: true});
    for (const t of [1.0, 1.6, 2.0, 2.5]) ffmpeg(path.join(src, 'bone.wav'), bone(t), path.join(preview, `fracture_crack_x${t.toFixed(1)}.wav`), false);
    console.log('preview', preview);
}
