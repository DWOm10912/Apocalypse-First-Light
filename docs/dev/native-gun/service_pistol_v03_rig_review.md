# Service Pistol V0.3 rig and reference-arm review

Current status: historical authoring checkpoints below have been archived during
V0.4 cleanup, not retained as loose model files. Eleven old bbmodels and the old
pre-material PNG are recoverable from the hash-verified local ignored archive
`.gradle-user/asset-backups/service-pistol-pre-v038-20260906.zip`.
The sole current pistol source is V0.3.8; the reusable rig template is retained.
V0.3.8 SHA-256: `b4d77e47a5e3b481c21e56064e9a10d44a771103bfbe66979f2ceea4c5195391`.
Historical no-runtime statements below do not describe V0.4. See
`docs/dev/native-gun/native-afl-gun-framework-v0.md` for current runtime integration.

Date: 2026-09-06. Blockbench assets only. No Java/gameplay/runtime changes.

## Preserved version chain

1. `src/main/blockbench/service_pistol_v03_animated.bbmodel`: input simple fire/reload.
2. `src/main/blockbench/service_pistol_v03_two_hand_rig.bbmodel`: 83 cubes, 18 groups;
   hollow magwell, second magazine, two-hand debug reload. Preserved safety copy.
3. `src/main/blockbench/service_pistol_v03_1_realistic_two_hand_pose.bbmodel`:
   86 cubes, 22 groups; asymmetric wrap-shaped palms and separately rotated
   forearms. Preserved intermediate pose source, not a final visual acceptance.
4. `src/main/blockbench/service_pistol_v03_2_mc_arm_reference_rig.bbmodel`:
   Reference-standard baseline, 81 cubes, 18 groups, two textures and exactly two animations.
   Debug geometry is replaced with two fixed Classic reference arms.

The original V0.2 and simple animated V0.3 files were not overwritten. Their
SHA-256 hashes remained respectively
`32c47c90272fd4d3f619fba7ba87ceecbcf4bb92d9c819e8f1c580877f4d1c54` and
`482991608a16e3c1da25ea3c2b9fa1866b14fb916340b5915cab29d222e3f2bf`.

## Magwell and weapon protection

Only three original cubes were changed for the initial magwell work:
`grip_primary`, `magwell_lip`, `grip_upper_web`.

- Grip: `grip_left_shell`, `grip_right_shell`, `grip_front_shell`, `grip_back_shell`.
- Lip: four separate left/right/front/back border cubes instead of a solid plug.
- Upper web: left/right/front/roof pieces, with obstructing internal rear material
  omitted. Existing frame silhouette, slide, barrel, sights and grip angle retained.
- `magwell_inner_dark`: small deep dark cap using the existing gun texture.
- In grip-axis coordinates, the cavity spans X -2.05..2.05 and Z 4.62..10.68.
  Grip shells retain the original -22-degree rotation and original outer bounds.

V0.3.1 and V0.3.2 do not change gun or magwell geometry. The exact original gun
texture remains embedded at 128 x 128. No third-party assets were copied.

## Rig and reload

`weapon_root` was inserted between root and gun at `[0,8,6]`, preserving the
neutral weapon transform. Both hand anchors are its children. Ejection anchor is
under slide; muzzle/sight anchors and independent barrel/trigger remain.

`reload_magazine` duplicates only AFL's own magazine cubes and UV. Its unanimated
source pose is parked 64 units below the seated magazine. Both fire and reload
use scale-zero keys to hide it. Blockbench evaluates zero scale as 0.00001;
off-view parking also prevents a normally seated duplicate in the unanimated pose.
Visibility keys use step interpolation, not gradual shrinking.

`animation.service_pistol.reload` is once, **1.30 seconds**:

