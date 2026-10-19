# Roads

<StatusTag s="new" />

The six states are linked by two road classes. Pavement, guardrails, markings and signs are all AFL blocks

| Class | Lanes | Speed limit |
|---|---|---|
| Interstate | 4 lanes, two each way | 110 km/h |
| State highway | 2 lanes, one each way | 90 km/h |

- Interchanges are still being built, so you can't switch between highways yet

## Tunnels

- Roads tunnel through mountains in the way, with 5.1 m clearance
- Tunnels are unlit; turn your headlights on
- Each portal has a name plate
- Tunnel fixtures such as lights and signs can be removed with a crowbar

## County Road and Farmhouse Driveway

<StatusTag s="test" /> Test worlds only

- The county road is a two-lane country blacktop that runs past the Creek Valley Farmhouse. Each end joins Linden's streets or a state highway
- Where it meets a state highway there is a T junction with a STOP sign on the county road
- The farmhouse has a gravel driveway that follows the creek for <V j="RoadCounty.DRIVE_MIN" />–<V j="RoadCounty.DRIVE_MAX" u="m" /> and joins the county road from the side. There is no STOP sign at the driveway
- The driveway is at most <V j="RoadCounty.DRIVE_GRADE" pct /> steep. The last stretch before the road is gentle, with a clear view both ways
- If one end of the county road can't reach another road, it stops about <V j="RoadCounty.DEAD_END_RUN" u="m" /> past the driveway

<StatusTag s="planned" /> The tube farm gate, mailbox and PRIVATE DRIVE sign at the driveway, and the ROAD ENDS barrier at a dead end: their spots are set, but they aren't placed yet

## Driving

See [T15](../vehicles/t15)
