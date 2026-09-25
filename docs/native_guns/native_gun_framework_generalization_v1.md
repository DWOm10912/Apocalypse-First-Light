# Native Gun Framework Generalization V1

Current status: P9-01, BR51-01, HR55 and C.A.T use the public framework. P9/BR51/HR55 retain legacy SEMI-only data; C.A.T supports SEMI/AUTO, default SEMI. Historical verification below describes the original generalization task.

## Public framework

- Common input and authoritative action classes are `NativeGunInput` and `NativeGunActions`; the former `P901Input` / `P901Actions` public names no longer exist.
- FireMode V1 adds `NativeFireMode`, `NativeFireProfile`, `NativeFireModes` and `NativeFireControl`: data-driven SEMI/BURST/AUTO independent of WeaponClass, per-stack mode NBT, and per-player server trigger scheduling through the existing firing pipeline. See the [Playbook](native_weapon_integration_playbook_v1.md), section 19. No weapon-ID fire-mode rules are used.
- `WeaponClass` is required in every `data/apocalypse_firstlight/native_guns/*.json` definition. Supported values are `pistol`, `rifle`, `precision_rifle`, `machine_gun`, `smg`, `shotgun`, and `special`.
- Weapon IDs are not used to infer class, HUD size, magazine insertion timing, trail preset, ADS calibration, or normal/dry-fire sounds.
- Class defaults are supplied by `NativeGunPresentation` and `NativeAdsCalibration`. A definition may override HUD dimensions, `mag_in_tick`, trail preset, and ADS profile in JSON.
- `fire_sound` and `dry_fire_sound` are required definition fields. Generic no-animation reload fallbacks use `native_gun_*` sound events backed by the established P9 audio files, preserving current audible behavior without making P9 the public fallback API.
- Camera-bone consumption accepts any animated `NativeGunItem`; it no longer whitelists P9/BR51 IDs.
- Configured Native Guns register three GeckoLib controllers in order: `baseline` always supplies `static_idle` for the full ready pose; optional `empty_state` supplies `static_bolt_caught` when the current render stack has zero magazine rounds; `action` plays one-shot draw/shoot/reload/inspect clips and otherwise stops. The action layer runs last, so its authored channels override empty-state channels during a shot or empty reload. Empty state continues supplying channels omitted by draw. This is a shared visual rule without weapon-ID checks. P9 retains its separate action controller; an additional P9 `empty_draw_slide` controller runs after it only during a zero-round draw to hold the authored empty slide pose. See `p9_01_artist_asset_integration_v1.md`.

## First-Person-Only Animation V1

`weapon/client/NativeGunContextRenderer.java` is the common render-context policy used by both `NativeAnimatedWeaponRenderer` and `P901Renderer`. The controller description above applies to the animated path; third-person hand rendering bypasses controller evaluation.

- First-person hands retain the existing animated path, including empty baseline, actions, arms, temporary magazines, ADS and attachments.
- `THIRD_PERSON_RIGHT_HAND` and `THIRD_PERSON_LEFT_HAND` use `NativeThirdPersonPose`: a private bone hierarchy copied from the authored initial transforms, with the existing loaded idle clip's time-zero baked channel values applied. This is a complete loaded baseline, not an unposed raw bind model. It stays fixed during draw, put-away, reload, inspect, shooting and empty-state changes. Original animation bones/controllers are not mutated. The private pose is rebuilt when the baked model or animation identity changes on resource reload.
- FP-only subtrees are omitted by common names: `fp_only_*`, `empty_old_*`, `reload_mag*`, `new_mag*`, `ref_*`; and `additional_magazine`, `lefthand`, `righthand`, `left_hand_anchor`, `right_hand_anchor`, `camera`, `view`, `ref`, `refit`, `positioning`. Real magazine/gun grouping roots and `positioning2` attachment/FX anchors remain.
- Geometry and textures remain the existing resources. Recursive rendering still uses the real current stack and existing sight, muzzle, magazine replacement and shooting-FX layers. Gun-body shoot animation is suppressed in third person; sound, muzzle flash, tracer and shot feedback are not replaced or disabled.
- Local F5 and remote held guns follow the same hand-context policy, with no weapon-ID or local/remote-player special case. GUI, GROUND, FIXED, HEAD and other contexts retain their previous paths. New Native Guns must use this common policy.

Verification for this change: one offline `compileJava` invocation passed. No resource files or loading schema changed, so `processResources` was not run. No client, GameTest or multiplayer/visual test was run; F5, remote attachments/FX and returning to first person require user testing. Combat, ammunition, damage, FireMode, ADS and HUD behavior were not changed.

## Hybrid Mesh Runtime Core V1

2026-09-24: Native rendering accepts an optional `meshes/<id>.aflmesh.json` sidecar alongside `geo/<id>.geo.json`. `NativeGunContextRenderer.renderCubesOfBone` appends rigid per-bone Mesh geometry after existing Cubes, using the actual animated or frozen third-person pose and existing buffers. `NativeAnimatedWeaponRenderer` and `P901Renderer` inherit this adapter. The independent maintenance renderer also supports Mesh drawing and generation-aware Cube+Mesh bounds. The formal `silverwood_12` now opts in with its V2 sidecar; BR51, HR55 and P9 remain unchanged, and no sidecar preserves the old render path. Core/headless verification is complete; final in-game acceptance of the formal Silverwood ID, graphical/reload behavior and shader compatibility remains pending. See [Hybrid Mesh Runtime V1](hybrid_mesh_runtime_v1.md) for the strict converter, coordinate contract, dev fixture and verification boundaries. The earlier verification paragraphs describe their respective changes, not Mesh runtime acceptance.

## Frozen existing behavior

P9-01 and BR51-01 explicitly carry their accepted presentation values in JSON. Gameplay balance, semi-auto behavior, ammunition, capacities, reload durations, recoil, spread, ranges, noise, attachment compatibility, models, textures, animation resources, and maintenance behavior were not changed. P9's empty-reload two-tick visual synchronization remains an intentional P9-only implementation detail.

## Verification boundary

`compileJava processResources` passes. The targeted class-driven fixture verifies that the same P9/BR JSON parsed under unrelated fixture IDs preserves class presentation, proving the result is not derived from registry names; it also rejects an unknown `weapon_class`. No graphical client or multiplayer runtime was performed in this task.
