# P9-01 Original Audio Design V2

2026-09-09. Candidate-only experiment, not production audio. Human audition pending.

## Scope and provenance

Outputs: `dev_assets/audio_candidates/p9_01/v2/`. Reproduce with bundled Python + NumPy and ffmpeg using `synthesize.py`. The script generates deterministic noise fields and algorithmic micro-reflections; it has no reference-audio input. No external samples, reference MP3, ElevenLabs, or previous gun sounds are used as synthesis material. The already established abstract Target Profile is the design input.

Production resources, animations, weapons, statistics, suppressor and BR51 are unchanged. `analysis.json` records production P9 OGG hashes and verifies they match before/after generation. Existing unrelated working-tree changes are preserved. No build or Minecraft runtime test is needed for these uninstalled candidates, and neither is claimed.

## Target / P9_AUDIO_IDENTITY

Dry, close, damped lightweight-metal identity. Fire uses a fast irregular burst cluster over sustained 300–1500 Hz pressure texture, short low-frequency weight, restrained upper detail and a weak tail. Mechanics share the same 0.71/1.37/2.19/3.83 ms damped algorithmic response but have distinct friction/contact envelopes. This is a proposed identity, not a claim that procedural output sounds realistic.

No sine-resonance main layer, compressor, limiter or long room reverb. Crest is adjusted only by modest envelope weighting. A fixed 300 ms file window is used: 12–16 dB is the candidate target, not directly equivalent to the earlier four-second reference-segment crest measurement.

## Fire variants

| Candidate | Structure |
|---|---|
| A SERVICE | 12 irregular microbursts; 54 ms body exponential time constant |
| B CRISP | 17 microbursts; 47 ms body; stronger initial detail |
| C PUNCH | 10 microbursts; 65 ms body; greater short weight |
| D DRY | 20 microbursts; 37 ms body; earlier, stronger mechanical texture |

Each uses independently generated fields and timing, not just EQ/volume changes. Full intermediate layers are retained as WAV/OGG. All final candidates are mono, 48 kHz Vorbis quality 6, with PCM16 intermediate WAV. The common nominal single-shot WAV peak is -7 dBFS; perceived loudness is not guaranteed identical.

## Verification boundaries

`analysis.json` contains unweighted RMS, sample peak, crest and FFT energy bands for WAV and decoded OGG separately. These are not LUFS, true-peak or subjective harshness measurements. Spectral percentages are diagnostic, not perceptual proof. Encoded clipping is checked after decoding.

17-shot previews read `fire.interval_ticks` and `magazine_capacity` from `src/main/resources/data/apocalypse_firstlight/native_guns/p9_01.json`: 3 ticks, 17 rounds, 0.15 s interval / 400 RPM at 20 TPS. This is a maximum-rate semi-auto stress sequence, not a gameplay fire-mode change. Samples are summed without per-shot normalization or a limiter. Low-band RMS per firing interval is recorded to check persistent accumulation. Human assessment of fatigue, harshness, cannon-like weight, synthetic texture and continuous rumble remains mandatory.

Suppressed audio remains hash-identical. Perceptual compatibility with the accepted suppressed sound is pending; no claim of having listened is made.

## Reload prototypes, not final reload

Runtime animation is `animation.p9_01.reload` (1.3 s), not a literal `reload_tactical` clip; empty is `animation.p9_01.reload_empty` (1.65 s), in `src/main/resources/assets/apocalypse_firstlight/animations/p9_01.animation.json`. Editable source remains `src/main/blockbench/p9_01.bbmodel`.

| Cue | Candidate onset | Basis |
|---|---:|---|
| magazine release | 0.400 s | existing tick 8 and extraction start |
| magazine out | 0.415 s | designed texture during extraction; not a discrete animation event |
| magazine insert | 0.875 s | approach phase; chosen within motion |
| magazine seat | 0.950 s | magazine reaches zero offset, tick 19 |
| slide pull, empty only | 1.250 s | slide starts rearward motion, reaches rear at 1.300 |
| slide snap, empty only | 1.390 s | forward movement from 1.320 reaches zero at 1.390 |

Tactical has no slide cues. Pull is a sustained modulated friction envelope; snap is a short dense contact cluster, not the same sound played twice. Release/out are quieter than seat/snap; prototype peak levels are -24/-27/-24/-18/-24/-18 dBFS respectively, not equal loudness.

`weapon/P901Actions.java` currently plays out at tick 8, in at tick 19 and a combined empty rack at tick 25. These candidate split cues have NOT been wired into that system. Composite timing matches inspected motion numerically, but in-game audiovisual sync remains untested. Fire selection must precede final reload production.

## Human checkpoint

Open `dev_assets/audio_candidates/p9_01/v2/audition.html` for single-shot, 17-shot and prototype controls. Begin at low playback volume. Choose a fire identity or reject all; no candidate has been declared best. No official replacement should occur before that choice.

HUMAN_AUDITION_REQUIRED = YES
