# Building Power

<StatusTag s="creative" />

A building's wiring is hidden in the walls: draw wires through the blocks with the wiring wrench, connect lights, switches and outlets to a distribution panel, then connect the panel to a generator

## Distribution panel

<ModelShot id="distribution_panel" zh="配电箱" en="Distribution panel" />

| | |
|---|---|
| Circuits | <V j="DistributionPanelBlockEntity.SLOTS" /> breaker slots |
| Storage | <V j="DistributionPanelBlockEntity.CAPACITY" u="FE" /> |
| Max in / out | <V j="DistributionPanelBlockEntity.MAX_IN" u="FE/t" /> / <V j="DistributionPanelBlockEntity.MAX_OUT" u="FE/t" /> |
| Trip | Opens after <V j="DistributionPanelBlockEntity.TRIP_TICKS" x="0.05" u="s" /> of overload |

Open the panel to see supply, load and every circuit's state, and switch circuits on or off

## Wiring wrench

- <ItemIcon id="wiring_wrench" />Drag across blocks with the wrench to lay wire, like Terraria's wiring; the wire hides inside the blocks
- <ItemIcon id="insulated_copper_wire" />Each block of wire uses 1 copper wire
- 4 colour-coded circuits, several of which can share a block
- Wires are only visible while you hold the wrench

## Outlets and cords

- <ItemIcon id="wall_outlet" /><ItemIcon id="power_strip_3" />Low-power appliances (lamps, small devices) plug into outlets with a cord
- High-power or fixed devices (like the diesel generator) use a steel power port
- Cord lengths: power strip <V j="PlugCord.STRIP_LENGTH" u="blocks" />, appliance <V j="PlugCord.APPLIANCE_LENGTH" u="blocks" />, to a generator <V j="PlugCord.GENERATOR_LENGTH" u="blocks" />

## Lights

- <ItemIcon id="porcelain_lampholder" /><ItemIcon id="keyless_lampholder" /><ItemIcon id="wall_switch" />Room lights use <V j="BuildingLights.FE_PER_TICK" u="FE/t" /> each; a switch controls the whole circuit
- <ItemIcon id="emergency_light" />**Emergency light**: built-in battery of <V j="EmergencyLightBlockEntity.CAPACITY" u="FE" />, charging <V j="EmergencyLightBlockEntity.CHARGE" u="FE" /> per tick while powered and drawing <V j="EmergencyLightBlockEntity.DRAIN" u="FE" /> per tick when the power fails (about 5 minutes when full)
- When the power fails, lights flicker before going out
