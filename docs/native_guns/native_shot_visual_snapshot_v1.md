# Native Shot Visual Snapshot V1

Glass V1: the server now resolves tagged breakable glass continuously before returning the final endpoint; the existing shotId pairing and frozen muzzle remain unchanged. See [Native Bullet Glass Penetration V1](native_bullet_glass_penetration_v1.md). Broken glass is not a tracer stopping point.

## Boundary and audited order

Client defines input time and the muzzle of the currently presented gun; server defines whether the shot exists and its hit endpoint. Only a long `shotId` is added to the request/result; no client position, aim, damage or endpoint is accepted. Network protocol **21**, matching client/server required. The local successful-shot packet now includes endpoint and shotId atomically with slot/gun identity; the separate FX packet goes to tracking observers only, avoiding local confirmation/FX delivery on different frames.

Old order: input → request → server ammo/animation trigger → authoritative trace → recoil confirmation → FX result → AFTER_PARTICLES trail render → first-person muzzle anchor sampling. Thus the first sampled muzzle could already contain the new recoil/animation, and its trail was first drawn next frame. The old flash kept following the bone. This is a code-order finding, not a measured pixel-error attribution.

## Snapshot

Current coordinate conversion update: [Projection Compatibility V2](first_person_fx_projection_compat_v2.md) restores hand projection depth from world depth for first-person only, retaining the existing conversion and all snapshot rules. V2 is marked FIXED after user-confirmed in-game acceptance on 2026-09-09; individual regression variants were not separately reported.

`NativeShotVisualSnapshot` receives the actual first-person, attachment-resolved muzzle converted into world space by `NativeGunFx`. The original inverse-world-view / hand-to-world projection conversion has been restored after the projection-free resolver caused user-confirmed Shader OFF regressions (missing flash and left-offset tracer). The V1 rollback is historical; current [Projection Compatibility V2](first_person_fx_projection_compat_v2.md) is fixed per user in-game acceptance. On input, it freezes that currently presented pose into an independent immutable record with shotId, gun identity, input/pose timestamps, origin, barrel direction and suppressor state, BEFORE sending the shot request and before that shot's confirmed recoil. Pose includes already rendered sway/ADS/animation/residual recoil. It does not evaluate a hypothetical unseen pose at input time.

Presentation cache is guarded by world/player, stack equality, gun identity, at most one render-frame age and one game tick. It is only a source for input-time copying; later shots cannot overwrite pending records. Missing/stale poses skip local visuals, never fall back to an eye position or a future recoiled muzzle. This may suppress visuals immediately after an unseen equip/state change; server shooting remains unaffected.

Per-shot map is capped at 128. Successful server result echoes shotId and supplies endpoint, consuming exactly that pending entry. Unconfirmed/rejected records produce no effects and expire after 100 game ticks; world/player replacement clears all. No new explicit rejection packet. Existing server ammo/cooldown/validation remains authoritative; shotId is correlation metadata, not permission to fire. Server animation trigger and recoil values/formulas remain unchanged. With explicit user approval, local first-person recoil now queues until RenderTick END after the first visual frame, at most one render frame. A new shot input flushes queued recoil before sending Vanilla aim synchronization, so another shot cannot use pre-recoil aim. Queue entries validate player/world/slot/gun and cannot leak to a different player/world; non-first-person confirmation retains immediate application.

## Shared visuals

On confirmation, tracer uses frozen origin → server endpoint, with visual start offset **0.20 blocks**, distance-clamped under existing trail rules. Trail lifetime/speed/profile and shader remain unchanged. Start never follows current muzzle. Bare local first-person muzzle flash uses the same frozen origin/direction for its first world-render frame. It now has a **50 ms monotonic-time lifetime beginning at that first draw submission**: subsequent frames draw the fading remainder directly under the current final muzzle bone/attachment pose, following recoil/ADS/sway rather than leaving a frozen core in world space. It does not draw both paths in the same frame or replay the frozen flash. The server endpoint and frozen tracer never follow this live tail. See [Timed muzzle flash](native_muzzle_flash_lifetime_v1.md). Suppressed smoke spawns at the frozen origin and has no bare flash tail. Network delay does not change the saved input pose. Suppressor resolver is the existing `NativeMuzzleRendering.applyExit`/muzzle_exit_anchor path. Casing handling remains on its existing path.

Local first-person positive IDs never fall back to the old delayed muzzle path if a snapshot is unavailable. Other players/third-person use the existing resolver. A local camera-context switch can discard a pending first-person snapshot and use the third-person path. Server hitscan, spread, damage, range, recoil values, sway values, models and animations are unchanged. No glass/other penetration.

## Debug and verification

Bounded shader-diagnostic logging is available separately via `-Dafl.gunFxDebug=true`; see [Native Gun FX Debug V1](native_gun_fx_debug_v1.md). It records capture skips, confirmation, FX submission and projection samples without changing shot acceptance.

`-Dafl.shotSnapshotDebug=true` logs capture shotId, input/pose time, muzzle and confirmed endpoint. No default debug drawing/resources. Optional isolated probe: `src/dev/shot-snapshot-client.init.gradle`, `ShotSnapshotProbe`.

Build and existing 25 required GameTests passed (`build/shot-snapshot-tests.log`). Final build and trail geometry/shotId packet roundtrip passed (`build/shot-snapshot-final-checks.log`). The graphical probe overlapped confirmed user input and failed at BR51 capture; its intermediate PASS markers only checked pending-entry removal, not successful effect creation, and are NOT accepted as variant verification. No 60/120 FPS frame acceptance claimed.

Intermediate user feedback: P9 looked correct but BR51 showed a detached flash (screenshot `codex-clipboard-a92a6ea1-2024-4f5f-9340-3b2fe9df772a.png`). The user then authorized delaying confirmed recoil until the first visual frame ends. After implementing that and single-frame frozen flash, user feedback was “看起来没问题了”. This supersedes the earlier failed visual checkpoint.

Isolated client `build/shot-recoil-frame-client.log` passed all 12 probe cases: P9 HIP/ADS, suppressed+red-dot P9 HIP/ADS, BR51 HIP/ADS at 60 and 120 FPS caps. Probe checks actual confirmation (not mere pending removal); logs show FLASH presented before RECOIL applied. These are configured-cap runtime tests, not 60/120 FPS frame recordings. The final protocol-21 atomic-packet cleanup was compiled after that client run; no additional graphical client was launched after user acceptance. Final build/packet geometry checks: `build/shot-recoil-final-build.log`. Fast camera movement, truly concurrent shots and real multiplayer remain unverified.
