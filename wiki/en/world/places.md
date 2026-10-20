# Places

## Creek Valley Farmhouse

<StatusTag s="integrated" /> An old stone-and-timber house in the forest, with furniture, a stove and power. Every world created since 2026-10-19 has one, at the end of the county road's gravel driveway. Pick the farmhouse start and you begin at the foot of its porch steps

<Shot k="hero_building" zh="溪谷老宅（开发中）" en="Creek valley farmhouse (in development)" />

- <StatusTag s="verified" d="2026-10-19" /> The farmhouse plot sits at least <V j="RoadCounty.PLOT_HARD" u="m" /> from the county road, out of sight. A gravel driveway follows the creek to the road, see [Roads](./roads#county-road-and-farmhouse-driveway)

## Fuel stations

<StatusTag s="test" /> A North American roadside station: dispensers, canopy and underground tanks. The equipment works (see [Fuel and Refueling](../vehicles/fuel)) but stations don't generate in the world yet

## Towns

<StatusTag s="verified" d="2026-10-18" /> **Linden** is the first town with streets, in a valley beside the F-40 interstate. Take the Linden exit off F-40 and you enter town at the green city limit sign

- Streets have curbs, a planting strip and sidewalks; each intersection has two crossed street name signs. The street along the interstate is Front St, the main street is Main St, and the cross streets run 1st St, 2nd St… from west to east
- Blocks are split into lots: residential, downtown, highway commercial, industrial, mobile home park, civic, school, park and parking
- Every lot has a driveway to its street, so you can drive in and out without hopping the curb. A house driveway is <V j="TownPlan.DRIVE_HOUSE" u="m" /> wide, highway commercial lots have two of <V j="TownPlan.DRIVE_HWY" u="m" />, industrial lots <V j="TownPlan.DRIVE_IND" u="m" />; downtown and parks have none
- Each lot is levelled to a flat pad a little above the sidewalk, with gentle slopes between lots. Lots as wide as their whole block (school, park, parking) follow the ground
- The ground under the town is solid for <V j="TownWorks.ROCK" u="m" />, so you won't dig into caves below it
- Only worlds created with this version have Linden

<StatusTag s="planned" /> There are no buildings on the lots yet. The other 15 towns have their names decided and for now only appear on road signs

## Containers

Cabinets, shelves and fridges in buildings can be searched. See [Inventory and Weight](../guide/inventory#searching-containers)
