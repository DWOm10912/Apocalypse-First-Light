# HR55 Native Gun Integration

## Current behavior

`apocalypse_firstlight:hr55` is a JSON-defined, semi-automatic battle rifle using
`apocalypse_firstlight:12_7x55mm_round`. It has a 20-round magazine, emits
`apocalypse_firstlight:12_7x55mm_casing`, and uses the native gun system's normal
server-authoritative fire, reload, projectile, tooltip, HUD, maintenance, and attachment paths.

Its gameplay definition is `src/main/resources/data/apocalypse_firstlight/native_guns/hr55.json`:

- damage: 26 base, with falloff starting at 32 blocks and ending at 96 blocks;
- semi-auto interval: 5 ticks (240 RPM);
- tactical / empty reload: 3.88 / 4.29 seconds;
- ammo transfers at ticks 48 / 59 respectively, matching the visible magazine-settle phase;
- ADS: 0.25 seconds, 0.92 FOV multiplier;
- noise radius: 128 blocks, with tinnitus enabled.

## Visual and sound assets

The editable source is `src/main/blockbench/hr55.bbmodel`. Runtime files are the `hr55`
Geo/animation, texture, HUD, inventory image, and OGG resources below
`src/main/resources/assets/apocalypse_firstlight/`.

The runtime geometry is `geometry.hr55`. It retains the authored `right_hand_anchor`,
`left_hand_anchor`, `muzzle_pos`, `muzzle_anchor`, `shell`, and `sight_anchor` bones.
`muzzle_pos` uses a 3.55125-unit barrel-exit offset derived from the source's actual
muzzle-anchor separation.

The nine HR55 clips (`shoot`, `static_idle`, `reload_tactical`, `reload_empty`,
`inspect`, `inspect_empty`, `draw`, `put_away`, and `static_bolt_caught`) now give
`right_hand_anchor` one constant Position keyframe at time 0: `[0, -7, 0]`.
The prior Position baseline was the implicit `[0, 0, 0]`; this lowers the
right-hand locator without changing its pivot, reference-arm geometry, or the
existing parent animation keys. `reload_empty` still animates `righthand_pos`,
so the local offset is not a guaranteed rigid screen-space translation.
The editable `.bbmodel` and runtime `hr55.animation.json` carry the same offset.
First-person visual acceptance remains pending an in-game check.

Iron-sight ADS centers the authored front sight-ring window at `[0, 11.52334, -4.5]`.
This is the center of the visible circular window, rather than the rear U-notch, so the
ADS composition matches the BR51-style sight picture. The sight mount uses the same aim
point. This is visual-only and does not alter hitscan or projectile direction.

The HR55 accepts `rifle_red_dot_01` through its sight slot. It deliberately has no
`muzzle_slot` in V1: no suppressor attachment is currently compatible. The supplied
`hr55_fire_suppressed` event and audio are nevertheless registered now for later use;
they cannot be selected until a HR55-compatible muzzle attachment is authored and enabled.

The `shoot` clip has no animation sound marker. Successful firing plays the authoritative
`hr55_fire` event once through `NativeGunActions`; reload, inspect, draw, and put-away
markers are normalized to registered `apocalypse_firstlight:hr55_*` events.

## Verification boundary

Build and resource validation are recorded with the implementation change. First-person
ADS sight-axis and hand/third-person composition remain visual client checks and must not
be considered confirmed until inspected in a development client.
