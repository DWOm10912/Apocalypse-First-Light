# AFL Service Pistol V0.2 — model review

Current status: this is a historical art review. During V0.4 cleanup, the old
Service Pistol sources and pre-material PNG below were removed from the editable
folder after a hash-verified archive was made at
`.gradle-user/asset-backups/service-pistol-pre-v038-20260906.zip` (local, ignored).
Only `src/main/blockbench/service_pistol_v03_8_fire_slide_cleanup.bbmodel` remains
as the current pistol source. Earlier preservation/no-runtime statements describe
their historical stages, not today's workspace. Runtime status is documented in
`docs/dev/native-afl-gun-framework-v0.md`.

Date: 2026-09-06. Static editable asset only. No runtime integration or gameplay.

## Reference and authorship

The user replaced the inaccessible FBX prerequisite with read-only reference to
`run/tacz/tacz_default_gun/assets/tacz/geo_models/gun/glock_17_geo.json`.
That file was accessible. Its component/detail counts and coarse geometric extents
were inspected, without importing it into Blockbench. Its untransformed extents
include differently oriented components and are not asserted as assembled dimensions.
First-person appearance cannot be established from that geometry JSON alone and
was not verified in game.

The V0.2 cubes, pivots, hierarchy and material atlas were authored independently
through Blockbench MCP, in a new project. No reference geometry, UV, texture,
animation or pivot values were transferred. The earlier AFL model was not used as
the geometric starting point.

## Source and parameters

- Source: `src/main/blockbench/service_pistol_v02.bbmodel`.
- Preserved original: `src/main/blockbench/service_pistol.bbmodel`.
- Exact backup: `src/main/blockbench/service_pistol_v01_failed.bbmodel`.
- Project: `AFL Service Pistol V0.2`.
- Format: `geckolib_model` / GeckoLib Animated Model, Model Type `Item`.
- Object ID: `service_pistol`; Mod ID: `apocalypse_firstlight`.
- Box UV; 67 cubes, 11 groups, no arbitrary meshes, no animations.
- Actual embedded PNG: **128 × 128**, verified from PNG IHDR as well as the live image.
- Texture name: `service_pistol.png`. It is embedded in the bbmodel and requires no
  external texture file. No standalone texture inspection copy is retained.
- Internal geometry and pivots were uniformly scaled by two during the UV pass to
  give the box UV layout more pixels; proportions were preserved. These are authoring
  units, not established Minecraft display transforms or physical dimensions.

## Gates

1. **Silhouette passed, self-review.** Built the long, low slide, 22-degree grip,
   frame, guard, barrel, magazine and basic sights first. Replaced disconnected
   guard corners with continuous faceted segments and joined the upper grip web.
   Gate 1 had 16 cubes. Reviewed six views in Blockbench.
2. **Geometry passed, self-review.** Split the slide shell around the right-side
   ejection port; added top/end chamfers, chamber hood, eight muzzle rim segments,
   restrained rear serrations, rail, grip shaping, magwell, controls and sight base.
   Gate 2 had 64 cubes. Reviewed six views. The final three small sight
   markers account for the final count of 67.
3. **Initial texture pass, superseded by the material correction below.** Independently painted low-saturation material
   regions: slide `#828e9c`, frame `#535b65`, steel `#a1abb5`, grip `#48515b`,
   controls `#697581`, magazine `#616c78`, recess `#38424e`, bore `#10161e`.
   Added one-pixel slide highlights, subdued grip stippling and light sight dots.
   Removed a shared-UV highlight that bled onto the slide roof. Same-material cubes
   intentionally share box-UV regions; this is not a unique per-cube unwrap.

## Bones and pivots

```text
root
└─ gun
   ├─ frame
   ├─ slide
   ├─ barrel
   ├─ magazine
   ├─ trigger
   ├─ front_sight
   ├─ rear_sight
   ├─ muzzle_anchor
   └─ sight_anchor
```

| Bone | Pivot (authoring units) | Purpose |
| --- | --- | --- |
| root / gun | [0, 8, 6] | Grip/recoil vicinity |
| frame | [0, 9, 7.4] | Static lower assembly |
| slide | [0, 14, 8] | Barrel-axis translation reserved |
| barrel | [0, 14, -2] | Chamber vicinity, future tilt reserved |
| magazine | [0, 8, 7.6] | Local rotation [-22, 0, 0], withdrawal along grip |
| trigger | [0, 9.5, 0] | Upper trigger pivot |
| front_sight | [0, 16, -19] | Independently positioned front sight |
| rear_sight | [0, 16, 12] | Independently positioned rear sight |
| muzzle_anchor | [0, 14, -22.4] | Empty bone at bore center |
| sight_anchor | [0, 17.1, -19] | Empty bone on sight-top line |

