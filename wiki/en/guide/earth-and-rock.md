# Digging and Rock

- <StatusTag s="test" /> <StatusTag s="creative" /> The tools on this page are in the Creative inventory only, still on a test branch

The ground can be dug out and filled in one shovelful at a time. Granite boulders lie half buried in the soil. A shovel can't move them; a pick mattock breaks them up

## Tools

| Tool | Use | Weight |
|---|---|---|
| <ItemIcon id="round_point_shovel" /> Round Point Shovel | Dig, pour, scrape | <V d="item_mass/core_v1#items.apocalypse_firstlight:round_point_shovel.unit_mass_kg" u="kg" /> |
| <ItemIcon id="pick_mattock" /> Pick Mattock | Strike boulders | <V d="item_mass/core_v1#items.apocalypse_firstlight:pick_mattock.unit_mass_kg" u="kg" /> |

## Shovel

Hold the round point shovel, aim at loose ground and press Use once for one stroke. Holding the key does not repeat it

- Empty shovel: digs up a shovelful, costs <V d="stamina/stamina_v1#costs.shovel_dig" /> stamina
- Loaded shovel: pours it where you aim, costs <V d="stamina/stamina_v1#costs.shovel_pour" /> stamina
- Sneaking: scrapes the ground level, costs <V d="stamina/stamina_v1#costs.shovel_scrape" /> stamina

When the blade hits rock it says "Rock here, can't dig". Dig down beside a boulder to bare the part buried in the soil

## Boulders

Boulders are granite half sunk into the ground: round erratics, flat slabs, talus blocks, clusters and more. Bare hands, a shovel or any other tool can't touch them, and aiming at one says "Needs a pick"

Strike it with a pick mattock:

- Hold the pick mattock, aim at the boulder and press Use once for one strike, about <V j="BoulderStrike.DURATION" x="0.05" u="s" />
- Only rock close to your feet can be struck: within <V j="BoulderStrike.REACH" u="blocks" /> of your eyes
- Every strike that lands costs <V d="stamina/stamina_v1#costs.pick_strike" /> stamina. Out of stamina, you can't strike
- It is loud: infected nearby hear it
- After enough strikes a big boulder **splits** into pieces. Strike each piece until it **breaks**, dropping <ItemIcon id="field_stone" /> field stones
- A whole boulder takes at most 50 strikes from the first to the last piece
- A boulder that is gone leaves a pit, and the soil at its edges slumps in

## Field Stone

<ItemIcon id="field_stone" /> Field stones are the granite a broken boulder drops, <V d="item_mass/core_v1#items.apocalypse_firstlight:field_stone.unit_mass_kg" u="kg" /> each, up to 8 to a stack. They have no use yet

- <StatusTag s="planned" /> Loose stones and pebbles to pick up from the ground
- <StatusTag s="planned" /> Quarrying natural stone with the pick mattock
