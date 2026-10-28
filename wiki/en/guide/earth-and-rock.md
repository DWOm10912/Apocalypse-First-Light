# Digging and Rock

- <StatusTag s="test" /> <StatusTag s="creative" /> The tools and stones on this page are in the Creative inventory only, still on a test branch. World generation puts boulders and loose stones only round the cave entrances of the deep test world for now; elsewhere loose stones can only be placed with a dev command

The ground can be dug out and filled in one shovelful at a time. Granite boulders lie half buried in the soil. A shovel can't move them; a pick mattock breaks them up. Bare rock in the open is struck with the pick mattock too, and every strike takes the ground a little lower. Loose stones lie on the ground, free to pick up

## Tools

| Tool | Use | Weight |
|---|---|---|
| <ItemIcon id="round_point_shovel" /> Round Point Shovel | Dig, pour, scrape | <V d="item_mass/core_v1#items.apocalypse_firstlight:round_point_shovel.unit_mass_kg" u="kg" /> |
| <ItemIcon id="pick_mattock" /> Pick Mattock | Strike boulders and rock, break up asphalt roads (see [Roads](../world/roads#digging-up-a-road)) | <V d="item_mass/core_v1#items.apocalypse_firstlight:pick_mattock.unit_mass_kg" u="kg" /> |

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
- Next to roads, houses and tree roots (a block of asphalt road can be dug once all its asphalt is broken out, see [Roads](../world/roads#digging-up-a-road))
- Under a block less than two blocks overhead

## Boulders

Boulders are rock half sunk into the ground: round erratics, flat slabs, talus blocks, clusters and more. Bare hands, a shovel or any other tool can't touch them, and aiming at one says "Needs a pick"

- <StatusTag s="test" /> Boulders often lie round cave entrances: slabs and blocks of the fallen roof on a collapse pit's floor, rounded erratics bared from the soil in a cover sinkhole, broken roof scattered over a great sinkhole's floor, blocks rolled past the foot of a rockfall heap, the biggest furthest out. Now and then one or two sit at the sides of the cut in front of a hillside mouth. No boulder blocks the way down
- <StatusTag s="test" /> Boulders and loose stones round cave entrances show their rock: mostly pale grey limestone, some tan sandstone and dark grey shale; the round erratics in a cover sinkhole are granite, carried in by the ice. A broken boulder drops its own rock's stones: limestone stones from limestone, sandstone stones from sandstone, shale shards from shale, granite stones from granite

Strike it with a pick mattock:

- Hold the pick mattock, aim at the boulder and press Use once for one strike, about <V j="BoulderStrike.DURATION" x="0.05" u="s" />
- Only rock close to your feet can be struck: within <V j="BoulderStrike.REACH" u="blocks" /> of your eyes
- Every strike that lands costs <V d="stamina/stamina_v1#costs.pick_strike" /> stamina. Out of stamina, you can't strike
- It is loud: infected nearby hear it
- After enough strikes a big boulder **splits** into pieces. Strike each piece until it **breaks**, dropping its rock's stones (such as <ItemIcon id="field_stone" /> granite stones) and sometimes a few <ItemIcon id="pebble" /> pebbles
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
- The stone it breaks off drops by volume: on average a stone every two strikes and a <ItemIcon id="pebble" /> pebble or two every strike. The stone is the rock you struck (granite gives a <ItemIcon id="field_stone" /> granite stone). Softer rock gives more to a strike: shale and marl go faster than granite
- <StatusTag s="test" /> Every strike throws out chips of rock, a faint puff of pale rock powder and a sprinkle of grit, all in the rock's own colour; hard quartz-bearing rock such as granite and sandstone now and then strikes sparks. Hard rock rings under the pick, limestone and shale sound dull; when a block or a boulder goes you hear the chunk drop and tumble, and the infected hear a strike as far as you do (16 blocks)

Rubble struck loose in older saves is still there: it is loose earth, and like sand and gravel it goes into the shovel, pours out and heaps into slopes; poured on the ground and levelled it becomes gravel

- <StatusTag s="planned" /> Rock underground (tunnels, shafts) and the granite, andesite and diorite patches
- <StatusTag s="test" /> In deep test worlds the underground is now real rock layers: granite, gneiss, schist, sandstone, limestone, dolomite, shale, marl and rock salt, laid out by the geology, and road cuts show the local rock too. Like natural stone it takes a pick in Survival, and it breaks into that rock's stones. All textures but granite's are still stand-ins
- <StatusTag s="test" /> Natural rock away from the open ground (cave walls and roofs, cliff faces) can be cut too: each strike of the pick mattock breaks a piece off where it lands, and the hollow shows at once, never the same shape twice, its fresh faces paler than the weathered rock. A block's worth takes about 20 strikes in granite, 13 in shale, 11 in marl; a block with less than an eighth left is cleared, the rest still counted in the drops. Digging down in a pit or shaft (rock on two sides or more above the floor), the floor goes down level and takes the corners along the walls with it, so the walls stay straight and the shaft does not narrow; cleared blocks let daylight down the shaft, and drops, cut faces and what you hold in a pit or hollow are lit by the light at its mouth, darker further in. The hollows collide as they look, so you can stand in them. Ground the earthwork has dug and rock under a boulder can't be cut this way, and neither can the floor of a pit downwards yet
- <StatusTag s="verified" d="2026-10-24" /> Cave walls, roofs, cliff faces and rock in the open are no longer block faces but continuous, irregular rock: granite breaks into jointed, tilted planes, limestone into rounded solution hollows and bulges, shale and sandstone into beds that stand out and set back in bands. Floors and ledge tops are uneven too: limestone in gentle flowstone swells, shale, sandstone and granite in shallow slab terraces that don't hinder walking; ledges and outcrops have rounded edges, but a one-block rock step has to be jumped. The rock collides as it looks, so you can step into its hollows and cut into them; a torch, ladder or block placed against a face leaves that face flat. Steep soil banks, the soil walls of cave mouths and the undersides of overhanging soil are continuous surfaces too, slumped round at the edges and joined to the rock; ordinary soil slopes stay smooth slopes. Caves in deep worlds no longer have dirt floors and dirt walls, soil lies only at the surface; vanilla ores are gone there too, see the next item for the ores to come
- <StatusTag s="test" /> Ore lies inside the rock, and cutting ore-bearing rock gives raw ore, see Ores below
- <StatusTag s="verified" d="2026-10-24" /> With shaders on (Aureveil), rock surfaces have more relief and darker crevices. A fresh break cut with the pick shows its mineral faces: feldspar, quartz and mica in granite and gneiss, calcite in limestone, quartz grains in sandstone, mica along the foliation in schist, flashing one by one as the light or your view moves. Ores hold more grains of their metallic minerals (galena, chalcopyrite, pyrite, hematite), each catching the light its own way. Without shaders the rock looks the same, and ores show a few more mineral grains

## Natural ground

- <StatusTag s="test" /> <StatusTag s="creative" /> AFL has its own ground blocks: topsoil, mud, sand, red sand, gravel, clay, snow, and red sandstone. They are in the Creative inventory
- <StatusTag s="test" /> In a newly created federal world all natural ground is made of them: grassland is topsoil with turf, conifer forest floors have leaf litter, river and lake beds are rounded pebbles, mountains have angular scree, swamps are mud, snowy peaks are snow; the Altamesa badlands are no longer terracotta but beds of red sandstone, sandstone and shale. Worlds created earlier keep vanilla dirt and sand, in newly explored areas too

- Ground textures are drawn from real photos like the rock, one block to a metre, with no visible repeat between neighbouring blocks
- There is no grass block. Topsoil is soil on its sides and bottom, and its top shows what grows on it: turf, leaf litter, moss or bare soil. Turf takes the biome's grass colour; up close it shows tufts of grass and the gaps between them. A wall or a pit dug in topsoil shows no strip of grass at its sides
- Gravel comes in two looks: rounded pebbles in river beds, angular scree in the mountains
- This ground slopes smoothly and can be dug and poured with the shovel like vanilla dirt (except snow). Dug topsoil turns bare on top; dug and poured earth stays the ground it came from, and red sand stays red
- <StatusTag s="test" /> Slopes are no longer folded into two flat triangles per block but drawn as one continuous curved surface that runs on into the next slope. Where different ground meets (turf, sand, gravel, mud…) the boundary no longer follows the block edges: it wanders irregularly, at most about half a block off the edge, on level ground too. What you dig out is still decided by the block
- <StatusTag s="test" /> Holes and trenches dug into level ground, in creative or survival, come out as smooth hollows: one block deep is a shallow bowl with its floor at the depth you dug; two deep is a funnel sloping in from all sides; a one-block-wide trench is a V-shaped trough with a continuous floor. Only three blocks or deeper gives straight walls. One block of soil placed on level ground becomes a small mound. Walking, jumping and driving in a hole follow the ground as drawn
- Mud sinks a little underfoot, as vanilla mud does
- Sand and gravel do not fall as in vanilla: loose ground slumps into a slope at its angle of repose
- <StatusTag s="test" /> In a newly created test world with AFL rivers, river banks are slopes that run down into the water, not one-block steps. Rivers are wider than before and their banks wander in and out: gentle sand and gravel beaches in places, ordinary earth slopes in others, a low 1–1.5 m cut bank on the outside of bends and a sand bar on the inside. Riffles are 0.3–0.5 m deep and can be waded; every 5–7 river widths there is a pool 1.2–2 m deep, and about one pool in three is a 2.5–3.5 m hole that has to be swum. The water in a pool is nearly level, the fall comes at the riffles, where the current is faster. At bridges, culverts and in towns a river narrows back to its old width. Sloped lake shores are still planned

## Ores

<StatusTag s="test" /> In newly created deep test worlds the rock now holds ore. Ore bodies sit in the strata where the geology puts them. There are five ore-bearing rocks so far:

| Ore rock | Look | Cutting it gives |
|---|---|---|
| Coal Seam | Black, banded bright and dull, running in beds between shale and sandstone | <ItemIcon id="coal_lump" />Coal Lump |
| Banded Iron Formation | Thin maroon and steel-grey bands, layer on layer | <ItemIcon id="raw_hematite" />Raw Hematite |
| Mineralized Quartz Vein | Thin veins of white quartz with dark ribbons and rust stains; the veins of one district carry one metal | <ItemIcon id="gold_quartz" />Gold-Bearing Quartz, <ItemIcon id="raw_chalcopyrite" />Raw Chalcopyrite or <ItemIcon id="raw_galena" />Raw Galena |
| Lead-Zinc Dolomite | Lead-grey and dark brown grains along the bedding of grey dolomite | <ItemIcon id="raw_galena" />Raw Galena, <ItemIcon id="raw_sphalerite" />Raw Sphalerite, and dolomite stones |
| Copper Breccia | Grey rock fragments cemented together by brassy mineral | <ItemIcon id="raw_chalcopyrite" />Raw Chalcopyrite |

- The middle of an ore body is rich ore and its edges lean. Rich ore holds more mineral and gives more raw ore: a whole block of rich ore gives about 4 pieces, lean about 1. A coal seam gives far more (a dozen or so lumps a block), gold-bearing quartz less
- Ore rock is cut with the pick mattock like any rock, and gives pebbles besides its raw ore
- A coal seam is coal all through, mostly lean; inside the planned coalfields it is rich
- Banded iron formation lies only on the shield edge along the lake country's north shore: northwards the sedimentary rock thins until granite and schist come to the surface, and the iron lies in that old rock

| Raw ore | Weight | Stack |
|---|---|---|
| <ItemIcon id="coal_lump" /> Coal Lump | <V d="item_mass/core_v1#items.apocalypse_firstlight:coal_lump.unit_mass_kg" u="kg" /> | 16 |
| <ItemIcon id="raw_hematite" /> Raw Hematite | <V d="item_mass/core_v1#items.apocalypse_firstlight:raw_hematite.unit_mass_kg" u="kg" /> | 16 |
| <ItemIcon id="raw_chalcopyrite" /> Raw Chalcopyrite | <V d="item_mass/core_v1#items.apocalypse_firstlight:raw_chalcopyrite.unit_mass_kg" u="kg" /> | 16 |
| <ItemIcon id="gold_quartz" /> Gold-Bearing Quartz | <V d="item_mass/core_v1#items.apocalypse_firstlight:gold_quartz.unit_mass_kg" u="kg" /> | 16 |
| <ItemIcon id="raw_galena" /> Raw Galena | <V d="item_mass/core_v1#items.apocalypse_firstlight:raw_galena.unit_mass_kg" u="kg" /> | 16 |
| <ItemIcon id="raw_sphalerite" /> Raw Sphalerite | <V d="item_mass/core_v1#items.apocalypse_firstlight:raw_sphalerite.unit_mass_kg" u="kg" /> | 16 |

Raw ore has no use yet. Later it is hand-sorted, crushed and washed to concentrate, then smelted

- <StatusTag s="planned" /> Batch two: nickel-copper sulfide, pegmatite, greisen, bauxite
- <StatusTag s="planned" /> Signs of ore: rust-coloured gossans, coal seams showing in river banks, ore fragments washed down rivers, abandoned mines
- <StatusTag s="test" /> The old ore blocks (Galena Ore, Sphalerite Ore and the rest) can no longer be obtained; those already placed in a save stay. Breaking Galena Ore or Sphalerite Ore gives the matching raw ore

## Loose Stones

Out in the wild a few stones lie on the ground here and there: some pebbles, sometimes a bigger stone. On riverbeds and banks they are river cobbles, worn rounder, flatter and smoother by the water

- <StatusTag s="test" /> Loose stones lie thickest in patches on the floors of entrance hollows and on rockfall heaps, thinning out up the sides. At entrances in limestone or dolomite there is sometimes a flint among them

- Aim at loose stones and press Use once to pick one up: the big stone first, then the flint, then the pebbles one by one
- No tool needed, bare hands will do. The stones you pick up count towards your load
- With the round point shovel in hand, Use digs. Digging where loose stones lie knocks them out as items

## Stones, Pebbles and Flint

| Item | Where from | Weight | Stack |
|---|---|---|---|
| <ItemIcon id="field_stone" /> Granite Stone | Granite and natural stone, granite boulders and loose stones, river cobbles | <V d="item_mass/core_v1#items.apocalypse_firstlight:field_stone.unit_mass_kg" u="kg" /> | 8 |
| <ItemIcon id="gneiss_stone" /> Gneiss Stone | Gneiss | <V d="item_mass/core_v1#items.apocalypse_firstlight:gneiss_stone.unit_mass_kg" u="kg" /> | 8 |
| <ItemIcon id="schist_stone" /> Schist Stone | Schist | <V d="item_mass/core_v1#items.apocalypse_firstlight:schist_stone.unit_mass_kg" u="kg" /> | 8 |
| <ItemIcon id="sandstone_stone" /> Sandstone Stone | Sandstone, its boulders and loose stones | <V d="item_mass/core_v1#items.apocalypse_firstlight:sandstone_stone.unit_mass_kg" u="kg" /> | 8 |
| <ItemIcon id="limestone_stone" /> Limestone Stone | Limestone, its boulders and loose stones | <V d="item_mass/core_v1#items.apocalypse_firstlight:limestone_stone.unit_mass_kg" u="kg" /> | 8 |
| <ItemIcon id="dolomite_stone" /> Dolomite Stone | Dolomite | <V d="item_mass/core_v1#items.apocalypse_firstlight:dolomite_stone.unit_mass_kg" u="kg" /> | 8 |
| <ItemIcon id="shale_shard" /> Shale Shard | Shale, its boulders and loose stones | <V d="item_mass/core_v1#items.apocalypse_firstlight:shale_shard.unit_mass_kg" u="kg" /> | 16 |
| <ItemIcon id="marl_lump" /> Marl Lump | Marl | <V d="item_mass/core_v1#items.apocalypse_firstlight:marl_lump.unit_mass_kg" u="kg" /> | 8 |
| <ItemIcon id="rock_salt_lump" /> Rock Salt Lump | Rock salt | <V d="item_mass/core_v1#items.apocalypse_firstlight:rock_salt_lump.unit_mass_kg" u="kg" /> | 8 |
| <ItemIcon id="pebble" /> Pebble | Loose stones, broken boulders | <V d="item_mass/core_v1#items.apocalypse_firstlight:pebble.unit_mass_kg" u="kg" /> | 32 |
| <ItemIcon id="flint" /> Flint | Now and then among loose stones | <V d="item_mass/core_v1#items.apocalypse_firstlight:flint.unit_mass_kg" u="kg" /> | 16 |

Flint is rare, mostly in limestone country and in river gravel. It is dark with a waxy sheen, unlike the pale granite around it. Flint from the hills still wears a white rind; flint washed down a river has had it worn away

River cobbles in your hand are ordinary pebbles and granite stones, and stack with the ones from the fields (river stones come down from everywhere upstream, not from the rock of their bed)

These are all materials and none has a use yet. Building will use materials to build Mesh directly; stones will never be made into blocks

- <StatusTag s="planned" /> Loose stones placed by world generation: forests, fields, riverbeds
- <StatusTag s="test" /> Vanilla stone, cobblestone, stone bricks, andesite, granite and diorite, with their stairs, slabs and walls, can no longer be obtained, and neither can vanilla flint. Gravel drops only gravel
- <StatusTag s="test" /> Natural stone has a new rock texture (4 of them, mixed at random); only its look changed