Muzzle points toward negative Z. Y is up. No animation has been implemented or
validated. Sights are direct children of `gun` as requested; any future slide
animation must explicitly account for their motion.

## Initial visual review (before material correction)

| View reviewed in Blockbench | Finding |
| --- | --- |
| Left | Low slide, rearward grip, continuous guard, small controls |
| Right | Ejection opening and chamber contrast, fine rear serrations |
| Top | Slim width, aligned sights, inset chamber region |
| Front | Eight-sided muzzle rim and dark bore; frame narrower than slide |
| Front three-quarter | Readable chamfers, barrel, port and material separation |
| Rear three-quarter | Rear notch, three sight dots, grip and magazine alignment |

The user subsequently requested no screenshots and no workspace screenshot files.
All 18 saved view images and the texture inspection copy were removed. No further
screenshots are requested or retained as deliverables.

Scores below are the earlier agent assessment, before the current material correction,
not a new visual acceptance of the corrected texture or runtime proof:

| Category | Score |
| --- | --- |
| Silhouette | 7.5 / 10 |
| Proportion | 7 / 10 |
| Geometry detail | 7 / 10 |
| Texture readability | 7 / 10 |
| Minecraft style fit | 8 / 10 |

The grip retains deliberately faceted shaping. Its former patterned stippling was
replaced during the material correction. Display scaling and in-game appearance
remain a separate, unperformed stage.

## Texture-only material correction — 2026-09-06

The user accepted the geometry and requested matte dark metal / black polymer
material relationships, using the supplied photograph only as a tonal reference.
No photo pixels, brand marks, inscriptions or TaCZ assets were used in this pass.

The existing embedded texture was repainted through Blockbench MCP. Geometry,
bone hierarchy, pivots, anchors, UV coordinates and texture UUID bindings were
preserved. The current project was already open and was audited before editing.

| Surface | Current authored colors | Treatment |
| --- | --- | --- |
| Slide | #414448 / #4c4f53 / #53565a / #5c5f63 | Four principal levels: lower shade, main charcoal, slightly lighter roof, sparse one-pixel edge accents; no continuous bright band |
| Frame | #303336 | Low-contrast polymer, approximately 28/255 darker than the slide main color in weighted sRGB code-value brightness |
| Grip | #2c2f32, sparse #323538 and #272a2d | Deterministic irregular single-pixel flecks; low contrast, no repeating checker grid |
| Barrel | #5a5d61; hood #606367 | Approximately 14–20/255 brighter than slide main; dark steel rather than silver |
| Recess / bore | #1b1e22 / #0c0f12 | Bore remains the darkest region, no pure black |
| Magazine | #35383c, #2d3034, #3b3e42 | Subtle longitudinal differences; baseplate uses frame polymer |
| Controls | #3c3f42 | Restrained intermediate dark gray |
| Sight dots | #aeb0ac | Small gray markers; no emission or PBR |

Texture backup: `src/main/blockbench/service_pistol_v02_texture_before_material_pass.png`.
This is the exact original embedded PNG, saved only as the explicitly authorized
editable-texture backup, not as a preview image. The current texture remains
embedded in `src/main/blockbench/service_pistol_v02.bbmodel`.

Verification: actual PNG IHDR is 128 × 128 after saving; 67 cubes and 11 groups
remain. Saved `elements` (including geometry and UV) and animations compare exactly
with the baseline. Group/outliner differences are solely editor selection and
expanded/collapsed flags (`primary_selected`, `isOpen`); structural hierarchy and
pivots are unchanged. Live geometry/UV and group/pivot comparisons also match.

Left, right, top, front-three-quarter and rear-three-quarter views were displayed
in the current Blockbench viewport without screenshot calls. The final rendered
appearance is for the user's live visual review: color and binding checks do not
independently prove that the overall plastic appearance has been eliminated.
No screenshots, preview directories or texture preview copies were created in
this material-only pass. No geometry, Java, runtime, animation or gameplay changes.

## Verification and scope

- Parsed the saved bbmodel, verified embedded PNG dimensions, hierarchy and texture
  binding. Live audit found no missing texture bindings or out-of-range UV values.
- Old source retained, failed-version backup preserved, V0.2 saved separately.
- Visual review used Blockbench's preview renderer; screenshot files were removed
  following the user's updated instruction.
- No Java, Gradle, registry, runtime resource, TaCZ, gameplay or animation changes.
- No build/client run for this model-only task; no gameplay or first/third-person
  verification is claimed. No commit or push.
- Existing `.obsidian/workspace.json` changes are unrelated and were left alone.
- A concurrent change appeared in `src/main/resources/assets/apocalypse_firstlight/lang/zh_cn.json`;
  this task did not edit it. Repository-wide `git diff --check` reports trailing
  whitespace on its `poplar_leaves` entry, so the overall worktree check is not clean.

