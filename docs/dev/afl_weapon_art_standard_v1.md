# AFL Weapon Art Standard V1

Date: 2026-09-06. Authoring convention, not an implemented runtime renderer.

## Source ownership and hierarchy

Editable sources belong under `src/main/blockbench/`, separate from runtime JSON.
The reusable template is
`src/main/blockbench/templates/afl_weapon_rig_template.bbmodel`.
It contains two cubes, seven groups, one embedded reference texture, no weapon
geometry and no animations. The Service Pistol exemplar is
`src/main/blockbench/service_pistol_v03_8_fire_slide_cleanup.bbmodel`.
Older pistol revisions were archived during V0.4 cleanup. The reusable template
is still source-only; reference arms are never gun geometry. V0.4.1 renders the
player's own skin-model arms from the exported animated hand anchors. Runtime
scope and export verification are tracked in `docs/dev/native-afl-gun-framework-v0.md`.

```text
root
  weapon_root
    gun
      weapon-specific parts and anchors
    right_hand_anchor
      right_arm_reference
        right_arm_reference_cube
    left_hand_anchor
      left_arm_reference
        left_arm_reference_cube
```

- `weapon_root` controls shared visual recoil and reload tilt.
- Gun parts animate independently inside `gun`.
- Hand anchors identify grip/wrist positions, not shoulders. The Service Pistol
  runtime hand layer consumes their animated transforms. V0.4.2 adds local palm
  contact/orientation calibration without moving the source anchors or arms.
- Reference groups/cubes are permanent source-only art/animation references.
  Both groups and cubes carry `export: false`; they remain visible and toggleable
  in Blockbench. Any future exporter/renderer must respect this distinction.
- In the pistol, `ejection_anchor` is under `slide`, at `[2.85,14.9,-0.2]` in
  authoring coordinates. Muzzle and sight anchors are retained.

## Fixed arm dimensions

Default Classic/Steve reference geometry is exactly **4 x 12 x 4** on the cube's
unrotated X/Y/Z axes. Never stretch, shorten or scale-animate it to fit a weapon.
The cube/reference-group pivot is at its shoulder end, with the distal end
positioned at the hand anchor. Offset geometry and group rotation make it extend
toward the body while the author manipulates the hand anchor.

Runtime support selects Classic **4 x 12 x 4** or Slim **3 x 12 x 4** from the
actual PlayerRenderer and uses the player's skin. The source reference remains
Classic; runtime does not render the reference cubes themselves. V0.4.3 fixes
V0.4.2's inadvertent inheritance of gun display scale: the animated contact keeps
its camera-space position/rotation, while arm/sleeve scale is independent.
The unit-scale and subsequent 0.55 pass did not establish an accepted Ready pose.
V0.4.4 temporarily restores the exact V0.4.2 effective hand mapping (independent
visual scale 0.30) on detached arm matrices; further arm calibration is deferred.
Unscaled Classic/Slim geometry and the source reference sizes remain standard.
Forearms extend toward the lower viewport; geometry is not shortened or clipped
by custom code. Original skin sleeve settings and outer-layer inflation apply.

For pistols: right-hand high/rear dominant grip, left-hand slightly lower/forward
wrap support. For rifles/SMGs/shotguns: dominant hand at the grip and support hand
at the handguard/fore-end. Anchor poses may differ; reference-arm dimensions do not.
This standard is established for subsequent native AFL gun sources, not a claim
that all existing project weapons have already been migrated.

## Materials, scale and verification

Use an independent embedded reference texture, never the weapon's atlas.
The exemplar uses the existing 16 x 16 source-only neutral-color image renamed
`afl_arm_reference_source_only.png`. The gun's 128 x 128 atlas is unchanged.

Review weapon scale if the reference proportions look wrong; do not resize the
reference arms. Service Pistol uses earlier doubled authoring coordinates and
has a roughly 36-unit slide versus a 12-unit reference arm. **Weapon scale/display
proportion review is required**; neither gun scale nor display transforms were
changed in this pass. No first-person runtime scale is established by this rig.

Validate hand attachment, preserved lengths, magazine clearance, exact end poses,
and fire regression. Numeric checks and editor playback are not visual acceptance
or runtime verification. Do not automatically capture screenshots or save previews.