| Time | Action |
| --- | --- |
| 0.00 | New ready pose, installed magazine shown, replacement hidden |
| 0.10-0.22 | Restrained weapon tilt; support hand detaches |
| 0.22-0.38 | Old magazine withdraws 18 units along grip axis |
| 0.48 | Old magazine hidden after further exit |
| 0.50-0.55 | Left hand supports replacement magazine from below |
| 0.62 / 0.72 / 0.80 | Remaining insertion travel: 12 / 6 / 0.7 units |
| 0.84 | Replacement seated; slight weapon-root seating bump |
| 0.86 | Step visibility swap to installed magazine |
| 0.88-1.06 | Support hand withdraws outward, then returns |
| 1.18-1.30 | Hands and weapon at ready pose |

Magazine translation axis is `[0,-cos(22 degrees),sin(22 degrees)]`, not world
vertical. New and installed magazine geometry matched exactly in center and
extents at the swap pose. Left-hand position tracks share the visible magazine's
travel during insertion, keeping their relative position constant. Pose/reference
revisions compensate only the left-hand track; weapon/magazine timing is unchanged.

`animation.service_pistol.fire` remains once, **0.14 seconds**. All original
effective keys were retained, with gun recoil retargeted to weapon_root so hands
follow. Slide reaches +3.2 Z at 0.040 seconds; trigger pulls 8 degrees; root rises
3.2 degrees. Magazine has no independent fire motion. Parts reset by the end.

## Final reference-arm pose

Both cubes are exactly **4 x 12 x 4**, with no stretch or scale animation.
Reference groups and cubes are marked `export: false`, separate from gun geometry.
See `docs/dev/native-gun/afl_weapon_art_standard_v1.md` for reuse/runtime boundaries.

Final hand-anchor authoring origins:

- Right: `[0.6,8.4,14.9]`, high and rearward.
- Left: `[-3.3,7.4,13.5]`, one unit lower and 1.4 units forward.
- Both retain the -22-degree base X rotation; separately rotated reference groups
  point toward different lower/rear body directions. They are not mirrored.
- Reference geometry is shoulder-pivoted with a fixed 12-unit span ending at the
  hand anchor. Ready-pose hand ends have an intentional overlap region; no fingers
  or anatomical palm geometry are part of the final standard.

Left-hand carry placement was minimally compensated for the standard cube's
distal end. No arm length/scale, gun scale, display transform, magwell, magazine
logic or fire keys were changed in V0.3.2. Weapon-scale review remains required.

## Verification and limits

- Reload actually played in Blockbench from zero through 1.2966 seconds, then
  completed and returned to zero. Required key poses were also evaluated numerically.
- Sampled reload every 0.01 seconds. Fixed arm dimensions stayed within floating
  point tolerance (maximum error about 3.6e-15 units).
- Oriented-box checks found no visible magazine/frame penetration after magwell
  correction and no reference-arm/gun penetration deeper than 0.08 units at the
  sampled poses. The small tolerance permits contact; this is not exhaustive
  triangle/continuous collision proof or a claim of no overlap anywhere.
- Fire effective keys, recoil direction, slide motion, hand parent-following,
  start/end restoration and original weapon/texture preservation were checked.
- Natural appearance, all-angle hand overlap and a human-looking ready pose are
  **pending user visual acceptance**. Numeric geometry and playback do not prove
  these aesthetic outcomes. No in-game testing or runtime renderer is claimed.
- Screenshot exception: during V0.3.1, the camera-angle MCP tool unexpectedly
  returned one viewport image. No image file was saved; the camera tool was then
  discontinued. No screenshot/preview/contact-sheet files were created. It would
  be incorrect to claim zero image captures for the combined task.
- A minimal reusable template was created: two cubes, seven groups, one embedded
  reference texture, no gun geometry or animations. Final pistol project restored.
- No external runtime JSON, Java, gameplay, TaCZ, client launch, commit or push.

Existing `.obsidian/workspace.json` and `lang/zh_cn.json` changes were left alone.

## V0.3.3 approved right hand / V0.3.4 left-hand candidate

The preceding pose checks describe older source revisions, not acceptance of the
latest two-hand pose. Earlier anchor-only checks did not establish actual arm-end
contact: a rotation-order/local-offset mismatch left the visible hand end displaced.
The current model uses ZYX rotation order. Reference offsets are now calculated
against the evaluated orientation, with actual distal-end positions checked.

