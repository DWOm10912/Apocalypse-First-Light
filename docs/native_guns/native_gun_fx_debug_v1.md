# Native Gun FX Debug V1

Current build includes [Projection Compatibility V2](first_person_fx_projection_compat_v2.md). Diagnostics themselves remain opt-in; 50 ms flash behavior, shot validation, recoil and server ballistics are unchanged. BOOT version is `projection-compat-v2`.

## Enable

Add JVM arguments (not game arguments) in the independent launcher's version settings:

```text
-Dafl.gunFxDebug=true -Dafl.gunFxDebugLabel=OFF
```

For the second launch with shaders enabled use label `ON`. Label is user-supplied, not shader detection. Do not also enable the older `afl.shotSnapshotDebug` during this test: its output is not covered by the new limit. Remove arguments after testing.

Output goes to gameDir `logs/latest.log`, prefix `[AFL GUN FX DEBUG]`. BOOT reports debug version, label and limit. Limit is 1200 diagnostic event lines per JVM session, followed by LIMIT_REACHED; restart if reached. No idle per-frame logging. Matrix sampling is opt-in and bounded by the same limit.

## Events

| Event | Meaning |
| --- | --- |
| CAPTURE | Shot ID, capture success or skip reason, sample frame/gun, frame interval, registry item, FOV option, window dimensions and camera type |
| MATRICES | Only with additional `-Dafl.gunFxDebugMatrices=true`: last sampled first-person muzzle bone pose, raw hand projection, cached world view/projection; 16 floats each, column-major |
| PROJECTION_SANITIZE | Per capture: sampleFrame, firstPerson, handDepthBefore/worldDepth/handDepthAfter (`m22,m32`), changed flag |
| SNAPSHOT_DIRECTION | Per capture: last sampled rawLength, normalized vector, presented acceptance; correlate sampleFrame/gun with CAPTURE (not server acceptance) |
| CONFIRM | Received server result, snapshot presence, gun identities and authoritative endpoint |
| FX_QUEUED | Valid local snapshot was accepted for visuals; includes suppressed state and frozen muzzle/direction |
| FLASH_WORLD_SUBMIT | Frozen first-frame world flash submitted, camera/origin, stage and buffer class |
| FLASH_ATTACHED_SUBMIT | Attached tail submitted, age and buffer class |
| CASING_BIRTH | Confirmed shot ID for local first-person casing, origin and camera distance; other legacy paths may report ID 0 |

Matrices are last-sampled render data, not a fresh input-time renderer evaluation. CAPTURE failures can contain an older sample: use sampleFrame/sampleGun and the skip reason. Submission logs do not prove GPU visibility or actual shader selection; no Oculus internal API or GPU state mutation is introduced.

## Test protocol

Same location and view, unsuppressed P9 and BR51, HIP/ADS each 3–5 slow single shots. First run Shader OFF with OFF label; preserve `logs/latest.log` as `fx-off.log` before the next launch. Second run Oculus + Complementary ON with ON label; preserve as `fx-on.log`. Record a short video of each run. Keep FOV, resolution and FPS cap identical for this comparison.

Historical diagnostic V1 build: PASS (`gradlew.bat build --offline`, 24 seconds). Its hash is obsolete for V2; see the V2 report for current build verification. User-supplied ON/OFF logs confirmed V1 diagnostic capture; V2 runtime acceptance is still pending. Submission is never proof of pixel visibility.
