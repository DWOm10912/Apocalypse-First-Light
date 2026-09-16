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
complete members are separated by `0.20 px` in opposite directions along the
brace plane normal; no center plate is added. Their shapes are simple
eight-step in-cell approximations, mirrored for each slope and combined for X.

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