User-approved right-hand checkpoint, saved without overwriting V0.3.2:
`src/main/blockbench/service_pistol_v03_3_ready_pose_fix.bbmodel`.
The right anchor is `[1.65,8.6,13.9]`; the arm extends toward the right/rear/down
with world direction approximately `[0.480,-0.230,0.847]`. Its actual hand end
coincides with the anchor, contacting the upper backstrap. Left reference remains
hidden in this safety checkpoint. This is right-hand approval only.

Historical V0.3.4 candidate, **left-hand visual acceptance pending at that stage**:
`src/main/blockbench/service_pistol_v03_4_left_support_pose.bbmodel`.
Only the left anchor/reference local transform and its visibility were changed
from the saved right-hand checkpoint. Right-hand group/cube data compare unchanged.

- Left anchor: `[-2.6,8.05,13.6]`, 0.55 units lower and 0.30 units forward of right.
- Actual hand end matches the anchor within floating point tolerance.
- Left arm direction: approximately `[-0.400,-0.290,0.869]`, not a right-hand mirror.
- Both reference cubes remain exactly 4 x 12 x 4 and `export: false`.
- Hand-end oriented boxes have a small overlap region. Left hand contacts the
  grip/backstrap; the maximum sampled static contact penetration is about 0.29
  units. This is not a claim of visually acceptable clipping or natural wrapping.
- Static checks find no left-arm intersection with slide or magazine/lip cubes.
- Every animation track remains unchanged, including reload middle keys, duration
  and magazine handoff. Zero-offset reload endpoints inherit the new ready pose.
  Changed rest transforms may affect intermediate hand placement; handoff review
  is explicitly deferred, not described as fixed.
- Fire peak still has +3.2 Z slide travel. Reference arms follow weapon_root and
  retain their dimensions. No gun geometry, texture, runtime or Java changes.
- No screenshots or preview files were created during V0.3.3/V0.3.4 work.

V0.3.4 checkpoint: user evaluates support-hand wrapping, visible overlap, contact
and natural convergence in Blockbench. V0.3.4 must not be declared visually
complete until that acceptance. The reusable template was not modified.

## V0.3.5 live contact pose and V0.3.6 clipping cleanup

V0.3.6 saved checkpoint (superseded by the V0.3.7 candidate below):
`src/main/blockbench/service_pistol_v03_6_pose_clipping_cleanup.bbmodel`.
Input was the live V0.3.5 hand-contact project, not a reloaded disk source.
At audit the left arm was still temporarily hidden and V0.3.5 was not on disk.
The left arm was restored to visibility without changing its pose. An attempted
V0.3.5 safety export was rejected by the safety reviewer to protect that path;
no V0.3.5 file was written. Only the requested V0.3.6 output was saved.

The user described the current overall ready composition as basically accepted.
This pass treats that live composition as the baseline and does not independently
claim a new visual acceptance, add hand-zone geometry or redesign either arm.

| Hand | Live input anchor | Cleanup anchor | Translation only |
| --- | --- | --- | --- |
| Right | [4.2, 9.1, 6.3] | [4.265, 9.1, 6.3] | +0.065 X |
| Left | [-2.6, 8.05, 13.6] | [-2.86, 8.05, 13.8] | -0.26 X, +0.20 Z |

Each reference group's origin and cube bounds/origin received the same translation
as its anchor, preserving the arm direction and exact hand-end attachment.
All group rotations, gun geometry, texture data and animation data compare
unchanged with the live input. Cubes remain 4 x 12 x 4, `export: false`.
There are still 81 cubes and 18 groups. No reference-arm scale keys were added.

Before cleanup, model-space oriented-box overlap measured approximately 0.055 units
between right arm and frame, and up to 0.291 units between left arm and grip shell.
No baseline hand/slide, hand/trigger-guard or hand/magazine-lip collision was found.
After the translations, no reference-arm intersections with frame, grip, slide,
barrel, trigger, guard or installed magazine were detected in the tested poses.
No arm/arm overlap remained in the ready pose.

