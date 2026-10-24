# Furniture bench work sounds (2026-10-22): cut the user's Firefly takes into mono OGGs, and one preview per group.
# python fb_cut_sounds.py <take dir> <assets sounds dir> <preview dir>
# Same cutting as design/items/folding_knife_v1/fk_cut_sounds.py: rough window by hand (from the spectrograms) -> onset
# where the envelope first comes within 30 dB of the window peak, minus 5 ms; end where it last stays above
# max(peak - 45 dB, floor + 6 dB), plus 20 ms; 3 ms fade in, 40 ms fade out. One gain per group, loudest cut at -3 dBFS.
# The rag group is levelled per cut instead (its strokes came out up to 20 dB apart, a generation artefact, and two
# carry sharp knocks that set the sample peak): each cut is raised to the loudest cut's 95th-percentile 10 ms level,
# never past -3 dBFS peak.
# Continuous takes (stir) are cut whole between the quietest points near the hand marks, with 30 ms fades.
# Windows (seconds in the take):
#   钉钉子: 13 single full blows, no light taps in the take. The three with the most body under 1 kHz (3.80, 2.48, 7.60 s)
#           are the seating blows; six of the rest are the strikes; the light taps reuse the strikes, quieter (sounds.json).
#   拿钉子: the flap at 0.25 s, five picks (the 5.7 kHz ring is the nails clinking); the long rummage at 6.9-9.1 s unused.
#   撬开漆罐: one opening only (three prying creaks, the pop and the lid down at 2.74 s), used whole.
#   搅漆: ~10 s of stirring, three pieces; the last ends with the stick lifted out.
#   抹布上漆: dips are the pieces with the 0.6-2.4 kHz wet body (0.04, 4.64 s); five strokes; the two sharp rim knocks
#           (2.5, 7.1 s) unused; the stroke at 8.9-9.9 s too (a knock at 9.3 s caps its peak, the stroke stays 8 dB under the rest).
#   拧螺丝: hand turns, ~0.3-0.6 s each with gaps; the two lower, tighter ones (7.36, 9.62 s) are the seating turns.
import os, subprocess, sys, wave, numpy as np

SRC, DST, PREVIEW = sys.argv[1], sys.argv[2], sys.argv[3]
NAIL_STRIKE = [0.0, 1.478, 4.662, 5.980, 6.406, 8.434]
NAIL_SEAT = [3.798, 2.478, 7.600]
NAIL_ONSETS = sorted([0.0, 0.996, 1.478, 2.478, 2.990, 3.798, 4.662, 5.236, 5.980, 6.406, 7.600, 8.434, 9.620, 10.0])


def blow(t):
    nxt = next(o for o in NAIL_ONSETS if o > t + 0.01)
    return max(0.0, t - 0.02), min(nxt - 0.02, t + 0.7)


GROUPS = {
    'nail': [('钉钉子', 'furniture/nail_strike_%d' % (i + 1)) + blow(t) for i, t in enumerate(NAIL_STRIKE)]
            + [('钉钉子', 'furniture/nail_seat_%d' % (i + 1)) + blow(t) for i, t in enumerate(NAIL_SEAT)],
    'pick': [('拿钉子', 'furniture/box_open_1', 0.2, 0.9)]
            + [('拿钉子', 'furniture/nail_pick_%d' % (i + 1), a, b) for i, (a, b) in
               enumerate([(1.25, 1.9), (2.62, 3.62), (5.32, 5.76), (6.1, 6.56), (9.18, 9.78)])],
    'can_open': [('撬开漆罐', 'furniture/can_open_1', 0.0, 3.46)],
    'rag': [('抹布上漆', 'furniture/rag_dip_%d' % (i + 1), a, b) for i, (a, b) in enumerate([(0.0, 1.32), (4.6, 5.84)])]
           + [('抹布上漆', 'furniture/rag_wipe_%d' % (i + 1), a, b) for i, (a, b) in
              enumerate([(1.64, 2.16), (3.72, 4.54), (5.85, 6.9), (8.06, 8.92)])],
    'screw': [('拧螺丝', 'furniture/screw_turn_%d' % (i + 1), a, b) for i, (a, b) in
              enumerate([(0.0, 0.5), (0.55, 0.98), (1.1, 1.64), (1.78, 2.32), (2.72, 3.5), (3.92, 4.62), (5.0, 5.7),
                         (6.62, 7.1)])]
             + [('拧螺丝', 'furniture/screw_seat_%d' % (i + 1), a, b) for i, (a, b) in enumerate([(7.32, 7.82), (9.58, 9.98)])],
}
LEVEL_EACH = {'rag'}
STIR = [(0.0, 3.0), (3.0, 6.2), (6.2, 10.0)]   # hand marks; each boundary moves to the quietest 10 ms within 0.35 s


