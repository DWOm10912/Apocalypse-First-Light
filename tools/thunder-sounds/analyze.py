"""Thunder takes: loudness envelope (dBFS per 100 ms), peaks, the quietest 0.5 s (noise floor), and spectrum share above 4 kHz.

python tools/thunder-sounds/analyze.py design/audio/thunder/source/*.wav
"""
import sys
import wave

import numpy as np


def load(path):
    with wave.open(path, 'rb') as w:
        ch, sw, sr, n = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
        raw = w.readframes(n)
    assert sw == 2, 'expects 16-bit PCM'
    a = np.frombuffer(raw, dtype='<i2').astype(np.float32) / 32768.0
    return a.reshape(-1, ch).mean(axis=1), sr, ch


def db(x):
    return 20 * np.log10(max(x, 1e-9))


def main(paths):
    for p in paths:
        x, sr, ch = load(p)
        hop = sr // 10
        rms = np.array([np.sqrt(np.mean(x[i:i + hop] ** 2)) for i in range(0, len(x) - hop + 1, hop)])
        env = ' '.join(f'{db(r):4.0f}' for r in rms)
        win = sr // 2
        quiet = min(np.sqrt(np.mean(x[i:i + win] ** 2)) for i in range(0, len(x) - win + 1, sr // 20))
        spec = np.abs(np.fft.rfft(x)) ** 2
        f = np.fft.rfftfreq(len(x), 1 / sr)
        hi = spec[f > 4000].sum() / spec.sum()
        lo = spec[f < 200].sum() / spec.sum()
        print(f'{p}: {len(x) / sr:.2f} s, {sr} Hz, {ch} ch, peak {db(np.abs(x).max()):.1f} dBFS, '
              f'quietest 0.5 s {db(quiet):.1f} dBFS, <200 Hz {lo:.0%}, >4 kHz {hi:.1%}')
        print('  dB/100ms:', env)


if __name__ == '__main__':
    main(sys.argv[1:])