Verification was performed in weapon_root-local space to exclude editor Display
scale and position from model measurements:

- Fire: 141 samples at 0.001-second intervals, including 0.040-second peak,
  through the unchanged 0.14-second duration; no tested arm/gun intersection.
- Reload: 0.00 and 1.30-second endpoints have no tested intersection.
- Fire/reload start and end reference transforms match the cleaned ready pose;
  weapon_root position and rotation both return to neutral.
- Arm dimension error stayed within 3.6e-15 authoring units.
- An oriented swept box for each magazine cube covers continuous travel from
  seated to 23 units out along the grip axis. It does not intersect either arm.
  This checks the shared axial insertion/withdrawal corridor, not all possible
  future hand trajectories.
- SAT contact tolerance is 0.0001 model units. These are geometric/numeric checks,
  not image-based acceptance or proof of arbitrary continuous animation collisions.
- Reload middle keys, magazine timing/handoff, fire rhythm, recoil and trigger
  keys are unchanged. Existing reload middle/handoff issues remain out of scope.

During this pass the user edited Display settings in the live UI. Those changes
were preserved, not reverted or authored by the assistant. Saved first-person
translations are right `[-6,1,0]` and left `[-3,1,0]`, each with scale
`[0.3,0.3,0.3]`; at task start both translations were `[-9,1,0]`.
Third-person and other display entries were not changed by the assistant.

No screenshots, previews, external runtime JSON, Java/gameplay/TaCZ changes,
client launch, commit or push. No reusable-template change. The user can review
the cleaned pose in Display / First Person Right Hand without changing its values.

## V0.3.7 multi-fix candidate

V0.3.7 checkpoint (superseded by the fire-only V0.3.8 below):
`src/main/blockbench/service_pistol_v03_7_multi_fix_pass.bbmodel`.
Started from the live V0.3.6 project and retained all earlier files. The saved
V0.3.6 SHA-256 is
`03712a1f38527ed092e5274f473aff0652b77f35c861554b59daa631782c8177`.
This section supersedes the earlier deferred reload-middle/handoff status.
The user accepted the new left support/contact and converging forearms in the
restored first-person right-hand ready view. Dynamic fire/reload visual acceptance
and separate left/right 3/4 checks remain pending.

### Ready pose and preservation

- Right anchor `[4.265,9.1,6.3]`, right reference geometry and both arm directions
  remain unchanged from live input.
- Left anchor moves from `[-2.86,8.05,13.8]` to `[-4.995,8,4.9]`, toward the
  front-grip/trigger-guard transition. Its reference pivot and cube receive the
  same translation; the actual distal end remains attached to the anchor.
- Both arms remain 4 x 12 x 4 with group/cube `export: false`. No texture bytes,
  UVs or material assignments changed.
- Existing display values are preserved, including right first-person
  translation `[-6,1,0]`, scale `[0.3,0.3,0.3]`. The live ground translation
  `[0,2.25,0]` was already present at audit and was not reverted.
- All 18 named groups remain. There are now 79 cubes: only the two duplicate
  helper cubes, `reload_magazine_body` and `reload_magazine_baseplate`, were
  removed from this new version. Their original geometry remains in V0.3.6.
- Gun cube changes are limited to `port_shadow.to.x`: 2.32 -> 1.83, placing the
  dark port insert just inside the slide sidewall rather than through it.
- `muzzle_anchor`, `sight_anchor` and `ejection_anchor` are unchanged.

### Reload: 1.30 seconds, single visible mesh

The live input's replacement-magazine scale keys had become linear, causing
premature gradual appearance. Merely parking a second mesh below the gun also
left duplicate geometry in the unanimated model. V0.3.7 instead reuses the single
`magazine` mesh for old/new magazine roles. `reload_magazine` is retained as an
empty, hidden trajectory guide at pivot `[0,8,7.6]`, rotation `[-22,0,0]`, with
matching reload position keys. It has no renderable geometry in ready, fire or
reload; no idle animation or runtime visibility mechanism was added.

