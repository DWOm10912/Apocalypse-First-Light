# First-Person Gun FX Shader Compatibility V1

## Status: failed visual acceptance; baseline restored

The projection-free V1 experiment is withdrawn. User video `C:/Users/willi/Downloads/QQ20260909-154242-HD.mp4` reports missing muzzle flashes and severe leftward tracer displacement even with shaders OFF. The prior compile/numerical PASS did not establish runtime correctness. Shader compatibility is **UNRESOLVED**.

## Findings and failed approach

The original `NativeGunFx` used inverse world view, inverse world projection, current hand projection and the final bone pose to map hand FX into the world pass. Installed Oculus 1.8.0 compresses hand-projection depth (observed 0.125); treating that as physical depth is a compatibility risk.

The attempted `cameraRotation * viewAnchor` replacement removed first-person projection conversion without establishing equivalence with the actual rendered hand/world chain. Its 108 synthetic comparisons tested only the proposed algebra, not real renderer inputs or screen alignment. The exact remaining renderer-space discrepancy has not been proven by runtime matrix capture.

## Current live code

Subsequent scoped change: [Timed muzzle flash](native_muzzle_flash_lifetime_v1.md) adds a 50 ms attached tail after the frozen first frame. The restored projection conversion is untouched; the exact-file baseline check below describes the rollback checkpoint, not the later timing change. Shader compatibility remains unresolved.

- `NativeGunFx.java` restored exactly to its pre-experiment Git baseline, including first-person projection conversion for snapshot muzzle, tracer anchor, casing and smoke.
- Experimental `NativeGunAnchorResolver.java` and `NativeGunAnchorResolverTest.java` removed; available in the prior task patches. No experimental shader resolver remains live.
- Local flash, smoke and tracer retain their shared frozen shot muzzle; casing retains its previous confirmation-driven render-time origin.
- Shot IDs, snapshot pairing, server hit endpoints, ballistics, models, anchor offsets, FX scales, recoil deferral and RenderType unchanged. No manual rightward tracer offset or shader-specific compensation added.
- Third-person behavior unchanged.

## Verification and next checkpoint

- Exact `NativeGunFx.java` baseline diff check: PASS.
- Build: PASS (`gradlew.bat build --offline`, 23 seconds). Log `build/fx-baseline-restore-build.log`; artifact `build/libs/apocalypse_firstlight-1.0.0.jar`.
- Deployment: after user confirmed exit and process check found no client, replaced `D:/Games/MC TEST/.minecraft/versions/1.20.1-Forge_47.4.22/mods/apocalypse_firstlight-1.0.0.jar`. Build/deployed SHA256 both `152B41EADA3A795F01A8C72B39D92BF820A41D46D56E0FEF7E78C278A48ADA19`. Previous jar backed up under `C:/Users/willi/AppData/Local/Temp/afl-fx-regression-backup-31f604e6-ef01-4197-bbae-c15c613f55a2/`.
- User Shader OFF revalidation required: P9/BR51 HIP/ADS, flash per shot, tracer muzzle alignment (including left-offset regression), casing, suppressors and third person. Baseline restoration alone does not prove all intermittent-flash reports fixed.
- Shader work paused until the no-shader baseline is accepted. Later work must validate actual render matrices/passes and flash delivery, not claim compatibility from synthetic math or compilation alone.