```text
AUDITED = YES
MCP USED = YES
REFERENCE ACCESS = YES
MODEL REBUILT FROM SCRATCH = YES
GATE 1 SILHOUETTE PASSED = YES
GATE 2 GEOMETRY PASSED = YES
GATE 3 TEXTURE PASSED = INITIAL PASS SUPERSEDED; MATERIAL VISUAL ACCEPTANCE PENDING
ACTUAL TEXTURE SIZE = 128x128
SLIDE SEPARATE = YES
BARREL SEPARATE = YES
MAGAZINE SEPARATE = YES
TRIGGER SEPARATE = YES
MODEL IS MEDIUM DETAIL = YES
VISUAL QUALITY ACCEPTABLE = GEOMETRY ACCEPTED; CURRENT MATERIAL VISUAL REVIEW PENDING
JAVA CHANGED = NO
GECKOLIB RUNTIME CHANGED = NO
GAMEPLAY CHANGED = NO
TACZ CHANGED = NO
COMMIT = NO
PUSH = NO
```

New files retained in this task: the failed-version backup, V0.2 bbmodel and this
text review. No screenshot or separate texture preview files are retained.
The material-only follow-up additionally retains the authorized original-texture
backup PNG described above.

## V0.3 animation-only successor (2026-09-06)

Animated source: `src/main/blockbench/service_pistol_v03_animated.bbmodel`.
The original `service_pistol_v02.bbmodel` remains unchanged and static; the
earlier V0.2 status blocks above describe that version, not this successor.

- `animation.service_pistol.fire`: once, 0.14 seconds. Slide travels +3.2 model
  units on Z, reaching maximum recoil at 0.040 seconds and returning at 0.105.
  Trigger pulls 8 degrees; gun rises 3.2 degrees with small rearward translation.
  All tracks return to neutral at 0.14 seconds. Front/rear sights and sight anchor
  receive the same slide translation without changing their existing parents.
  Magazine has no independent fire motion. Barrel remains independent and static.
- `animation.service_pistol.reload`: once, 1.2 seconds. Gun makes a restrained
  preparation/settle motion. Magazine starts withdrawing after 0.12 seconds,
  reaches 18 units of travel at 0.44, holds until 0.64, and seats at 0.97.
  Its parent-space direction is `[0, -cos(22 deg), sin(22 deg)]`, aligned with
  the existing tilted grip. No magazine rotation or teleport is used. Gun and
  magazine return exactly to neutral by 1.2 seconds.
- Keyframes use linear interpolation and 1000-step timeline snapping to preserve
  millisecond timing and avoid spline overshoot. Only these two animations exist.
- No hierarchy, bone pivot, texture or UV changes were introduced. All 67 cubes
  and 11 groups are retained. The live editor already contained an unsaved
  `extractor` offset before this task: its from/to/origin Z values were each +1
  compared with disk V0.2. V0.3 preserves that pre-existing edit; the animation
  pass does not revert it or apply further geometry changes.

Verification: live evaluated transforms confirm upward fire recoil, rearward
trigger pull, synchronized sights, unchanged local magazine pose during fire,
and neutral start/end poses. At reload maximum withdrawal, projection along the
extraction axis separates magazine bounds from the checked grip/magwell bounds
by approximately 4.13 model units. This is a clearance check, not an exhaustive
collision or visual-quality certification. No screenshots were taken. Final
visual timing and all-angle intersection review remain for the user in Blockbench.

The GeckoLib export action is available and its animation compiler successfully
built both named animations in memory with `geckolib_format_version: 2`.
No external animation JSON was exported and no runtime assets were added.
An earlier codec-list-only availability assessment is not applicable to this
verified live export action. Runtime playback/import compatibility is untested.
No Java, gameplay, renderer, sound, effects, TaCZ, client launch, commit or push.

## Subsequent rig sources

The simple animation successor and V0.3.2 reference source are preserved.
The right-hand-approved checkpoint is
`src/main/blockbench/service_pistol_v03_3_ready_pose_fix.bbmodel`.
The latest saved authoring source is
`src/main/blockbench/service_pistol_v03_8_fire_slide_cleanup.bbmodel`.
It freezes the accepted V0.3.7 Ready Pose, Reload, display settings and cube data.
Only Fire barrel linkage and the port-shadow parent are corrected; duration is
still 0.14 seconds and slide peak travel is still 3.2 units. The user accepted
the Blockbench fire visual result. V0.3.7 remains an unchanged rollback point.
Source/editor checks are not native-runtime or in-game validation.
See `docs/dev/service_pistol_v03_rig_review.md` for the two-hand reload, magwell,
pose/reference-arm revisions and verification limits, and
`docs/dev/afl_weapon_art_standard_v1.md` for the reusable authoring convention.
These are source assets only; no native gun runtime integration is implemented.