- 0.00-0.22: left support releases and reaches the magazine base.
- 0.22-0.48: withdrawal along `[0,-cos(22deg),sin(22deg)]`, reaching 24 units.
- 0.48-0.52: carry out to 26 units and X=-3, fully clear before hiding.
- 0.52-0.60: hidden exchange interval; the same mesh resets to the incoming role
  at 24 units out and X=-3. No second visible magazine exists.
- 0.60-0.66: left hand carries the incoming mag onto the insertion axis, still
  fully below the magwell. Then 12 units out at 0.75, 5 at 0.83, 0.7 at 0.90.
- 0.93: fully seated; hand remains in contact until 0.98.
- 0.98-1.23: left hand releases, clears the frame laterally and returns to ready.
  Root settle is retimed to finish at 1.23; neutral pose held through 1.30.

Magazine scale keys use `step`: 1 at 0, 0 at 0.52, 1 at 0.60, 1 at 1.30.
Blockbench clamps evaluated zero scale to 0.00001; this is the editor's numerical
epsilon, not a second magazine. The hidden exchange is a source-only animation
simplification, not a modeled physical inventory transfer or gameplay event.

The left hand's carried-base contact uses reference-arm corner geometry, not
anchor distance alone. Its seated hand-end target is approximately
`[-5.097114,-5.362181,12.609105]`, with a 0.005-unit clearance at the magazine's
left baseplate side. During removal/insertion it shares the magazine's evaluated
translation, keeping the contact offset fixed. Arms are rigid MC reference blocks,
not finger/palm geometry; convincing wrapping still requires user inspection.

### Fire: 0.14 seconds

The original root recoil, trigger, slide and sight tracks are retained. Slide
maximum is still +3.2 Z at 0.040 seconds. Barrel pivot changes from `[0,14,-2]`
to `[0,14,-21.8]` without changing the rest geometry. Added barrel unlock keys:
2.8 degrees X and +0.25 Z at 0.012, +0.35 Z at 0.040/0.058, +0.25 Z at 0.090,
and zero position/rotation at 0.105/0.140. This lowers the chamber under the
moving forward roof; it is simplified visual feedback, not mechanical simulation.

### Verification and limits

- Both native timelines were actually played once at normal speed, with sampled
  progressing times, automatic completion and return to zero recorded.
- Reload evaluated at 1,301 samples, 0.001-second spacing: no tested arm/gun,
  arm/arm or visible magazine/frame OBB intersections; no fractional visible
  scale frames. Hand/baseplate contact offset error during carry was below
  2.5e-14 units; maximum arm dimension error below 5.4e-15 units.
- Reload end reference centers return exactly to the ready pose. Boundary checks
  around 0.52 and 0.60 confirm the step visibility exchange.
- Fire evaluated at 141 samples: no tested arm/gun intersections. The identified
  chamber/port versus forward roof, right wall and right bevel intersections are
  absent after the unlock and inset changes.
- These tests use weapon-root-local oriented boxes, excluding Display scale.
  Existing barrel/slide housing overlaps remain; this is not a claim of globally
  disjoint geometry, exhaustive continuous collision proof or visual approval.
- First-person right-hand ready framing is restored; the user answered yes to
  left front-grip support and natural forearm convergence in that view. This does
  not establish normal-speed dynamic visual readability or left/right 3/4
  acceptance, which remain pending. No screenshots or preview images/files
  were created.
- Saved source only; no external animation/geometry JSON was exported in this
  pass. No Java, runtime, gameplay, TaCZ, client launch, dependencies, commit or push.
  The reusable template is unchanged.

## V0.3.8 fire slide cleanup

Source: `src/main/blockbench/service_pistol_v03_7_multi_fix_pass.bbmodel`.
Output: `src/main/blockbench/service_pistol_v03_8_fire_slide_cleanup.bbmodel`.
The V0.3.8 request explicitly accepts and freezes the V0.3.7 Ready Pose and Reload.
V0.3.7 remains unchanged; its SHA-256 is
`d6f0fe075ee713a85e117f2d07fbb6b985faf6ff9e9b6b2c8bca980180b7ce7e`.

