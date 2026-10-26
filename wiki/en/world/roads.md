# Roads

<StatusTag s="verified" d="2026-10-17" />

The six states are linked by two road classes. Pavement, guardrails, markings and signs are all AFL blocks

| Class | Lanes | Speed limit |
|---|---|---|
| Interstate | 4 lanes, two each way | 110 km/h |
| State highway | 2 lanes, one each way | 90 km/h |

## Junctions and interchanges

- Two state highways meet at grade, as a T or a crossroads, with warning signs before it
- Where a state highway meets an interstate there is a diamond interchange: the state highway crosses over the interstate and four ramps lead up and down. Advance signs and exit signs come before each exit, and exits are numbered by milepost
- Where two interstates meet there is a trumpet interchange. Before the split, a sign above each lane shows which road that lane goes to
- Where an off-ramp meets the state highway there are DO NOT ENTER and WRONG WAY signs: don't drive up an off-ramp onto the interstate
- F-40 has an exit to Linden, see [Places](./places#towns)

## Road Cuts and Embankments

<StatusTag s="test" /> Test worlds only, not checked in game yet

- The ground under the roadbed and its cut and fill slopes is solid for <V j="RoadWorks.ROCK" u="m" />: no cave mouths open in a cut face and no springs run out beside the road
- The natural hillside beyond the slopes is unchanged and still has caves and springs
- Deep cuts are rock cuts: steep rock faces with a <V j="CrossSection.ROCK_BENCH" u="m" /> bench every <V j="CrossSection.ROCK_LIFT" u="m" /> of height. Shallow cuts keep their grass slopes
- Beside the interstates, a very deep cut can still end in a lower sheer wall at its outer edge
- <StatusTag s="verified" d="2026-10-25" /> The grass slopes beside a road follow the designed ditch and slopes, and only blend into the natural ground within <V j="RoadWorks.JOIN" u="m" /> of where they meet it: no block-high lumps in the ditch, and stray faces between neighbouring grass slopes are rare (a few remain at the edge of steep rock cuts)
- <StatusTag s="verified" d="2026-10-25" /> The turf on the grass slopes beside a road is the same as the turf on the topsoil next to it: the same tufts, the same biome colour, and soil, not vanilla's grass fringe, at its edges. In worlds created earlier the road slopes use this turf too; the vanilla grass blocks beside them stay
- <StatusTag s="verified" d="2026-10-25" /> Between two curves turning opposite ways, an undivided road's whole pavement tilts from one side over to the other, so no water lies along its centre line

## Digging Up a Road

<StatusTag s="verified" d="2026-10-25" /> Every new world has it

- With a <ItemIcon id="pick_mattock" /> pick mattock in hand, press Use on the asphalt: each strike breaks off about <V j="RoadDamage.STRIKE_LITRES" u="L" /> of asphalt as <ItemIcon id="asphalt_chunk" /> asphalt chunks of about <V j="RoadSurfaceBlock.ASPHALT_CHUNK_LITRES" u="L" /> and <V d="item_mass/core_v1#items.apocalypse_firstlight:asphalt_chunk.unit_mass_kg" u="kg" /> each. Bare hands and other tools cannot dig a road
- Asphalt breaks in 12.5 cm pieces: each strike knocks out an irregular piece where it lands, a hole widens from its rim, its edge is ragged, its sides are the asphalt's broken edge and its floor the aggregate base. A wheel drops into the hole, and you can stand in it
- The walls of a whole-block hole (by command or explosion) show the layers: the asphalt course on top, the aggregate base below. Interstates and ramps have the thickest asphalt and county roads the thinnest; parking pads use <V j="CrossSection.PAD_ASPHALT" x="100" u="cm" />
- Under the asphalt lies AFL gravel and backfill instead of vanilla gravel, cobblestone and dirt. The farmhouse driveway is a layer of round gravel
- Road blocks are not in the creative inventory; they can only be given by command
- Once all the asphalt of a block (1 m × 1 m) is broken out, that block becomes ordinary ground: angular gravel you can dig down into with a <ItemIcon id="round_point_shovel" /> round point shovel (round gravel subbase below it, then backfill) and pour back into. Its edge along pavement that still has asphalt stays put, so roadside asphalt is never undercut and left hanging. A block with only part of its asphalt broken out keeps a gravel floor the shovel cannot move: break out the rest of that block's asphalt first
- <StatusTag s="planned" /> Water pooling in holes: the ground in a hole already sits at the asphalt's underside; it waits for physical water to be merged

## Tunnels

- Roads tunnel through mountains in the way, with 5.1 m clearance
- Tunnels are unlit; turn your headlights on
- Each portal has a name plate
- Tunnel fixtures such as lights and signs can be removed with a crowbar

## County Road and Farmhouse Driveway

<StatusTag s="verified" d="2026-10-19" /> Every new world has it

- The county road is a two-lane country blacktop that runs past the Creek Valley Farmhouse. Each end joins Linden's streets or a state highway
- Where it meets a state highway there is a T junction with a STOP sign on the county road
- The farmhouse has a gravel driveway that follows the creek for <V j="RoadCounty.DRIVE_MIN" />–<V j="RoadCounty.DRIVE_MAX" u="m" /> and joins the county road from the side. There is no STOP sign at the driveway
- <StatusTag s="verified" d="2026-10-25" /> The driveway reaches the county road from the farmhouse's side only; it never crosses the road and loops back to the far edge
- The driveway is at most <V j="RoadCounty.DRIVE_GRADE" pct /> steep. The last stretch before the road is gentle, with a clear view both ways
- If one end of the county road can't reach another road, it stops about <V j="RoadCounty.DEAD_END_RUN" u="m" /> past the driveway

- <StatusTag s="integrated" /> The driveway ends at the [Creek Valley Farmhouse](./places#creek-valley-farmhouse)
- <StatusTag s="integrated" /> A closed tube farm gate stands across the driveway mouth, its PRIVATE DRIVE sign facing the county road, with an empty rural mailbox by the road
- <StatusTag s="integrated" /> For now the gate can only face due north, east, south or west. Most county roads run at an angle, so the gate often sits askew across the driveway
- <StatusTag s="planned" /> The ROAD ENDS barrier at a dead end: its spot is set, but it has no model yet

## Town Intersections and Markings

<StatusTag s="test" /> Test worlds only, not checked in game yet

- Linden's intersections have STOP signs. At a T, the side street stops; at a crossroads, the minor street stops. Where Main St crosses the exit street, all four ways stop, with an ALL WAY plaque under each STOP sign
- Each stopped approach has a white stop line. Stop before it
- Intersections on Main St and the exit street have crosswalks at least <V j="TownControl.WALK" u="m" /> wide
- Main St and the exit street have a double yellow centre line. Don't cross it. Residential streets have no centre line
- Town STOP signs stand higher than highway ones, their bottom <V j="RoadSigns.TOWN_BOTTOM" u="m" /> above the ground, so people can walk under them
- On the way into town, a speed limit sign stands <V j="TownSigns.SPEED_IN" u="m" /> past the city limit sign: SPEED LIMIT <V j="TownSigns.SPEED_LIMIT" /> mph
- The city limit sign's third line is the town's population (POP)
- On a street name sign, the small number after the name is that block's hundred block, for example 600 at 6th St
- Like the highway signs, these can be removed with a crowbar

## Driving

See [T15](../vehicles/t15)
