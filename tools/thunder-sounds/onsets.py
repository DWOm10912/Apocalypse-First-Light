"""Onsets (a jump of 9 dB or more against the previous 150 ms, 10 ms frames) and octave-band levels of the whole take and of
its quietest second (where the hiss would show).

python tools/thunder-sounds/onsets.py design/audio/thunder/source/*.wav
"""
import sys

import numpy as np

from analyze import load, db

BANDS = [(20, 60), (60, 125), (125, 250), (250, 500), (500, 1000), (1000, 2000), (2000, 4000), (4000, 8000), (8000, 16000), (16000, 24000)]


def bands(x, sr):
    spec = np.abs(np.fft.rfft(x * np.hanning(len(x)))) ** 2
    f = np.fft.rfftfreq(len(x), 1 / sr)
    return ' '.join(f'{lo}-{hi}:{10 * np.log10(max(spec[(f >= lo) & (f < hi)].sum(), 1e-12)):5.0f}' for lo, hi in BANDS)


def main(paths):
    for p in paths:
        x, sr, _ = load(p)
        hop = sr // 100
        lvl = np.array([db(np.sqrt(np.mean(x[i:i + hop] ** 2))) for i in range(0, len(x) - hop + 1, hop)])
        ons = []
        for i in range(15, len(lvl)):
            if lvl[i] - lvl[i - 15:i].max() >= 9 or (i < 30 and lvl[i] - lvl[:i].min() >= 20 and not ons):
                if not ons or i - ons[-1] > 30:
                    ons.append(i)
        print(p)
        print('  onsets s/peak dB:', ', '.join(f'{i / 100:.2f}/{lvl[i:i + 20].max():.0f}' for i in ons))
        w = sr
        qi = min(range(0, len(x) - w + 1, sr // 20), key=lambda i: np.mean(x[i:i + w] ** 2))
        print('  whole :', bands(x, sr))
        print(f'  quiet {qi / sr:.2f}s:', bands(x[qi:qi + w], sr))


if __name__ == '__main__':
    main(sys.argv[1:])
