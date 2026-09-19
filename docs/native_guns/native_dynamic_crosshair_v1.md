# Native Gun dynamic crosshair V1

Client presentation only. `NativeGunCrosshair` intercepts the vanilla CROSSHAIR Pre overlay only for a living non-spectator holding a Native Gun. First-person only; hidden with a screen/F1. Other items retain the existing overlay. The old center dot is replaced by four outlined off-white segments with an empty center. Existing diagonal white/red confirmed hit markers still render afterward, including during ADS. Full ADS (progress >= .999) hides the base crosshair as before. Native Gun HUD layout and fire-mode text are unchanged.

`NativeDynamicCrosshair` samples local horizontal displacement/velocity, using `NativeStanceAccuracy.classify`, `multiplier` and `recovery` as read-only pure helpers. It estimates base spread × stance multiplier with the existing 3/5 tick crouch/standing recovery. Teleports above 4 blocks do not become movement penalties. Speed, sprint, airborne, climbing and water also add visual-only gap. This is an approximate stability indicator, not a projected hitscan cone or a new accuracy calculation. Crouching may tighten below standing due to the actual accuracy profile.

Target gap = min(max_gap, base_gap + estimated spread degrees × spread_scale + movement_gap × min(speed / .15, 1) + applicable sprint/airborne/ladder/water additions). Gap follows the target exponentially with response_speed per second. Confirmed `NativeGunHud.shot(slot, gunId)` notifications add shot_impulse to a capped max_bloom, decaying exponentially with recovery_speed per second. Failed/dry-fire clicks do not trigger bloom. Displayed gap is capped by max_gap. The local player/level/selected slot/GeoItem ID bind presentation state; switching away resets it. There is no shared multiplayer timer and no network change.

## Visual resource

`src/main/resources/assets/apocalypse_firstlight/gui/layout/native_crosshair.json` is the only visual configuration. F3+T reloads it; development uses the existing safe source resource resolver, packaged clients use resources/resource packs. No config copy, writes, or HUD editor registration.

- Geometry in GUI pixels: line_length=4, thickness=1, base_gap=3, max_gap=24.
- Spread conversion: spread_scale=3 pixels/degree.
- Visual additions: movement_gap=1.5, sprint_gap=3, airborne_gap=3, ladder_gap=5, water_gap=5.
- Shooting: shot_impulse=2.5, max_bloom=8, recovery_speed=9, response_speed=14 (exponential rates per second).
- Style: color="#E8E8E2", alpha=.9, outline=true (one-pixel dark outline at 65% of line opacity), sprint_alpha=.45 multiplier. Line length/thickness stay fixed; only gap changes dynamically.

All fields are optional. Invalid types, non-finite/out-of-range numbers and malformed colors fall back per field; malformed JSON falls back as a whole. Parser bounds are authoritative. Center stays at the existing overlay center so hit markers remain aligned.

## Verification

Standalone `src/dev/tests/NativeCrosshairTest.java` covers defaults/invalid fields, finite bounds, valid override, confirmed impulse math, frame-rate independence, decay, caps and reset. Compile/resource checks do not verify visual feel. Manual testing: compare standing/crouching/walking/sprinting/jumping/climbing/swimming; fire repeatedly then stop; switch guns/items; ADS and shoot a target to check the retained hit marker; reload the JSON with F3+T. No runClient, clean, commit or push is part of this implementation. No hitscan, damage, actual recoil/spread, FireMode or gun data is modified.
