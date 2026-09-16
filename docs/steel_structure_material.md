# Steel structure shared material

`steel_beam` and the fixed 1x1 brace sources share the canonical 64×64 PNG at
`src/main/blockbench/steel_beam/steel_beam.png`. Brace models reference
`../steel_beam/steel_beam.png` and embed the same PNG.
The former 128px sources are preserved under
`src/main/blockbench/steel_structure_backup_128/` as historical backups.

The 64px atlas was repainted by material region, rather than directly resized:
continuous muted edge highlights, dark simplified recessed webs, restrained
panel divisions and a single compact fastener detail region. It contains no
random grain or rust. Source UV coordinates were halved; normalized Java
block-model UV coordinates remain unchanged. Beam geometry remains 34 cubes.
Each fixed single brace reuses all 34 beam elements and their original UVs,
stretching only the longitudinal coordinates from 16 px to about 22.627 px;
the fixed X model contains two complete copies (68 elements), separated by
`+0.60/-0.60 px` along the plane normal to avoid center z-fighting.

The registered beam uses the matching runtime texture at
`src/main/resources/assets/apocalypse_firstlight/textures/block/steel_beam.png`.
The three fixed 1x1 brace blocks (`diagonal_brace_a`, `diagonal_brace_b`, and
`cross_brace`) use this same texture. See `docs/diagonal_brace_runtime.md`.

Legacy multi-bay Blockbench assets remain under `src/main/blockbench/diagonal_brace/`
as visual history only. Runtime uses the editable fixed sources under
`src/main/blockbench/fixed_diagonal_braces/`. Reduced texture detail is intended
to improve distant sampling; in-game performance remains separately verified.

Verification on 2026-09-15 confirmed successful resource processing and client
resource loading for the fixed brace models. Visual and performance verification
inside a world remains pending.
