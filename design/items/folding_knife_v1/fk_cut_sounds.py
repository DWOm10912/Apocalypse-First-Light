# Folding knife V1 sounds (2026-10-22): cut the user's Firefly takes into mono OGGs.
# python fk_cut_sounds.py <take dir> <assets sounds dir>
# Each cut: rough window by hand (from the spectrograms) -> onset where the envelope first comes within 30 dB of the window
# peak, minus 5 ms; end where it last stays above max(peak - TAIL dB, floor + 6 dB), plus 20 ms; 3 ms fade in, 40 ms fade out.
# TAIL is 45 dB, except the stabs and hard hits (60): the stab windows are cut by hand to end after each pull-out (the
# room tone under them is only ~45 dB down), and the metal hit keeps its ring.
# One gain per group so the loudest cut in the group peaks at -3 dBFS and the quiet ones stay quieter.
import os, subprocess, sys, wave, numpy as np

SRC, DST = sys.argv[1], sys.argv[2]
WOOD = 'Firefly_audio_The_tip_of_a_steel_pocket_knife_jabbing_into_a_thi_variation2'
GROUPS = {
    'open_close': [('开刀', 'folding_knife/open_1', 0.0, 1.0), ('收刀', 'folding_knife/close_1', 0.0, 1.0)],
    'swing': [('挥空', 'folding_knife/swing_1', 0.0, 0.6), ('挥空', 'folding_knife/swing_2', 0.7, 1.4),
              ('挥空', 'folding_knife/swing_3', 1.45, 2.5)],
    # the first four stabs, each with its pull-out (user 2026-10-22)
    'stab': [('刺入', 'folding_knife/stab_flesh_1', 0.0, 0.82), ('刺入', 'folding_knife/stab_flesh_2', 1.45, 2.45),
             ('刺入', 'folding_knife/stab_flesh_3', 2.88, 3.75), ('刺入', 'folding_knife/stab_flesh_4', 4.88, 6.3)],
    'whittle': [('削木头', 'folding_knife/whittle_%d' % (i + 1), a, b) for i, (a, b) in
                enumerate([(0.0, 1.2), (2.2, 2.85), (4.8, 5.6), (6.4, 7.1), (8.1, 8.85), (9.5, 10.0)])],
    # take 1 rings at 3 kHz (metal); the rest are short dry ticks with no low body (stone); no wood hit in this take
    'hit': [('刺入硬物', 'folding_knife/hit_metal_1', 0.0, 1.0), ('刺入硬物', 'folding_knife/hit_stone_1', 2.45, 3.5),
            ('刺入硬物', 'folding_knife/hit_stone_2', 3.9, 5.0), ('刺入硬物', 'folding_knife/hit_stone_3', 6.0, 7.0),
            ('刺入硬物', 'folding_knife/hit_stone_4', 8.6, 9.4)],
    # wood re-generated on its own (user 2026-10-22): four tocks with low body; the faint ticks after them are left out
    'hit_wood': [(WOOD, 'folding_knife/hit_wood_%d' % (i + 1), a, b) for i, (a, b) in
                 enumerate([(0.0, 0.4), (1.38, 2.2), (2.7, 3.6), (4.05, 4.4)])],
    'tool_down': [('手锯放到台面', 'workbench/tool_down_1', 0.0, 1.01)],
}


def load(name):
    with wave.open(os.path.join(SRC, name + '.wav')) as w:
        sr, ch = w.getframerate(), w.getnchannels()
        x = np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float64) / 32768
    return x.reshape(-1, ch).mean(1), sr


TAIL = {'stab': 60, 'hit': 60}


def bounds(x, sr, a, b, tail):
    hop = sr // 200
    seg = x[int(a * sr):int(b * sr)]
    fr = seg[:len(seg) // hop * hop].reshape(-1, hop)
    db = 20 * np.log10(np.sqrt((fr ** 2).mean(1)) + 1e-9)
    floor = np.percentile(20 * np.log10(np.sqrt((x[:len(x) // hop * hop].reshape(-1, hop) ** 2).mean(1)) + 1e-9), 10)
    pk = db.max()
    on = np.argmax(db > pk - 30)
    keep = np.nonzero(db > max(pk - tail, floor + 6))[0]
    t0 = max(0.0, a + on * hop / sr - 0.005)
    t1 = min(b, a + (keep[-1] + 1) * hop / sr + 0.02)
    return t0, t1, np.abs(seg).max()


for group, cuts in GROUPS.items():
    info = []
    for take, out, a, b in cuts:
        x, sr = load(take)
        t0, t1, peak = bounds(x, sr, a, b, TAIL.get(group, 45))
        info.append((take, out, t0, t1, peak))
    gain_db = -3 - 20 * np.log10(max(i[4] for i in info))
    for take, out, t0, t1, peak in info:
        dst = os.path.join(DST, out + '.ogg')
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        d = t1 - t0
        af = 'pan=mono|c0=0.5*c0+0.5*c1,volume=%.2fdB,afade=t=in:d=0.003,afade=t=out:st=%.3f:d=0.04' % (gain_db, max(0, d - 0.04))
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-ss', '%.3f' % t0, '-t', '%.3f' % d, '-i', os.path.join(SRC, take + '.wav'),
                        '-af', af, '-c:a', 'libvorbis', '-q:a', '6', dst], check=True)
        print('%-30s %5.2f-%5.2f s  %.2f s  peak %5.1f dB' % (out, t0, t1, d, 20 * np.log10(peak) + gain_db))
