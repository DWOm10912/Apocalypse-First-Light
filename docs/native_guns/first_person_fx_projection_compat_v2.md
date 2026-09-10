# First-Person FX Projection Compatibility V2

Status: FIXED — USER IN-GAME ACCEPTANCE PASSED (2026-09-09). User confirmed: “标记为已修复，游戏内测试没问题”. Acceptance is user-reported, not an agent-run graphical test; individual test-matrix rows were not separately reported. No automatic launcher deployment was performed.

## Root cause and scope

User `fx-off.log` / `fx-on.log` captured hand display-depth compression while world depth remained normal. Unprojecting the compressed hand depth moved P9's world muzzle near the camera and reduced BR51's two-point direction below Vanilla Vec3.normalize's zero cutoff. BR51 snapshots were rejected; casings used a separate path. P9 tracer GPU visibility is not established by queue logs.

`FirstPersonProjectionSanitizer.sanitize(hand, world)` copies hand projection and restores only JOML `m22` and `m32` from the current world projection captured at AFTER_SKY. JOML uses column,row naming: these are column-major indices 10/14, the clip-Z coefficients. X/Y, hand FOV/aspect, and clip-W `m23` are untouched. Caller matrices and render state are never mutated. Equal depth terms give an exact no-op.

Existing inverse world view × inverse world projection × sanitized hand projection × final anchor remains in place. No projection-free resolver, mod/pack checks, depth multiplier, anchor compensation or normalize threshold changes.

## Audited code boundary

All Java paths below are under `src/main/java/com/antaurora/apofirstlight/weapon/client/`.

| Path | V2 behavior |
| --- | --- |
| `NativeGunFx.anchor` first-person snapshot | Sanitize projection before existing two-point muzzle resolution; normalization unchanged |
| `NativeShotVisualSnapshot.presented/capture/confirm` | Acceptance rules and shotId pairing unchanged; presented returns acceptance for diagnostics |
| `NativeGunFx.anchor` first-person legacy tracer, ejection, suppressed smoke | Same sanitation; casing motion parameters unchanged |
| `NativeGunFx.render` | AFTER_SKY world matrix capture, first frozen world flash and subsequent 50 ms attached tail unchanged |
| `NativeBulletTrails.snapshot` | Frozen muzzle to authoritative endpoint; creation, lifetime, RenderType and draw stage unchanged |
| `NativeGunFxLayer` / `P901Renderer` | P9 final bone and `NativeMuzzleRendering.applyExit` unchanged |
| `NativeAnimatedWeaponRenderer` | BR51 mount selection, accessory exit and bare barrelExitOffset unchanged |
| `NativeMuzzleRendering` | Existing accessory `muzzle_exit_anchor` traversal retained; optics/magazines do not select a muzzle attachment |
| Third-person and server | No sanitation outside first-person branches; no ballistics, packets, damage, recoil, accuracy, ammo or attachment-state changes |

## Verification

`src/test/java/com/antaurora/apofirstlight/weapon/client/FirstPersonProjectionSanitizerTest.java` runs through Gradle `projectionMathTest`, wired into `check` / `build`, with no new dependency.

Coverage: exact OFF no-op and unchanged resolver; compressed depth; unchanged input matrices and all non-depth elements; FOV 70/90/110; aspect 16:9/21:9/4:3; yaw/pitch/combined; synthetic HIP/ADS/crouch ADS/sway/recoil/reload/sprint poses; pistol/rifle scales, bare offset and composed accessory exit; ejection origin; finite/nonzero corrected direction above Vanilla cutoff. Synthetic poses/accessory matrices do not prove real model alignment. BR51 recorded anchor and approximate P9 recorded translation are additional fixtures.

Build: PASS, `gradlew.bat clean build --offline`, Java 17, 54 seconds, 15 executed tasks. The initial two clean attempts failed while IDEA's concurrent Gradle import held generated files; after the user closed IDEA, clean and full build succeeded.

Math: PASS, 9,078 cases; minimum corrected two-point direction length `0.004099943` (well above Vanilla's unchanged `0.0001` cutoff). The existing standalone `NativeFlashLifetimeTest` also passed at 30/60/120/300/600/1000 FPS plus boundary, stalled-frame and independent-shot cases. No agent-run GameTest/client rendering session was run. Subsequent user in-game acceptance passed on 2026-09-09.

Artifact: `build/libs/apocalypse_firstlight-1.0.0.jar`; not automatically deployed. Static diff audit: no server/resource/attachment-renderer/third-person changes, no runtime mod/pack checks or compression constants; the synthetic compression values exist only in tests.

## User acceptance — passed

The user confirmed the V2 issue is fixed in-game on 2026-09-09. The following is the retained regression checklist, not a claim that each variant was independently logged or tested by the agent. Diagnostic JVM arguments may now be removed.

Enable `-Dafl.gunFxDebug=true -Dafl.gunFxDebugLabel=OFF`, then repeat with label `ON`. See [debug reference](native_gun_fx_debug_v1.md). Compact PROJECTION_SANITIZE and SNAPSHOT_DIRECTION records accompany capture; full matrices require additional `-Dafl.gunFxDebugMatrices=true`.

- Shader OFF regression checklist: Both guns: HIP, ADS, crouch ADS, repeated fire, suppressors; flash/tracer/casing must retain accepted baseline.
- Oculus 1.8.0 + Complementary Reimagined r5.9 regression checklist: Both guns: correct flash/tracer/casing origins; BR51 snapshot and FX queue restored; P9 no near-camera giant flash.
- Accessories: bare/suppressor, optic, extended magazine (P9 24R / BR51 35R); correct suppressor exit and no unrelated muzzle shifts.
- Third-person OFF/ON regression checklist: no regression.

Only if a new regression is reported: if snapshots, FX queue and flash submissions return with reasonable coordinates but pixels remain absent, investigate RenderType/stage/depth as a separate task. V2 intentionally does not preemptively alter those systems.
