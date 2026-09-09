# Generic Rifle Suppressor Model V1

Asset ID: `rifle_suppressor_01`. Name: 步枪消音器 / Rifle Suppressor.

Status: independent model/texture and BR51 static mock mount completed. Now registered and equippable through [formal integration V1](rifle_suppressor_01_v1.md). The model-only milestone below is historical; integration does not alter accessory geometry or texture.

## Asset and interface

- Source: `src/main/blockbench/rifle_suppressor_01.bbmodel`.
- Geo: `src/main/resources/assets/apocalypse_firstlight/geo/rifle_suppressor_01.geo.json`.
- Texture: `src/main/resources/assets/apocalypse_firstlight/textures/item/rifle_suppressor_01.png` (128×256 PNG, 64×128 UV units; restrained dark gunmetal palette, nominal 2 pixels per model unit).
- Palette correction: sampled BR51's existing neutral metal pixels directly. Body `#2C2D2D`, restrained blocks `#303232` / `#282929`, collar `#353636`, cap `#393B3B`, narrow highlights `#4E5050`, bore `#161717`. Replaces the earlier blue-green gray palette. No random noise or heavy wear. Only PNG pixels and embedded texture data in the independent/fit-preview sources changed; geometry, UVs, mount and exit anchors remain unchanged.
- Root `rifle_suppressor_root` at `[0,0,0]`, rear mounting seat centerline. Forward is local negative Z. Rear sleeve overlaps behind the seat by 0.45 model units.
- Exit `muzzle_exit_anchor` at `[0,0,-11.55]`. Visual FX locator only, never ballistic aim authority.
- Eight-sided shell, 56 cube segments: `rear_mount`, `locking_collar`, `body`, `rear_service_ring`, `front_service_ring`, `front_cap`, `front_bore`, plus exit anchor. No BR51 geometry or player arms in the independent asset.
- Length 12 model units; body 2 units across flats, collar 2.10. Pistol suppressor reference is 9.1 long and 1.5 across body flats. Rifle design uses a heavier locking collar, spaced service rings and recessed bore, not a uniform scale of the pistol asset.
- Inner bore extends from Z -11.53 to -9.4, with real inner faces and an open exit. No black-dot substitute.

## BR51 mock mount

Editable reference: `src/main/blockbench/br51_rifle_suppressor_fit_preview.bbmodel`. Contains a separate `mock_muzzle_anchor` and accessory subtree; original BR51 source/runtime assets, texture and animations are unchanged. Preview-only combined atlas and hidden arm/reference-pad geometry are not runtime resources.

Initial placement at BR51 `muzzle_flash` `[0,11.43125,-28.0375]` left the original flash-hider slots exposed. Corrected mounting seat is `[0,11.4375,-26.2]`, aligned with the actual barrel centerline and backed onto the solid flash-hider section. Rear sleeve reaches Z -25.75, covering the pronged section rather than merely touching its tip. Final mock exit is `[0,11.4375,-37.75]`. This seat is distinct from the naked flash FX locator. Do not reuse the initial tip placement in future integration.

Future rifles provide their own mounting-seat anchor and compatibility declaration; the independent model retains fixed scale and its own root/exit. Formal integration adds an empty production BR51 `muzzle_anchor` at the calibrated seat and data-driven compatibility. Existing cube-based NativeMuzzleRendering resource convention is retained; no new renderer.

## Verification

Live Blockbench preview inspected for independent front, side, front/rear oblique, bare/mounted BR51, interface close-up, first-person static camera and maintenance-pad layout. Valid texture UUID bindings checked. The corrected interface covers the exposed flash-hider slots and retains coaxial alignment.

Previews: `build/rifle-suppressor-model/` — `front.png`, `side.png`, `independent_front_oblique.png`, `rear_oblique.png`, `br51_bare.png`, `br51_mounted.png`, `mount_closeup.png`, `first_person_mock.png`, `maintenance_mock.png`.

First-person is a 70-degree Blockbench static camera mock, not the game's animated viewmodel/FOV. Maintenance uses a representative green-pad layout mock, not the production workstation renderer. Third-person silhouette is checked as an external model view, not a live player. Full in-game ADS, reload, world/maintenance integration and multiplayer remain for the later equip task.

Build log: `build/rifle-suppressor-model/build.log`. No graphical game client launched for this asset-only task.

Neutral-palette revision inspected in live Blockbench: `build/rifle-suppressor-model/neutral_texture_independent.png` and `neutral_texture_mounted.png`. These supersede earlier screenshots for color review. Texture-only rebuild log: `build/rifle-suppressor-model/texture-build.log`. No in-game visual verification for this revision.
