# Forests

<Shot k="hero_forest" zh="AFL 的森林" en="An AFL forest" />

AFL replaces vanilla trees with its own, with round trunks and leafy crowns

Six species: white oak, red oak, shagbark hickory, red maple, white pine and eastern hemlock. Each biome mixes them differently

## Felling

- You need an axe; bare hands won't do
  - <StatusTag s="test" /> Vanilla axes can no longer be obtained and AFL has no axe of its own yet, so trees cannot be felled in new worlds for now
- Each chop takes <V j="FellingRules.CHOP_TICKS" x="0.05" u="s" />; after <V j="FellingRules.STAGES" /> chops the tree falls
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
