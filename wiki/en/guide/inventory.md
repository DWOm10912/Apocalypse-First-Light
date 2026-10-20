# Inventory and Weight

AFL replaces the vanilla inventory with a grid: every item takes up a block of cells. Press **E** to open it

## Grid inventory

- **Pockets**: you always have 4 small pockets
- **Chest rig**: wearing the field chest rig adds magazine pouches. See [Equipment](../arms/equipment)
- **Backpack**: the canvas backpack has 5×6 cells, the hiking backpack 6×10
- Press **R** while dragging to rotate an item, **Q** to drop the item under the cursor
- Items on the ground are not picked up automatically: look at one and press Use, or Sneak + Use to put it straight into your grid

## Weapon slots and quick binds

| Slot | Holds | Needs |
|---|---|---|
| 1 | Long gun | A gun sling fitted to the gun |
| 2 | Pistol | A pistol holster |
| 3 | Melee weapon | — |
| 4–9 | Quick binds for items in pockets or the rig | — |

AFL has no off hand; guns are always held right-handed

## Weight

Everything has a close-to-real weight. Blocks are weighed as "one block to carry", so they are much lighter than the real thing. Items with no listed weight count as <V d="item_mass/core_v1#policy.fallback_unit_mass_kg" u="kg" /> each

Comfortable load is <V d="item_mass/core_v1#policy.comfort_capacity_kg" u="kg" />. Above it:

| Load | Move speed | Jump |
|---|---|---|
| ≤ <V d="item_mass/core_v1#policy.comfort_capacity_kg" u="kg" /> | Normal | Normal |
| <V d="item_mass/core_v1#policy.penalties.curve.1.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.1.speed" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.1.jump" /> |
| <V d="item_mass/core_v1#policy.penalties.curve.2.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.2.speed" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.2.jump" /> |
| <V d="item_mass/core_v1#policy.penalties.curve.3.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.3.speed" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.3.jump" /> |
| <V d="item_mass/core_v1#policy.penalties.curve.4.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.4.speed" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.4.jump" /> |

- At <V d="item_mass/core_v1#policy.penalties.sprint_block_ratio" x="100" digits="0" u="%" /> of comfortable load (about <V d="item_mass/core_v1#policy.penalties.sprint_block_ratio" x="30" digits="0" u="kg" />) you can no longer sprint
- Armor you wear counts at <V d="item_mass/core_v1#policy.penalties.armor_load_factor" pct /> of its weight
- A heavier load also costs more stamina:

| Load | Walk / sprint cost | Action cost | Stamina regen |
|---|---|---|---|
| <V d="stamina/stamina_v1#weight.1.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="stamina/stamina_v1#weight.1.move" /> | ×<V d="stamina/stamina_v1#weight.1.action" /> | ×<V d="stamina/stamina_v1#weight.1.regen" /> |
| <V d="stamina/stamina_v1#weight.2.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="stamina/stamina_v1#weight.2.move" /> | ×<V d="stamina/stamina_v1#weight.2.action" /> | ×<V d="stamina/stamina_v1#weight.2.regen" /> |
| <V d="stamina/stamina_v1#weight.3.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="stamina/stamina_v1#weight.3.move" /> | ×<V d="stamina/stamina_v1#weight.3.action" /> | ×<V d="stamina/stamina_v1#weight.3.regen" /> |
| <V d="stamina/stamina_v1#weight.4.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="stamina/stamina_v1#weight.4.move" /> | ×<V d="stamina/stamina_v1#weight.4.action" /> | ×<V d="stamina/stamina_v1#weight.4.regen" /> |

Kilograms in these tables assume the default 30 kg comfortable load

## Searching containers

Cabinets, shelves, bins and other containers in the world start "unsearched". Open one and its contents turn up one slot at a time; close the screen to stop and pick up where you left off next time

<StatusTag s="integrated" /> For now most of what you find is store-themed "goods" (grocery, hardware, pharmacy, school, industrial); detailed item lists are still being made
