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
