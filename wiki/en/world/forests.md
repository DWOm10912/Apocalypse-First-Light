# Forests

<Shot k="hero_forest" zh="AFL 的森林" en="An AFL forest" />

AFL replaces vanilla trees with its own, with round trunks and leafy crowns

Six species: white oak, red oak, shagbark hickory, red maple, white pine and eastern hemlock. Each biome mixes them differently

## Felling

- You need an axe; bare hands won't do
  - <StatusTag s="test" /> Vanilla axes can no longer be obtained; the AFL [Felling Axe](../arms/equipment) comes only from the creative inventory for now
- Each swing takes <V j="FellingRules.CHOP_TICKS" x="0.05" u="s" />; a chop counts when the axe bites into the wood
- The cut deepens in <V j="FellingRules.STAGES" /> steps and the tree falls once you have chopped enough; thicker trees and harder wood take more chops
- The cut opens on the side you chop from, and follows you if you move round
- Trees fall away from the cut, and **a falling tree hurts**
- Each chop costs <V d="stamina/stamina_v1#costs.chop" /> stamina, and infected nearby can hear it

<StatusTag s="creative" /> Timber has no recipes yet. See [Materials and Processing](../industry/materials#timber)

## Vanilla wood

<StatusTag s="test" /> Vanilla tree species and wooden items cannot be obtained in AFL

- Logs, planks, stairs, slabs, fences, doors, trapdoors, signs, boats, saplings and leaves of oak, spruce, birch and the other vanilla species cannot be crafted and never drop
- The same goes for things made of vanilla wood: sticks, crafting tables, chests, barrels, bookshelves, ladders, beds, torches and wooden tools
- Items you already have and blocks already placed still work, but they drop nothing when broken
- Every biome with trees grows only AFL trees, and lush caves no longer grow azalea trees
- Recipes using AFL timber will come with the workbench
