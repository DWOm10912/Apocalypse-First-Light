# Native AFL Gun Framework V0.4.4 — Service Pistol

## Scope and implementation

Minecraft 1.20.1 / Forge 47.4.22 / Java 17. Existing GeckoLib **4.7.4** and Maven
configuration are retained, not upgraded. TaCZ remains installed with unchanged
dependencies, compat, assets and behavior. Native pistol code has no TaCZ imports.

Item `apocalypse_firstlight:service_pistol` / Service Pistol / 制式手枪 is registered
in `AflItems` and appended to the AFL **Items** creative tab. Its development
tooltip is Native weapon system prototype / 原生武器系统测试版.

This is an infinite-ammo animation/audio prototype. There is no ammo item/count,
inventory consumption, durability, damage, hitscan, projectile, dry-fire, shell
ejection, ADS, attachments, suppressor, Noise or Tinnitus.
GeckoLib writes only its standard `GeckoLibID` render identity to a stack; no
custom gameplay NBT is added. Source-only reference arms are not runtime geometry.

## Source and export gate

Accepted source: `src/main/blockbench/service_pistol_v03_8_fire_slide_cleanup.bbmodel`.
V0.4 source SHA-256: `b4d77e47a5e3b481c21e56064e9a10d44a771103bfbe66979f2ceea4c5195391`.
During V0.4.1 the user saved a GUI-display edit and requested a fresh geo export.
The saved/live native exporter agreed; the new geo and animation objects exactly
match the existing runtime assets. No geometry rewrite was necessary. The user's
new source SHA-256 is `cf5191d92bfaf73e2b1876fb407864a08a2452bca38354038b376b1e78da2835`.
Codex does not edit the source. Its new GUI settings remain in the source and
in-hand export; the runtime GUI is independently routed to a static icon.
The user subsequently changed the source GUI pose again (SHA-256
`2dfacbe292aae026013bbde318f4aaa06bc18b4767ab27bb966af4910f359bb0`).
Non-GUI display and animation verification still pass. Blockbench MCP was then
unavailable, so a second native export of this latest snapshot is not claimed.
The unused GUI display on the in-hand JSON is intentionally not synchronized;
the accepted static icon and GUI routing remain frozen.

Actual Blockbench GeckoLib plugin 4.2.5 model/display export actions and the
plugin-patched `Animator.buildFile` compiled the full model, not hand-authored
replacement geometry. Compatibility normalization is applied to the outputs:

- Native output uses Bedrock 1.21.110 metadata, which GeckoLib 4.7.4 does not
  enumerate. This model uses only the supported cube/bone/per-face-UV subset;
  output metadata is normalized to 1.12.0 without modifying geometry.
- The current animation exporter rounds millisecond source times to 120 Hz and
  omits `step`. Each exported track is matched by bone/channel/key order against
  the source; vector values and coordinate conversion must agree, then exact
  source times are restored. There are 132 keys, two animations, with no lost
  endpoint after animation_length.
- Source step interpolation is encoded as `easing: afl_hold` on the incoming
  segment. `ServicePistolItem` registers this easing through GeckoLibUtil: hold
  start value until GeckoLib's segment-end branch selects the end value. The
  built-in subdivided `step` easing is deliberately not used. This retains the
  magazine's exact 0.52/0.60-second hide/reappear boundaries without partial scale.
- Runtime has 77 cubes, 16 bones. `right_arm_reference` and `left_arm_reference`
  are excluded; hand, muzzle, ejection and sight anchors remain. All named action
  bones remain, including the empty `reload_magazine` guide.
- The embedded 128×128 `service_pistol.png` is decoded byte-for-byte. No material
  or texture generation. Non-GUI runtime display parameters match the saved source.

Resources, relative to `src/main/resources/assets/apocalypse_firstlight/`:

- `geo/service_pistol.geo.json`
- `animations/service_pistol.animation.json`
- `textures/item/service_pistol.png`
- `models/item/service_pistol.json` (`forge:separate_transforms`, GUI routing)
- `models/item/service_pistol_in_hand.json` (`builtin/entity`, exported display settings)
- `textures/item/service_pistol_icon.png` (original 32x32 RGBA pixel artwork)
- `sounds/weapons/service_pistol/service_pistol_fire.ogg`
- `sounds/weapons/service_pistol/service_pistol_magazine_out.ogg`
- `sounds/weapons/service_pistol/service_pistol_magazine_in.ogg`

`tools/verify-service-pistol.ps1` checks source/export key fidelity, duration,
cube/reference counts, anchors, display and texture bytes. Optional
`-PrepareAssets -SoundSourceDirectory E:/Download` decodes the source PNG and
copies the three supplied OGG files; no images are rendered or captured.