### Audit and root cause

Fire remains 0.14 seconds with 59 keyframes. Slide movement is strictly +Z,
maximum 3.2 authoring units at 0.040 seconds. Root recoil, trigger and barrel
already participate. `ejection_anchor` is a slide child at `[2.85,14.9,-0.2]`.
Both hand anchors are weapon_root children, with rigid reference-arm following.

Start, 0.012, 0.040 peak, 0.075 return, 0.105 and 0.140 checks distinguish
internal housing intersections from visible-region risks. No slide/body-frame
major intersection was found. The existing front cap/guide-tip overlap occurs
in the closed pose and is not evidence of a slide travelling through the frame.
The previous broad SMALL_FIRE_CLIPPING flag included internal intersections;
it did not establish that every such overlap was a visible defect.

Two fire-only issues were addressed:

1. The 2.8-degree barrel tilt drove the rear barrel into `frame_primary` by about
   0.0947 units. The joint was retuned to keep clearance both above the frame
   and below the forward slide roof, without changing body geometry.
2. `port_shadow` was a barrel child, although it decorates the slide opening.
   It therefore descended with the barrel and intersected the slide lower web
   instead of retaining its clearance within the moving ejection port.

### Exact changes and frozen data

- Only four nonzero barrel rotation keys change X from 2.8 to 1.5 degrees
  (0.012, 0.040, 0.058, 0.090 seconds).
- At those same four barrel position keys, Y changes from 0 to -0.37 units.
  Existing Z linkage remains +0.25/+0.35/+0.35/+0.25. At 0, 0.105 and 0.140,
  barrel position and rotation remain neutral. Pivot is unchanged.
- `port_shadow` is reparented from `barrel` to `slide`, preserving its exact
  rest transform, cube dimensions, faces, UVs and texture. No decorative cube
  resize or vertex edit was needed. No parent of an anchor changes.
- Slide, front/rear sight, sight-anchor, weapon-root and trigger tracks are
  unchanged. Fire duration and key count are unchanged; no recoil redesign.
- All 79 cube records, all 18 group records, texture data and display data remain
  unchanged compared with live V0.3.7. The only outliner edit is the shadow parent.
- Reload animation data, timing, magazine scale/handoff, helper, hand-contact
  tracks and Ready Pose are frozen. Since barrel and slide are both static
  relative to gun during reload, the shadow's parent change does not change its
  reload trajectory. No magazine, magwell, grip, hands or sights were remodeled.
- Ejection anchor remains `[2.85,14.9,-0.2]` under slide; muzzle anchor unchanged.

### Validation and acceptance

- 1,401 fire samples at 0.0001-second spacing in weapon-root-local coordinates:
  no tested barrel-primary/frame-primary intersection; no chamber/forward-roof,
  chamber/right-wall or chamber/right-bevel intersection; no shadow intersection
  with any tested frame, slide or barrel cube.
- No tested reference-arm/gun intersection. Maximum hand-center drift relative
  to weapon_root is below 7.2e-15 units; end-state error is zero. Slide X/Y drift
  is zero and Z stays within [0,3.2], with no added overshoot or discontinuity.
- The user's live visual review answered "no problem" to slide motion, barrel
  linkage, port cleanliness and absence of obvious clipping/flicker. This is
  Blockbench visual acceptance, not native-runtime or in-game validation.
- An initial attempt to autoplay while in Display mode left the timeline at
  zero; it is not counted as completed playback. Native Animate-mode playback
  was then verified at speed 100: time advanced through 0.0244, 0.0496, 0.0879
  and 0.1273 seconds, then stopped and returned to zero. Display values were
  never altered. First-person right-hand ready view was restored afterwards.
- Internal barrel/slide housing overlap is deliberately retained under the
  task's allowance. Numeric tests are scoped, not proof of globally disjoint
  geometry or arbitrary continuous/render-driver behavior.
- No screenshots, preview files, external JSON export, Java/runtime/gameplay,
  TaCZ, client launch, dependencies, commit or push. Only source asset and its
  directly related review documents are changed.

