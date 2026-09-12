"""Original procedural aluminum swing-door preview sounds; no external samples."""
import math
import random
import struct
import wave
import subprocess
from pathlib import Path

RATE = 48000
OUT = Path(__file__).resolve().parents[1] / 'src/main/blockbench/audio'

def render(kind):
    rng = random.Random(910 if kind == 'open' else 911)
    samples = [0.0] * int(RATE * 0.85)
    def impact(start, strength, decay, frequencies):
        low = 0.0
        for j in range(int(RATE * min(0.30, decay * 8))):
            i = int(start * RATE) + j
            if i >= len(samples):
                break
            t = j / RATE
            low = 0.78 * low + 0.22 * rng.uniform(-1, 1)
            env = (1 - math.exp(-t / 0.0015)) * math.exp(-t / decay)
            tone = sum(math.sin(2 * math.pi * f * t) / (k + 1) for k, f in enumerate(frequencies))
            samples[i] += strength * env * (0.72 * low + 0.28 * tone)
    # Soft pivot friction: filtered air/rubber texture, not a motor or a squeal.
    low = 0.0
    for i in range(int(RATE * 0.58)):
        t = i / RATE
        low = 0.93 * low + 0.07 * rng.uniform(-1, 1)
        envelope = math.sin(math.pi * t / 0.58) ** 1.5
        samples[i] += 0.095 * envelope * low
    if kind == 'open':
        impact(0.015, 0.30, 0.020, [370, 1120, 1830])
        impact(0.065, 0.12, 0.017, [510, 1490])
        impact(0.49, 0.065, 0.025, [220, 730])
    else:
        impact(0.485, 0.34, 0.043, [155, 390, 1010])
        impact(0.525, 0.20, 0.026, [210, 670, 1530])
        impact(0.575, 0.10, 0.012, [580, 1720])
    # Shared gain preserves the intentional softer opening. Fade both ends.
    samples = [x * 1.8 * min(1, i / 240, (len(samples)-1-i) / 960) for i, x in enumerate(samples)]
    peak = max(abs(x) for x in samples)
    assert peak < 0.98, peak
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / f'commercial_glass_swing_door_{kind}.wav'
    with wave.open(str(path), 'wb') as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(b''.join(struct.pack('<h', round(x * 32767)) for x in samples))
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', str(path), '-c:a', 'libvorbis', '-q:a', '5', str(path.with_suffix('.ogg'))], check=True)
    print(f'{kind}: 0.85s, mono, {RATE} Hz, peak={peak:.4f}; {path}')

if __name__ == '__main__':
    for kind in ('open', 'close'):
        render(kind)
