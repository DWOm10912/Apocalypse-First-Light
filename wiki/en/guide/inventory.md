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
| 1 | Long gun | A gun sling fitted to the gun ([how](../arms/attachments#gun-sling)) |
| 2 | Pistol | A pistol holster |
| 3 | Melee weapon | — |
| 4–9 | Quick binds for items in pockets or the rig | — |

<StatusTag s="integrated" d="2026-10-25" /> Without a pistol holster on, the empty pistol slot shows red and takes nothing; with no slung long gun on you, the empty long gun slot does too. Hover over it to see what is missing. The mouse wheel skips red slots. When your hands hold something that does not fit anywhere on you, the wheel does not switch and the screen says "No room"

AFL has no off hand; guns are always held right-handed

## Body map

- <StatusTag s="verified" d="2026-10-20" /> The body map

The middle of the inventory's left column shows your own skin from the front, turned grey. The screen's left is your body's right; R and L under the feet say which is which, with your health below

An injured part is tinted and carries the same status badge as the HUD: red wants treating soon (an uncleaned infection, an unsplinted fracture), brown wants treating (an untreated scratch), amber is treated and getting better

Hover over a part to list each injury on it and what to do: the best item you have on you is outlined; better ones you do not have are dimmed above it, so you know what to look for; with nothing at all you get one line on what to do without. "On you" means your hands, pockets, rig and backpack, not the ground or a container. See [Survival](./survival#wounds)

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

## Inspecting items

<StatusTag s="integrated" />

- Point at an item in the inventory and press **V**, or press **V** holding anything that is not a gun, to open the inspect screen. The item flies out of its cell to the middle of the screen; the game keeps running. In first person it is lit by the light around you, like the gun on the field attachment screen, and the background is not dimmed
- Left-drag to turn it, middle-drag to pan, scroll to zoom, **R** to square it up again; **V**, **Esc** or **E** closes it
- Top right are the key hints, with the item's name and a one-line description under them (the same line its hover tooltip shows under the name). The minimap, coordinates, compass and Geiger counter window are hidden meanwhile
- Some items have hotspots: turn the item so the part faces you, point at it to see it outlined in amber, and click. Each action finishes before the next click. For now only the Geiger counter has them:
  - the power slider on the side: switch on / off
  - the battery cover on the back: open the cover; once open, right-click the bay to close it
  - a fitted battery: remove it
  - the empty bay: fit a battery, picked from a row of the 9 V batteries you carry (pockets, chest rig, backpack, hotbar)
- A battery you take out goes back into your inventory, or drops at your feet if there is no room. Closing the screen with the cover off puts it back on first
- Being hurt, or the item leaving the cell it was in, closes the screen
- **V** with a gun in hand still plays the inspect animation; a gun in the inventory can only be turned and looked at

## Searching containers

Cabinets, shelves, bins and other containers in the world start "unsearched". Open one and its contents turn up one slot at a time; close the screen to stop and pick up where you left off next time

<StatusTag s="integrated" /> For now most of what you find is store-themed "goods" (grocery, hardware, pharmacy, school, industrial); detailed item lists are still being made
