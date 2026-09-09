# First-Person Weapon Sway V1

Visual-only implementation for P9 and configured Native guns (including BR51). `NativeWeaponSway` transforms a detached first-person pose shared by gun geometry, hand anchors and attachments. No camera, bullet/hitscan, spread, stance accuracy, recoil calculation or ADS calibration changes. Maintenance/world/item rendering does not receive this layer.

`WeaponSwayProfile.DEFAULT` centralizes amplitudes: X 0.0008, Y 0.0012, Z 0; yaw 0.20°, pitch 0.15°, roll 0.07°. Continuous sin/cos periods 4.2 and 6.7 seconds; time is client gameTime + partialTick, not wall clock or random. Shots do not reset phase. World/player replacement resets amplitude and paused ticks do not advance smoothing.

HIP 1.0; ADS 0.30; crouch ADS 0.20, interpolated with existing ADS progress. Walking multiplies by 0.40; reload by 0.10; sprint, inspect, draw/put-away and other ADS-blocking actions target 0. Screen/maintenance rendering applies no sway. Multipliers converge with a 150 ms exponential time constant at client ticks and are interpolated for rendering, so action suppression fades rather than snaps.

Composition: shared visual sway → existing viewmodel recoil → existing ADS correction → existing weapon pose/animation/hand anchors. Recoil and ADS math are not reordered relative to each other. The scope does not alter actual aiming; sight graphics can drift slightly by design. Future per-gun profiles can select another WeaponSwayProfile without modifying Native Gun JSON.

Files: `weapon/client/WeaponSwayProfile.java`, `NativeWeaponSway.java`, `NativeGunAds.java`, `ConfiguredGunFirstPerson.java`, `P901FirstPerson.java` under `src/main/java/com/antaurora/apofirstlight/`.

Verification: compile/build log `build/gun-name-sway-build.log`. Code inspection confirms shared gun/arm transform and no gameplay writes. P9/BR51 graphical sway, ADS/red-dot alignment, reload and subjective amplitude have NOT been runtime-verified this round; do not interpret build success as visual acceptance.