def load(name):
    with wave.open(os.path.join(SRC, name + '.wav')) as w:
        sr, ch = w.getframerate(), w.getnchannels()
        x = np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float64) / 32768
    return x.reshape(-1, ch).mean(1), sr


def env(x, sr, hop):
    fr = x[:len(x) // hop * hop].reshape(-1, hop)
    return 20 * np.log10(np.sqrt((fr ** 2).mean(1)) + 1e-9)


def bounds(x, sr, a, b):
    hop = sr // 200
    seg = x[int(a * sr):int(b * sr)]
    db = env(seg, sr, hop)
    floor = np.percentile(env(x, sr, hop), 10)
    pk = db.max()
    on = np.argmax(db > pk - 30)
    keep = np.nonzero(db > max(pk - 45, floor + 6))[0]
    t0 = max(0.0, a + on * hop / sr - 0.005)
    t1 = min(b, a + (keep[-1] + 1) * hop / sr + 0.02)
    return t0, t1, np.abs(x[int(t0 * sr):int(t1 * sr)]).max()


def quiet(x, sr, t):
    if t <= 0 or t >= len(x) / sr - 0.01:
        return min(max(t, 0.0), len(x) / sr)
    hop = sr // 100
    lo = max(0, int((t - 0.35) * 100))
    db = env(x[lo * hop:int((t + 0.35) * sr)], sr, hop)
    return (lo + int(np.argmin(db))) * hop / sr


def write(take, out, t0, d, gain_db, fade_in, fade_out):
    dst = os.path.join(DST, out + '.ogg')
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    af = 'pan=mono|c0=0.5*c0+0.5*c1,volume=%.2fdB,afade=t=in:d=%.3f,afade=t=out:st=%.3f:d=%.3f' % (
        gain_db, fade_in, max(0, d - fade_out), fade_out)
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-ss', '%.3f' % t0, '-t', '%.3f' % d, '-i', os.path.join(SRC, take + '.wav'),
                    '-af', af, '-c:a', 'libvorbis', '-q:a', '6', dst], check=True)
    return dst


def preview(group, files):
    # every cut of the group one after another with 0.6 s of silence, to listen to outside the game
    os.makedirs(PREVIEW, exist_ok=True)
    args, chain = [], ''
    for i, f in enumerate(files):
        args += ['-i', f]
        chain += '[%d]apad=pad_dur=0.6[p%d];' % (i, i)
    chain += ''.join('[p%d]' % i for i in range(len(files))) + 'concat=n=%d:v=0:a=1' % len(files)
    subprocess.run(['ffmpeg', '-v', 'error', '-y'] + args + ['-filter_complex', chain, '-c:a', 'libvorbis', '-q:a', '6',
                    os.path.join(PREVIEW, 'preview_%s.ogg' % group)], check=True)


for group, cuts in GROUPS.items():
    info = []
    for take, out, a, b in cuts:
        x, sr = load(take)
        t0, t1, peak = bounds(x, sr, a, b)
        info.append((take, out, t0, t1, peak))
    gain_db = -3 - 20 * np.log10(max(i[4] for i in info))
    loud = {}
    if group in LEVEL_EACH:
        for take, out, t0, t1, peak in info:
            x, sr = load(take)
            loud[out] = np.percentile(env(x[int(t0 * sr):int(t1 * sr)], sr, sr // 100), 95)
    files = []
    for take, out, t0, t1, peak in info:
        g = gain_db
        if loud:
            g = min(-3 - 20 * np.log10(peak), -3 - 20 * np.log10(max(i[4] for i in info)) + max(loud.values()) - loud[out])
        files.append(write(take, out, t0, t1 - t0, g, 0.003, 0.04))
        print('%-30s %5.2f-%5.2f s  %.2f s  peak %5.1f dB%s' % (out, t0, t1, t1 - t0, 20 * np.log10(peak) + g,
              '  level %5.1f dB' % (loud[out] + g) if loud else ''))
    preview(group, files)

x, sr = load('搅漆')
cuts = [(quiet(x, sr, a), quiet(x, sr, b)) for a, b in STIR]
gain_db = -3 - 20 * np.log10(max(np.abs(x[int(a * sr):int(b * sr)]).max() for a, b in cuts))
files = []
for i, (a, b) in enumerate(cuts):
    files.append(write('搅漆', 'furniture/stain_stir_%d' % (i + 1), a, b - a, gain_db, 0.03, 0.03))
    print('%-30s %5.2f-%5.2f s  %.2f s' % ('furniture/stain_stir_%d' % (i + 1), a, b, b - a))
preview('stir', files)
