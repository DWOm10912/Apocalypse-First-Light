# Digging and Rock

- <StatusTag s="test" /> <StatusTag s="creative" /> The tools and stones on this page are in the Creative inventory only, still on a test branch. Loose stones can only be placed with a dev command for now, not by world generation yet

The ground can be dug out and filled in one shovelful at a time. Granite boulders lie half buried in the soil. A shovel can't move them; a pick mattock breaks them up. Bare rock in the open is struck with the pick mattock too, and every strike takes the ground a little lower. Loose stones lie on the ground, free to pick up

## Tools

| Tool | Use | Weight |
|---|---|---|
| <ItemIcon id="round_point_shovel" /> Round Point Shovel | Dig, pour, scrape | <V d="item_mass/core_v1#items.apocalypse_firstlight:round_point_shovel.unit_mass_kg" u="kg" /> |
| <ItemIcon id="pick_mattock" /> Pick Mattock | Strike boulders and rock | <V d="item_mass/core_v1#items.apocalypse_firstlight:pick_mattock.unit_mass_kg" u="kg" /> |

## Shovel

Hold the round point shovel, aim at loose ground and press Use once for one stroke. Holding the key does not repeat it

- Empty shovel: digs up a shovelful, costs <V d="stamina/stamina_v1#costs.shovel_dig" /> stamina
- Loaded shovel: pours it where you aim, costs <V d="stamina/stamina_v1#costs.shovel_pour" /> stamina
- Sneaking: scrapes the ground level, costs <V d="stamina/stamina_v1#costs.shovel_scrape" /> stamina

The round point shovel is 1.6 m long. One stroke digs about 300 L, a shallow hole a little ahead of your feet. A stroke takes about 2.2 seconds: the blade goes into the ground, your right foot stamps it in, you lever it back and lift the load. The earth stays on the shovel: aim somewhere out of the way and press Use again to pour it out, about 1.2 seconds. A scrape takes about 1.4 seconds

You carry the shovel two-handed across your body, the blade low ahead to the right. A loaded shovel can't dig until it is poured out

The shovel ends in a D grip. An empty shovel hangs from a peg of the wooden tool rack by it, blade down, reaching into the block below. A loaded one can't be hung

When the blade hits rock it says "Rock here, can't dig". Dig down beside a boulder to bare the part buried in the soil. The same goes for the rock under the soil: switch to the pick mattock

Ground that has been dug or poured on takes only the shovel. Bare hands can't break it: it says "Dig this ground with a shovel"

A thin soil lying straight on rock can be dug and poured on too. The shovel can't work these places, and a pour that can't go down says so:

- The top edge of a natural bank or cliff three blocks or higher. <StatusTag s="test" /> The top of a two-block bank can be dug and poured on
- Next to roads, houses and tree roots
- Under a block less than two blocks overhead

## Boulders

Boulders are granite half sunk into the ground: round erratics, flat slabs, talus blocks, clusters and more. Bare hands, a shovel or any other tool can't touch them, and aiming at one says "Needs a pick"

Strike it with a pick mattock:

- Hold the pick mattock, aim at the boulder and press Use once for one strike, about <V j="BoulderStrike.DURATION" x="0.05" u="s" />
- Only rock close to your feet can be struck: within <V j="BoulderStrike.REACH" u="blocks" /> of your eyes
- Every strike that lands costs <V d="stamina/stamina_v1#costs.pick_strike" /> stamina. Out of stamina, you can't strike
- It is loud: infected nearby hear it
- After enough strikes a big boulder **splits** into pieces. Strike each piece until it **breaks**, dropping <ItemIcon id="field_stone" /> field stones and sometimes a few <ItemIcon id="pebble" /> pebbles
- A whole boulder takes at most 50 strikes from the first to the last piece
- A boulder that is gone leaves a pit, and the soil at its edges slumps in

## Striking Rock

- <StatusTag s="test" /> Natural stone in the ground can't simply be broken. In Survival bare hands and other tools can't break it, and hitting it says "Rock takes a pick"

Every strike takes the rock a little lower, no shovel needed:

- Hold the pick mattock, aim at bare rock in the open and press Use once for one strike. The swing, stamina and sound are a boulder strike's
- Where it lands the rock is cut straight down, leaving no rubble. One strike takes away up to about 50 L
- Strike after strike you dig one continuous pit, and its sides can be steep
- Where soil still lies over the rock, dig the soil off with the shovel first, then strike the rock. Rubble or poured earth on the spot comes off first under the pick
- The pick goes down at most <V j="Earthwork.ROCK_DEPTH" u="blocks" /> below the first ground. Over a cave a layer of rock is always left, never broken through
- The top edge of a cliff three blocks or higher, and ground next to roads and houses, can't be struck: it says "Can't cut the rock here". The top of a two-block step can be struck. The ground at the foot of a cliff can be struck; a narrow edge along a cliff three blocks or higher stays as it is
- The stone it breaks off drops by volume: on average a <ItemIcon id="field_stone" /> field stone every two strikes and a <ItemIcon id="pebble" /> pebble or two every strike

Rubble struck loose in older saves is still there: it is loose earth, and like sand and gravel it goes into the shovel, pours out and heaps into slopes; poured on the ground and levelled it becomes gravel

- <StatusTag s="planned" /> Rock underground (tunnels, shafts) and the granite, andesite and diorite patches

## Loose Stones

Out in the wild a few stones lie on the ground here and there: some pebbles, sometimes a field stone. On riverbeds and banks they are river cobbles, worn rounder, flatter and smoother by the water

- Aim at loose stones and press Use once to pick one up: the field stone first, then the flint, then the pebbles one by one
- No tool needed, bare hands will do. The stones you pick up count towards your load
- With the round point shovel in hand, Use digs. Digging where loose stones lie knocks them out as items

## Field Stone, Pebble and Flint

| Item | Where from | Weight | Stack |
|---|---|---|---|
| <ItemIcon id="field_stone" /> Field Stone | Loose stones, broken boulders | <V d="item_mass/core_v1#items.apocalypse_firstlight:field_stone.unit_mass_kg" u="kg" /> | 8 |
| <ItemIcon id="pebble" /> Pebble | Loose stones, broken boulders | <V d="item_mass/core_v1#items.apocalypse_firstlight:pebble.unit_mass_kg" u="kg" /> | 32 |
| <ItemIcon id="flint" /> Flint | Now and then among loose stones | <V d="item_mass/core_v1#items.apocalypse_firstlight:flint.unit_mass_kg" u="kg" /> | 16 |

Flint is rare, mostly in limestone country and in river gravel. It is dark with a waxy sheen, unlike the pale granite around it. Flint from the hills still wears a white rind; flint washed down a river has had it worn away

River cobbles in your hand are ordinary pebbles and field stones, and stack with the ones from the fields

None of the three has a use yet

- <StatusTag s="planned" /> Loose stones placed by world generation: forests, fields, riverbeds
- <StatusTag s="test" /> Vanilla stone, cobblestone, stone bricks, andesite, granite and diorite, with their stairs, slabs and walls, can no longer be obtained, and neither can vanilla flint. Gravel drops only gravel
- <StatusTag s="test" /> Natural stone has a new rock texture (4 of them, mixed at random); only its look changed
