# Fixed 1x1 steel braces

`diagonal_brace_a`, `diagonal_brace_b`, and `cross_brace` are independent,
ordinary building blocks. Each has only `horizontal_axis=x|z`: EAST/WEST
placement selects the X-Y plane, NORTH/SOUTH selects Z-Y, and vertical placement
uses the plane perpendicular to the player's horizontal view.

The items select the brace slope directly: A is `/`, B is `\\`, and Cross is a
single fixed X model. All six runtime models are pre-baked for one block cell;
there is no dynamic slope choice, multipart rendering, 2x2 bay, cross merge,
occupancy, part block, BlockEntity, ticking, custom renderer, or neighbor logic.

The models reuse the 64x64 shared `steel_beam` texture, all 34 beam elements,
and every original face UV. Longitudinal coordinates are stretched from 16 px
to `16*sqrt(2)` (about 22.627 px) without changing UV values, then A/B add a
baked whole-model diagonal rotation. Cross contains two complete 34-element
copies (68 total). To prevent coplanar center faces from z-fighting, its two
complete members are separated by `0.60 px` in opposite directions along the
brace plane normal; no center plate is added. Their shapes are simple
eight-step in-cell approximations, mirrored for each slope and combined for X.

## Automatic steel-beam joints

All three fixed brace blocks expose `joint_negative` and `joint_positive`
Boolean block-state properties. For `horizontal_axis=x`, negative/positive
mean WEST/EAST; for `horizontal_axis=z`, they mean NORTH/SOUTH. Placement and
ordinary neighbor updates recalculate both properties using exact block identity
against `apocalypse_firstlight:steel_beam`. Other solid or decorative blocks do
not enable a joint, and no tick or block entity is involved.

The blockstates are multipart: the accepted brace body remains the always-visible
axis model, while a true joint property adds only the matching endpoint model.
A and B select their actual low/high endpoint independently. Cross adds both
endpoint joints on an enabled side. Each endpoint uses a slightly narrower
diagonal connector that continues the brace angle across the gap, with only
`0.03 px` of longitudinal overlap at the brace end. Its narrower `7.1..8.9 px`
cross-section deliberately avoids every existing beam skin/web plane. A small rectangular end
plate sits on the neighboring beam's actual brace-facing surface and carries
four restrained bolt heads that are slightly inset rather than coplanar. X joint
segments inherit the same `+0.60/-0.60 px` layer separation as their parent
members. All parts use the shared 64x64 `steel_beam` texture.
Joint editable sources live beside the fixed brace sources under
`src/main/blockbench/fixed_diagonal_braces/`.

All three require a diamond-tier pickaxe and drop exactly their own item. They
are listed together immediately after `steel_beam` in the industrial creative tab.
Their editable sources are under `src/main/blockbench/fixed_diagonal_braces/`.

Build verification remains separate from in-world verification. Old development
worlds may contain the removed `diagonal_brace` ID and should be cleared manually;
no world migration is provided.

Verification on 2026-09-15: `compileJava`, `processResources`, and full `build`
passed. The development client reached resource loading without a missing-model
or missing-texture warning for the three fixed brace blocks. In-world placement,
adjacent stacking, dense-build performance, collision feel, mining, and drops
remain untested.
