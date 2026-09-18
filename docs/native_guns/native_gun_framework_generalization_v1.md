# Native Gun Framework Generalization V1

Current status: implemented for the existing P9-01 and BR51-01 definitions. HR55-01 validation was explicitly deferred by the user and is not registered.

## Public framework

- Common input and authoritative action classes are `NativeGunInput` and `NativeGunActions`; the former `P901Input` / `P901Actions` public names no longer exist.
- `WeaponClass` is required in every `data/apocalypse_firstlight/native_guns/*.json` definition. Supported values are `pistol`, `rifle`, `precision_rifle`, `machine_gun`, `smg`, `shotgun`, and `special`.
- Weapon IDs are not used to infer class, HUD size, magazine insertion timing, trail preset, ADS calibration, or normal/dry-fire sounds.
- Class defaults are supplied by `NativeGunPresentation` and `NativeAdsCalibration`. A definition may override HUD dimensions, `mag_in_tick`, trail preset, and ADS profile in JSON.
- `fire_sound` and `dry_fire_sound` are required definition fields. Generic no-animation reload fallbacks use `native_gun_*` sound events backed by the established P9 audio files, preserving current audible behavior without making P9 the public fallback API.
- Camera-bone consumption accepts any animated `NativeGunItem`; it no longer whitelists P9/BR51 IDs.

## Frozen existing behavior

P9-01 and BR51-01 explicitly carry their accepted presentation values in JSON. Gameplay balance, semi-auto behavior, ammunition, capacities, reload durations, recoil, spread, ranges, noise, attachment compatibility, models, textures, animation resources, and maintenance behavior were not changed. P9's empty-reload two-tick visual synchronization remains an intentional P9-only implementation detail.

## Verification boundary

`compileJava processResources` passes. The targeted class-driven fixture verifies that the same P9/BR JSON parsed under unrelated fixture IDs preserves class presentation, proving the result is not derived from registry names; it also rejects an unknown `weapon_class`. No graphical client or multiplayer runtime was performed in this task.