`tools/export-service-pistol.blockbench.js` repeats the actual native export and
normalization from a matching saved/live V0.3.8 in Blockbench MCP. It returns
geo/animation/display objects for the above paths, refuses unsaved differences,
and never writes the source. Run the verifier after saving returned assets.
GeckoLib API reference: [GeckoLib4 triggerable animations](https://github.com/bernie-g/geckolib/wiki/Triggerable-Animations-%28Geckolib4%29);
the installed 4.7.4 source JAR was used to check the actual runtime API/parser.

## Input, state and synchronization

Implementation under `src/main/java/com/antaurora/apofirstlight/weapon/`:

- `ServicePistolItem.java`: GeoItem, synced instance, zero-transition triggerable
  controller `action`, `fire`/`reload`, prototype tooltip, no melee/block attack.
- `client/ServicePistolModel.java`, `client/ServicePistolRenderer.java`: GeoModel/
  GeoItemRenderer with the animated hand layer and V0.4.2 first-person-only
  presentation parent. Exported display JSON is not rewritten.
- `client/ServicePistolInput.java`: client-only `InteractionKeyMappingTriggered`
  cancels vanilla attack/swing for this item, sends once per press using an
  attack latch. R is a remappable IN_GAME KeyMapping, category AFL; consuming
  clicks plus held latch avoids OS key-repeat reload requests. GUI, focus,
  spectator and dead-player guards are applied.
- `ServicePistolActions.java`: server-side ephemeral player session, 3 tick fire
  minimum and 26 tick reload lock. R during an active action is ignored, including
  the short fire lock; fire during reload is ignored. Held stack change, dimension
  change, death or logout clears/cancels active interaction. No persistent state
  machine or ammo state. Server melee/block-break guards additionally reject
  ordinary attacks made while holding this prototype.

`network/AflNetwork.java` protocol is **10** (was 9). One direction-restricted
C2S packet carries reload/fire choice plus selected slot. Server validates slot,
main hand, life/spectator state and action lock. Existing S2C messages are unchanged.
Stack render identity is synchronized before GeckoLib's trigger packet.
`triggerAnim` uses GeckoLib's TRACKING_ENTITY_AND_SELF distribution; nearby player
animation visibility is supported by the API path but multiplayer visual testing
is still required. No double client-predicted trigger or duplicate local sound.

## Audio and authored timing

Only three sounds are copied/registered in `AflSounds` and `sounds.json`:

| Source in E:/Download | AFL event | Time |
| --- | --- | --- |
| 9mm_fire.ogg | service_pistol_fire | Immediately on accepted fire, once |
| 9mm_magazine_out.ogg | service_pistol_magazine_out | Reload tick 8 (0.40s), source fully clear at 0.42s |
| 9mm_magazine_in.ogg | service_pistol_magazine_in | Reload tick 19 (0.95s), source seated at 0.93s |

SoundSource.PLAYERS, volume/pitch 1.0, source at player position, server world
broadcast including shooter. Quantization error is 0.02s for each reload cue.
Each cue has an explicit once-per-session guard; changing held stack cancels
pending cues. Reload has no slide action, so no slide_action sound is played.
Suppressed/dry-fire/casing/slide files remain unused in the user's source folder.
Audible range is not infected Noise radius; no AFL Noise event is emitted here.

## V0.4 validation history

- Offline compileJava, processResources and build succeeded. Existing deprecation
  warnings remain; no dependency changes were necessary.
- Source/export verifier passed: 77 cubes, 16 bones, 132 exact keys, source PNG,
  display parameters, anchors and original OGG hashes.
- Repeating the native export script reproduced all three saved geo, animation
  and display objects exactly. The accepted V0.3.8 source SHA-256 is unchanged.
- Built `build/libs/apocalypse_firstlight-1.0.0.jar` contains the expected gun
  classes/resources and no DEV classes, copied `assets/tacz/`, or reference-arm
  geometry. Source bbmodel is separate from runtime assets.
- Graphical runClient started, OpenAL initialized, resource reload completed and
  the user entered the test world. The first manual pass crashed on cancellation:
  `run/crash-reports/crash-2026-09-06_14.29.13-server.txt`,
  StopTriggeredSingletonAnimPacket.encode -> FriendlyByteBuf.writeUtf(null).
  The new AFL cancellation path passed a null animation name; GeckoLib 4.7.4's
  packet encoder requires a non-null name despite the higher-level nullable API.
  This is an AFL integration defect, not a model or TaCZ defect.
- Fixed cancellation to use the shared explicit fire/reload name, and retained
  the item instance independently of the mutable stack so dropping its last item
  cannot cast AIR to ServicePistolItem. Server cancellation guards run at highest
  priority before other ordinary attack/break subscribers.
- DEV-only `NativeGunRuntimeSmokeCheck` checks the actual loaded GeckoLib model,
  animation cache, reference exclusion, anchors, registered hold easing and both
  stop-packet encodings after resource loading. It is excluded from the final JAR.
- The fixed graphical client logged `[AFL NATIVE GUN SMOKE] PASS` at 14:34:07
  on 2026-09-06. The final offline build succeeded in 26s; the final JAR was
  rechecked and contains zero DEV classes and zero `assets/tacz/` entries.
- After relaunch, the user answered "正常" to the explicit retest of firing or
  reloading while switching slots/dropping the pistol, GUI/first-/third-person
  rendering, gunshot/reload sounds and input locks. These user-tested items pass;
  the cancellation crash did not recur in that retest. No screenshots were taken.
- Creative-tab placement and semi-auto/vanilla-attack suppression are implemented
  and code-audited, but were not separately confirmed in that retest answer.
  Ground rendering, held-click non-repeat, ordinary melee/mining suppression,
  multiplayer visibility and TaCZ regression remain explicit manual checks.

## Source cleanup and recovery

Only the accepted V0.3.8 pistol remains as a loose source. Eleven obsolete pistol
bbmodels plus the pre-material-pass PNG were removed after every archived entry's
SHA-256 was checked. Recovery archive (local, ignored by Git):
`.gradle-user/asset-backups/service-pistol-pre-v038-20260906.zip`.
The rig template `src/main/blockbench/templates/afl_weapon_rig_template.bbmodel`
and non-pistol models remain. Older review files are retained as explicitly
historical records, not deleted or presented as current runtime documentation.

## V0.4.1 presentation implementation and verification

Historical first pass: its hand calibration and camera placement below are
superseded by V0.4.2. The GUI and third-person implementation remain current.

- **Static GUI**: Forge `separate_transforms` selects an unanimated `item_layers`
  2D icon for GUI/Hotbar/Inventory. All other contexts use the exported
  `builtin/entity` model. No recursive custom-renderer GUI call, screenshot,
  resampling, TaCZ icon or third-party image is involved. Reproducible native-pixel
  drawing source: `tools/draw-service-pistol-icon.ps1`.
- **First-person pass**: `ServicePistolFirstPerson` intercepts `RenderHandEvent`
  only while the main hand is this item, cancels both vanilla hand passes and
  renders the current main-hand stack once. It retains vanilla resting/equip
  translation and exported first-person transforms, but does not apply melee
  swing. This avoids rendering ItemInHandRenderer's stale pre-GeckoLibID stack
  during equip interpolation. Offhand items are visually suppressed while this
  two-handed gun is held; their inventory state and gameplay are not changed.
- **Skin arms**: `ServicePistolHandLayer` renders only the actual PlayerRenderer
  model's arm/sleeve parts using the current player's `getSkinTextureLocation()`.
  `getModelName()` selects Classic/Slim hand-center compensation; the actual
  PlayerRenderer supplies the corresponding 4px/3px arm mesh and vanilla skin UV.
  Sleeves honor player skin-part settings. Temporary part poses/visibility are
  restored in finally blocks. Invisible players do not render arms.
- **Anchor authority**: the per-bone layer inherits the full weapon_root and
  hand-anchor animated matrix, then translates to that bone's pivot. The accepted
  reference rig's local ZYX orientation calibrates the vanilla arm. Its hand-tip
  center lands exactly at the anchor for both Classic and Slim widths. No
  reference cube, reference texture, full player model copy or source offset is
  exported into gun geometry.
- **Third-person pose**: `ServicePistolPlayerPose` uses Forge's item-specific
  `IClientItemExtensions.getArmPose` / extensible two-handed ArmPose, inside normal
  HumanoidModel.setupAnim. Both arms raise and converge with head pitch/yaw;
  normal skin sleeves and held-item layers follow. Main hand and support roles
  mirror for left-dominant players. No mixin or world-player scan is needed.
  The third-person gun display transform is initially preserved pending visual
  alignment review. Multiplayer uses ordinary held-item/player-state visibility,
  but an observer-client test has not been performed.
- **Reload audit boundary**: exported 1.30s reload still contains weapon_root,
  magazine, reload_magazine and left_hand_anchor tracks. `reload_magazine` is an
  empty guide in the accepted source; the visible moving magazine is `magazine`.
  There is one triggerable action controller, with no idle loop overriding it.
  R/C2S/server trigger, 3/26-tick locks and all sound timing/volume/pitch/IDs/files
  remain unchanged. The stale-stack path was hardened; the later V0.4.2 audit
  identifies camera framing, not a lost animation, as the reload visibility cause.
- **Automated checks passed**: compileJava/processResources and offline build
  passed (final build 26s). The JAR's icon, routing model, in-hand model, geo and
  animation bytes match the source resources; no DEV classes or TaCZ assets are
  packaged. The 77-cube/16-bone/132-key/source-PNG/OGG verifier also passes.
  At 14:56:35 on 2026-09-06 the graphical client logged three smoke-test PASS
  results. The real reload controller reached 0.2443461 radians root rotation,
  24.106781 units magazine travel and 37.46896 units left-hand travel, and returned
  the magazine to ready. This rules out missing tracks and the default STOP
  predicate overriding a triggered reload. DEV-only tests
  check baked GUI-vs-3D routing, Classic/Slim vanilla geometry, anchor-tip mapping,
  raised-arm pose and real GeckoLib reload-controller evaluation. A bounded
  `NativeGunPresentationTrace` observes renderedId/heldId, controller and root/
  magazine/left-anchor transforms in the actual first-person pass. These classes
  are excluded from the release JAR. During the user's 14:59 in-world tests,
  first-person renderedId and heldId both remained 1. Repeated complete reloads
  showed root rotation around -0.2443 radians, magazine travel exceeding 22 units,
  left-anchor travel exceeding 35 units and subsequent zero return. The actual
  skin path was Slim. Static values after 15:00:02 followed a logged game pause,
  not a missing animation. Visual readability/skin contact/third-person alignment
  still require the user's feedback; this text trace is not visual acceptance.
  Manual in-world verification is pending;
  V0.4's prior broad acceptance is not reused as proof for this visual-fix pass.

## V0.4.2 first-person grip and reload presentation

The gun presentation is retained in V0.4.3; the arm-scale and local-direction
mapping below describes the historical V0.4.2 pass, superseded for arms only.

- **Cause**: V0.4.1 mapped both distal arm tips onto separated anchors with
  reference-rig orientations. Together with camera framing this exposed long,
  similar-looking arm columns rather than embedding the grip into the main palm.
  Animated anchor inheritance already worked; it was not a static-anchor bug.
- **Right hand**: unchanged animated anchor plus local pixel offset
  `(-1.55, +0.20, +0.75)`; vanilla arm palm center at local Y=8 (tip is Y=10).
  The two-pixel inset makes the grip intersect the palm region. Arm -Y aligns
  to normalized `(0.24, -0.69, 0.69)` within the anchor's animated frame.
- **Left hand**: local offset `(+2.45, -0.55, -1.65)`, palm Y=9.5;
  arm -Y aligns to normalized `(-0.24, -0.87, 0.44)`. This is a lower/front,
  shallower support contact, not a mirrored copy of the right-hand calibration.
  Classic/Slim center compensation remains; vanilla geometry/UV and source rig
  are unchanged. No geometry clipping or shortening is performed: orientation
  and camera composition move most of the forearm outside the lower viewport.
- **Ready**: camera-relative base translation changes from `(0.56,-0.52,-0.72)`
  to `(0.60,-0.54,-0.64)`; equip lowering remains `-0.6 * equipProgress`.
  The authored first-person translation/rotation and 0.3 scale remain unchanged.
- **Reload cause**: actual in-world controller and bone traces completed reload
  with matching stack IDs. At the old 70-degree held-camera framing, the entire
  magazine projected below the viewport during major extraction/insertion
  samples (top NDC Y=-1.202 at 0.32s, -2.320 at 0.48s). No renderer assignment
  was overwriting weapon_root; the motion existed outside the useful view.
- **Presentation**: after GeckoLib evaluates animation, the first-person root
  receives one extra parent transform, shared by gun and both animated hand
  layers. Weight uses smoothstep `t*t*(3-2*t)`: rise 0.00–0.24s, hold through
  0.85s, fall through 1.18s, then zero. At weight 1: camera translation
  `(+0.05,+0.28,-0.25)`, pivot `(0,8,6)/16`, rotations Z=-16°, Y=+32°, X=+8°
  in that order. Translation is divided by the existing display scale before
  applying. The authored root's roughly -14° roll combines to about -30°.
  Lift and pullback expose the long existing magazine travel; moving closer
  would worsen lower-screen clipping. No second magazine or hand timeline is
  authored. The empty reload_magazine guide remains empty.
- `ServicePistolAnimationController` reads clip time after the existing controller
  processing, using its tickOffset and speed; it adds no gameplay/network state.
  The hand layer continues reading each fully animated anchor transform.
  Fire has zero reload offset; all original animation bytes are retained.
- Third-person ArmPose/display and static icon/GUI routing are frozen. Audio
  files, 0.40/0.95s cues, volume and pitch are unchanged. No ADS or ammo system.
- Offline compileJava/processResources and build passed (build 31s).
  At 15:23:58 the graphical client passed Classic/Slim contact, actual reload
  controller, GUI routing and numeric projection tests (minimum magazine top
  NDC Y=-0.8021). Actual in-world reload traces showed the additional curve
  rising to 1 and returning to 0 while authored bone motion continued.
  **User rejected this first pass's direction: muzzle opened to the right.**
  The added yaw was corrected from -32 to +32 degrees: local barrel forward is
  -Z, so positive Y rotation turns its direction toward camera-left. All other
  Ready, hand calibration, reload translation/roll/pitch/timing are unchanged.
  A direction assertion was added. The corrected offline compileJava,
  processResources and build passed (39s), as did diff-check and packaged resource
  byte checks (DEV classes=0, TaCZ assets=0). The corrected graphical client
  started; at 15:28:35 direction, Classic/Slim contact, GUI routing and bounded
  curve checks passed, but the magazine extraction/insertion viewport assertion
  FAILED after the yaw reversal. That pass was not marked accepted at the time.
  The user's subsequent V0.4.3 brief explicitly accepts the gun Ready/Fire/Reload
  composition as basically usable and freezes it. The earlier -0.8021 result does
  not apply to +32° yaw: this known numeric frustum limitation remains recorded,
  now a diagnostic warning, not a reason to move the accepted gun in an arm task.

## V0.4.3 player arm scale and extension

The initial unit-scale attempt below was visually rejected. The explicitly
authorized independent-scale correction follows in the next subsection.

- **Scale cause confirmed**: the hand layer ran after the item JSON's 0.3 display
  scale. Vanilla arm geometry was correct but its full camera-space basis was
  still scaled: Classic effectively 1.2x3.6x1.2 pixels, Slim 0.9x3.6x1.2, instead
  of 4x12x4 and 3x12x4. No custom or shortened cuboid was used.
- **Old transform**: first-person base -> display translation/scale -> reload
  presentation -> animated root/hand anchor -> anchor pivot -> contact offset ->
  local arm rotation -> palm compensation -> vanilla arm/sleeve.
- **New transform**: same full hierarchy through contact offset to resolve the
  identical animated camera-space contact; in a pushed arm-only matrix, retain
  translation and orthonormalize the basis, preserving handedness. Then apply
  arm direction and palm compensation at unit scale. Lighting normals are updated
  to the rigid basis. No magic 3.33 multiplier or hard-coded inverse gun scale;
  no gun or anchor position is divided by scale. The shared gun pose is restored
  by popPose and no global camera/projection/depth setting changes.
- **Local directions**: right arm -Y aligns to normalized `(0.30,-0.85,-0.43)`,
  left to `(-0.35,-0.82,-0.42)` in the animated anchor frame. With the -22-degree
  bind rotation these point predominantly down/outward and slightly away from
  the camera. The old vectors pointed toward the camera and strongly foreshortened
  the already scaled arms. Complete emitted-vertex checks rule out actual near-
  plane truncation in the audited neutral-camera Ready/Fire/Reload poses: old
  right Ready sleeve closest Z=-0.31354; old full reload closest Z=-0.29643,
  both safely beyond Z=-0.05. The apparent short segment is consistent with
  shrunken geometry and camera-directed foreshortening, not missing faces.
  Looking-down/bob/hurt-camera visual behavior remains a manual check.
- Contact offsets remain right `(-1.55,+0.20,+0.75)`, left `(+2.45,-0.55,-1.65)`
  in authoring pixels. Palm Y remains 8/9.5 with Classic/Slim center compensation.
  Runtime rotation changes only arm extension, not the animated palm position.
- **Skin and sleeves**: actual PlayerRenderer rightArm/leftArm and rightSleeve/
  leftSleeve were already present; no missing sleeve was invented as a cause.
  Player skin ResourceLocation and sleeve-enable settings remain authoritative.
  Vanilla sleeves expand each face by 0.25 pixels. All six faces are rendered
  at standard depth, not always-on-top. Part pose, visibility, skipDraw and part
  scale are restored after rendering so the first-person pass cannot leak them
  into third person. No fixed Steve texture or reference-arm geometry.
- Frozen: gun base `(0.60,-0.54,-0.64)`, display scale 0.3, full +32-degree yaw
  reload curve, source/geo/animation/texture, third-person pose, icon/routes and
  all audio timing/files/volume/pitch. No gameplay scope expansion.
- DEV-only `NativeGunArmChecks` probes emitted vanilla base/sleeve vertices,
  dimensions, full faces, scale independence and unchanged contact positions.
  Reload/Fire controller sampling checks whole sleeves against the unchanged
  near plane. `NativeGunArmTrace` uses the actual per-bone first-person matrix
  and player skin, logging at most 120 samples per hand; it draws nothing and
  is excluded from the release JAR.
- **Verification, 2026-09-06**: offline compileJava, processResources and build
  passed (final build 39s); diff-check passed. All 14 frozen gun/source/audio
  files have unchanged SHA-256; release JAR contains the hand-layer update,
  zero DEV classes and zero TaCZ assets. Asset verifier passed 77 cubes, 16 bones,
  132 exact animation keys, source PNG and non-GUI display. Rechecking original
  E:/Download OGGs was unavailable because 9mm_magazine_out.ogg is no longer at
  that source path; project OGG signatures and pre-task hashes still match.
- At 15:44:45–46 the launched graphical client passed actual Classic/Slim base
  and sleeve geometry (24 emitted vertices each), unit basis for gun scales
  0.2/0.3/0.6/1.0, exact contact preservation, and complete sleeve near-plane
  checks. New right Ready closest Z=-0.35221; 301 reload samples closest
  Z=-0.23782; 41 fire samples closest Z=-0.33914; all are behind Z=-0.05.
  Fire recoil still peaks at 0.0558505 radians and returns to Ready.
  The frozen V0.4.2 magazine-top projection remains -1.0929165 and is explicitly
  logged as a known warning, not hidden or repaired by changing the gun.
  Manual arm thickness, perceived completeness, sleeves/skin appearance, grip
  and magazine non-occlusion await user review. Classic numerical geometry is
  verified, not yet a Classic-skin visual acceptance.
- The actual in-world V0.4.3 per-bone path subsequently logged Slim player skin
  `minecraft:textures/entity/player/slim/efe.png`, both sleeve settings enabled,
  24 base and 24 sleeve vertices per hand, and unit scale during Ready/Reload.
  Across the first 85 logged samples per hand, new closest Z was about -0.33
  (right) / -0.15 (left), with no near-plane intersection. This verifies the
  current Slim runtime route and skin selection, not a Classic visual result
  or a guarantee for every possible camera effect.
- **Manual acceptance FAILED**: the user supplied comparison images showing
  oversized AFL hands covering/intersecting the slide in Ready and obstructing
  the grip/magazine during Reload. Unit-scale and near-plane tests did not catch
  this contact-volume/occlusion failure and are not visual acceptance. Removing
  the inherited 0.3 scale increased the arm's linear size by about 3.33 while
  retaining palm-center mapping; preserving a mathematical contact point did not
  preserve a valid surface grip. The current arm pass must not be marked done.
  Further correction needs palm-surface clearance calibration. The user has now
  explicitly relaxed runtime scale=1.0 and authorized independent first-person
  arm visual scale while retaining vanilla geometry and frozen gun presentation.
  Gun transforms, all animations, source geometry, audio and TaCZ remain frozen.

### V0.4.3 correction: independent arm visual scale and palm-surface contact

Historical attempt, superseded by the V0.4.4 baseline restore below.

- User-authorized `ARM_VISUAL_SCALE = 0.55` is applied only after resolving the
  animated contact and removing inherited gun scale. It is a deliberate arm
  presentation parameter, not inverse compensation for the gun's 0.3 scale.
  Standard base geometry remains Classic 4x12x4 / Slim 3x12x4, with unchanged UVs
  and 0.25-pixel sleeve inflation. Visual dimensions are those values times 0.55;
  they are no longer reported as unit-scale runtime dimensions.
- Right palm contact offset becomes `(-1.55,-0.30,+1.10)` in anchor-local
  authoring pixels; left becomes `(+2.45,-0.70,-1.60)`. Right palm Y=9.65,
  left Y=9.85 (base distal tip Y=10), reducing the volume above the grip contact.
  Contact X is the vanilla arm center plus `-0.20 * width` for right and
  `+0.20 * width` for left, where width is 4/3 for Classic/Slim. The palm surface,
  not the cuboid center, meets the gun. Source anchors are not edited.
- Order: full animated gun hierarchy -> anchor/contact offset -> rigid contact
  basis -> retained arm local orientation -> arm visual scale -> palm-surface
  compensation -> original base/sleeve parts. Only the arm branch changes;
  contact positions still follow the actual animated anchors, not a fixed pose.
- DEV checks now assert 0.55 independent arm scale across incoming gun scales,
  exact calibrated contact mapping, standard unscaled geometry and complete
  faces. `NativeGunArmClearance` adds 15-axis OBB checks between complete sleeve
  geometry and animated slide/barrel/front/rear sights over Ready/Fire/Reload.
  This catches upper-gun intersections that the earlier near-plane-only tests
  missed; it does not prove visual magazine non-occlusion or natural gripping.
- Offline compileJava/processResources and build passed (final build 35s), and
  the corrected graphical client started. At 15:58:51 it passed independent
  0.55 scaling across gun scales 0.2/0.3/0.6/1.0, calibrated palm contact,
  standard Classic/Slim geometry and complete sleeves. Across 301 reload and
  41 fire samples, the OBB checks found no sleeve intersections with slide,
  barrel or sights. Closest sleeve Z=-0.33871 (reload), -0.41138 (fire), beyond
  the unchanged -0.05 near plane. Manual grip/magazine-occlusion acceptance
  remains pending; OBB clearance is not a claim of visual success.
- All 14 frozen file SHA-256 hashes are unchanged. Source/resource verifier
  passed, and release JAR contains no DEV classes or TaCZ assets. Whole-repo
  diff-check reports five Markdown trailing-space lines in the user's concurrently
  appended future-ammo/HUD checklist; those unrelated edits are preserved.
  Diff-check excluding that checklist passes; the arm changes add no whitespace
  errors. No screenshots/previews, source edits, commit or push.

## V0.4.4 first-person baseline restore and matrix ownership

- **Baseline provenance**: native pistol files remain uncommitted on `master`
  at `451f8bd`; there is no V0.4.2 commit to claim or reset to. Exact values are
  recoverable from the V0.4.2 implementation record above, the unchanged assets,
  and file hashes retained at the V0.4.3 entry audit. Before V0.4.4 edits, all
  14 audited frozen files still matched that snapshot. In particular:
  `ServicePistolPresentation.java` SHA-256
  `dc6a1c7499fb17b0adb5d9589881dc4bd015a9f5feae21e7c3d246457787e07a`,
  old `ServicePistolFirstPerson.java` SHA-256
  `186075f62c2ada7a627f5caabbf9d25f93a8c2864549a5d3e74f5553e6327f83`,
  and animation SHA-256 `ae04019755413def1170af9a42708f4bb52bb5c27d2b2ccfc0ee2e024cfcd30e`.
- **Current vs V0.4.2 READY**: camera translation `(0.60,-0.54,-0.64)`, no extra
  base rotation, display translation `(-6,1,0)/16`, display scale `(0.3,0.3,0.3)`;
  equip adds `-0.6*equipProgress` on Y. At rest weapon_root animation delta is zero.
  No difference in these values was found. The combined neutral pre-bone matrix
  has diagonal 0.3 and translation `(0.225,-0.4745,-0.64)` including GeckoLib's
  0.01 local Y offset. Existing camera bob/hurt transforms are inherited unchanged.
- **FIRE**: same base matrix, same 0.14s source; weapon_root pitch magnitude
  peaks at 3.2 degrees, slide reaches +3.2 authoring Z at 0.04s, then returns.
  No animation/source or recoil redesign. Reload presentation is zero on Fire.
- **Oversized-gun audit classification: OTHER**. There is no measured gun scale,
  camera-Z, root or double-transform change. Local PoseStack source confirms
  pushPose deep-copies pose/normal matrices; existing try/finally scopes balance.
  No arm-to-weapon_root assignment was found. The confirmed intervening change
  is hand scale/contact/direction around the gun, causing the reported hugging
  composition. This is not evidence of a gun matrix leak; perceived gun size
  still requires the user's visual comparison after restoring the reference hands.
- **Restore**: retain/enforce the already matching V0.4.2 gun matrix and restore
  its exact effective hand mapping: independent scale 0.30, palm Y 8/9.5,
  Classic/Slim hand-center compensation, offsets right `(-1.55,+0.20,+0.75)` /
  left `(+2.45,-0.55,-1.65)`, local -Y directions `(0.24,-0.69,0.69)` /
  `(-0.24,-0.87,0.44)`, normalized. This intentionally accepts slightly small
  reference arms; no new tuning or state-dependent hand scaling is introduced.
- **Reload preserved**: current +32° yaw, -16° roll, +8° pitch; camera delta
  `(+0.05,+0.28,-0.25)`; unchanged smoothstep envelope 0–0.24s, hold to 0.85s,
  return by 1.18s. Full 1.30s weapon_root/magazine/reload_magazine/left-anchor
  evaluation remains. Hand rendering uses the restored reference mapping in
  every state; gun side-open and hand-anchor motion are not rolled back.
- **Ownership isolation**: `ServicePistolRenderMatrices.detachedCopy` creates
  separate pose AND normal matrix objects. The weapon entry copies the caller's
  camera stack and owns its push/finally/pop scope. The hand layer only reads
  the animated per-bone pose, copies it, and calibrates/renders on its own scoped
  stack. Even a direct or unbalanced arm-only matrix edit cannot mutate weapon
  or camera matrices. Arm code does not write bones, roots, display or projection.
- `NativeGunMatrixChecks` adds one-shot DEV checks for baked display agreement,
  old push/pop isolation, detached matrix ownership, legacy-hand matrix equality
  and repeated Fire/Reload returns. Existing high-frequency DEV traces now require
  `-Dafl.debug.nativeGunPresentation=true`; default is off and release excludes
  all DEV classes. No release path logging was added.
- **Verification boundary**: offline compileJava/processResources/build passed;
  graphical clients were launched. The first restored-baseline run exposed a
  Classic right sleeve/upper-gun intersection under the stricter V0.4.3 OBB test.
  This is checked against the exact legacy mapping, not silently removed: the
  current and baseline intersection counts must agree at every sampled pose.
  Restoration is not a claim of zero arm/gun intersections; further palm
  calibration remains deferred. The known partial magazine frustum warning
  is also retained, not hidden.
- An initial repeated-action test checked Fire at 0.30s and found matrix element
  residual `0.00017054359`. GeckoLib's unchanged default bone-reset duration is
  5 ticks after the last sampled animation frame; the corrected test includes
  natural settling (Fire 0.45s / Reload 1.60s) without resetting bones between
  actions or modifying runtime/source timing. Final startup check at 16:24:40
  passed 40 actions / 7,320 samples on one controller manager, both hands and
  Classic/Slim: legacy/current hand matrices agree within 1e-5, each arm pass
  preserves the gun matrix/root, and every naturally settled action returns to
  the same READY matrix. This is numerical transition verification, not manual
  acceptance or a claim of bit-exact zero at every animation end timestamp.
- The final graphical client's resource/controller smoke checks passed. The
  initial Classic right sleeve intersection count is 1 in both legacy and current
  mappings; all 301 Reload + 41 Fire clearance samples match the old baseline.
  Reload sleeve nearest Z=-0.29642868, Fire Z=-0.30748424; both remain beyond
  the -0.05 near plane. Reload root rotation=0.2443461rad, magazine travel=
  24.106781, left-anchor travel=37.46896; Fire recoil=0.055850536rad.
- Final offline build passed (40s). Source/resource verifier passed 77 cubes,
  16 bones and 132 exact keys. Of 14 frozen-entry hashes, only the authorized
  FirstPerson stack-ownership refactor changed a file; presentation parameters,
  source/runtime assets, audio, icon, third-person and action logic remain exact.
  Release JAR includes the new matrix helper and contains zero DEV/TaCZ entries.
  Whole-repo `git diff --check` still reports five pre-existing trailing-space
  lines in the user's appended future-ammo/HUD checklist; they were preserved.
  The check excluding that checklist passes. No screenshots or previews were made.
- **V0.4.4 changed files**: production `weapon/client/ServicePistolFirstPerson.java`,
  `ServicePistolHandLayer.java`, new `ServicePistolRenderMatrices.java`; DEV
  `NativeGunMatrixChecks.java`, `NativeGunRuntimeSmokeCheck.java`,
  `NativeGunArmChecks.java`, `NativeGunArmClearance.java`,
  `NativeGunPresentationTrace.java`, `NativeGunArmTrace.java`. The four directly
  related framework/checklist/gun-system/art-standard documents were synchronized.
- Gun geometry, texture, source animations, audio, third-person, icon, input and
  prototype gameplay remain frozen. Manual V0.4.4 visual acceptance is pending.

## Planned, not implemented

V0.4.3 unit/0.55 arm passes were not accepted as the final Ready baseline.
V0.4.4 restores the smaller reference mapping; further isolated Arm Calibration
is deferred. V0.4.4 visual acceptance is pending. ADS is not implemented. V0.6+: real ammo,
damage/ballistics, Noise, Tinnitus, suppressor and shell systems. None is active
in this V0.4 prototype. Manual visual/audio testing does not imply those features.
