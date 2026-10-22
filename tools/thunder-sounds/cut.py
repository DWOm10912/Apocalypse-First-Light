"""Cut the five Firefly thunder takes (design/audio/thunder/source, one generation per distance band) into the game's
variants: each starts on one of the take's own strikes, fades out, is low-passed for its distance (air takes the highs
off far thunder, which also removes the generator's faint hiss above the rumble) and peak-normalised; the near bands
are mono so the game can place them, the in-cloud roll stays stereo. Writes design/audio/thunder/cut/*.wav and
src/main/resources/assets/apocalypse_firstlight/sounds/weather/thunder_<band>_<n>.ogg (ffmpeg, Vorbis q5).

python tools/thunder-sounds/cut.py
"""
import os
import subprocess
import wave

import numpy as np

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), '..', '..'))
SRC = os.path.join(ROOT, 'design', 'audio', 'thunder', 'source')
CUT = os.path.join(ROOT, 'design', 'audio', 'thunder', 'cut')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'apocalypse_firstlight', 'sounds', 'weather')

# band: (take, low-pass Hz or None, mono, [(from s, to s, fade-in s, fade-out s), ...]); cuts start a little before
# an onset (tools/thunder-sounds/onsets.py) so the strike's own attack is kept
BANDS = {
    'crack': ('1_crack.wav', None, True, [(0.10, 2.45, 0.005, 0.35), (2.52, 10.0, 0.005, 1.2)]),
    'close': ('2_close.wav', 7000, True, [(0.10, 7.00, 0.01, 0.8), (3.22, 10.0, 0.01, 1.0)]),
    'mid': ('3_mid.wav', 3000, True, [(0.08, 10.0, 0.03, 1.2), (3.12, 10.0, 0.03, 1.2)]),
    'far': ('4_far.wav', 900, True, [(0.0, 10.0, 0.3, 1.5), (3.4, 10.0, 0.8, 1.5)]),
    'cloud': ('5_cloud.wav', 1200, False, [(0.0, 10.0, 0.3, 1.5), (4.4, 10.0, 0.8, 1.5)]),
}
PEAK = 10 ** (-1 / 20)


def load(path):
    with wave.open(path, 'rb') as w:
        ch, sr, n = w.getnchannels(), w.getframerate(), w.getnframes()
        a = np.frombuffer(w.readframes(n), dtype='<i2').astype(np.float64) / 32768.0
    return a.reshape(-1, ch), sr


def lowpass(x, sr, fc):
    """Smooth FFT low-pass: flat to fc, cosine roll-off over the octave above, zero past 2 fc (per channel)."""
    n = x.shape[0]
    f = np.fft.rfftfreq(n, 1 / sr)
    g = np.where(f <= fc, 1.0, np.where(f >= 2 * fc, 0.0, 0.5 + 0.5 * np.cos(np.pi * np.log2(np.maximum(f, 1) / fc))))
    return np.stack([np.fft.irfft(np.fft.rfft(x[:, c]) * g, n) for c in range(x.shape[1])], axis=1)


def fades(x, sr, fin, fout):
    n = x.shape[0]
    env = np.ones(n)
    a, b = int(fin * sr), int(fout * sr)
    if a > 0:
        env[:a] = np.sin(np.linspace(0, np.pi / 2, a)) ** 2
    if b > 0:
        env[n - b:] = np.cos(np.linspace(0, np.pi / 2, b)) ** 2
    return x * env[:, None]


def write(path, x, sr):
    pcm = np.clip(np.round(x * 32767), -32768, 32767).astype('<i2')
    with wave.open(path, 'wb') as w:
        w.setnchannels(x.shape[1])
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(pcm.tobytes())


def main():
    os.makedirs(CUT, exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    for band, (take, fc, mono, cuts) in BANDS.items():
        x, sr = load(os.path.join(SRC, take))
        if fc:
            x = lowpass(x, sr, fc)
        if mono:
            x = x.mean(axis=1, keepdims=True)
        for k, (t0, t1, fin, fout) in enumerate(cuts, 1):
            y = fades(x[int(t0 * sr):int(t1 * sr)].copy(), sr, fin, fout)
            y *= PEAK / max(np.abs(y).max(), 1e-9)
            wav = os.path.join(CUT, f'thunder_{band}_{k}.wav')
            ogg = os.path.join(OUT, f'thunder_{band}_{k}.ogg')
            write(wav, y, sr)
            subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '5', ogg], check=True)
            print(f'{band}_{k}: {len(y) / sr:.2f} s, {y.shape[1]} ch, low-pass {fc or "-"} Hz, {os.path.getsize(ogg) // 1024} KB')


if __name__ == '__main__':
    main()
