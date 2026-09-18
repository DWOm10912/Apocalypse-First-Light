# C.A.T Native Gun Integration V1

`apocalypse_firstlight:cat` is integrated through the standard `ConfiguredNativeGunItem` path. V1 is a SEMI-only rifle using the existing 7.62x51mm round and casing registry entries, with a 20-round magazine, a five-tick minimum fire interval, and the shared Native Gun firing, tooltip, recoil, HUD, animation, and maintenance systems.

Gameplay data is isolated in `data/apocalypse_firstlight/native_guns/cat.json`: 13 base damage, falloff from 20 blocks, 40-block effective range, 64-block maximum range, 0.55 minimum damage multiplier, 0.25-degree base spread, six-block noise radius, and no tinnitus. It uses the existing `default` accuracy profile because it is the less punitive stable-rifle profile. Recoil is 0.28-0.42 degrees vertical and -0.06 to +0.06 degrees horizontal per shot, capped at 2.0 vertical and 0.35 horizontal.

The authored 3.08333-second reload is exposed as both `reload_tactical` and `reload_empty`; both lock for 62 ticks. No reliable insertion cue is authored, so `mag_in_tick` and `empty_mag_in_tick` currently use a conservative temporary value of 55 and require visual calibration. ADS uses the source `camera` reference with a first-pass per-gun profile, 0.18-second transition, and 0.94 FOV multiplier; exact sight alignment remains manual QA. HUD and inventory artwork use the supplied `cat_hud.png` and `cat_inventory.png`; HUD dimensions are data-driven at 60x20 GUI pixels and still require visual QA.

The converted editable source is `src/main/blockbench/C.A.T.bbmodel`; runtime geometry is `geometry.cat`. The real `right_hand_anchor`, `left_hand_anchor`, and `muzzle_anchor` are wired. The asset has no sight, magazine replacement, or ejection anchor, so no attachment slots are declared. Visual casing ejection is disabled generically by leaving the Profile ejection anchor unmatched while retaining the required 7.62x51mm casing definition. Idle audio files are retained under `src/main/blockbench/audio/cat/` but are not registered or played in V1.

The source-only `lefthand_pos` and `righthand_pos` proxy cubes and the hidden `camera` reference cube are absent from runtime geometry. Their locator bones remain available to animations and ADS data, but reference geometry is not rendered in third person, inventory, ground, or maintenance views.

Verification for this integration is limited to `compileJava` and `processResources`; no graphical client validation is claimed.
