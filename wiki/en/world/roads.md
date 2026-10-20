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

## Driving

See [T15](../vehicles/t15)
