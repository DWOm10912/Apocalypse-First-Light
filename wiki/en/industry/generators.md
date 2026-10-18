# Generators

<StatusTag s="creative" />

An AFL "hour" is one in-game hour (<V j="DieselGeneratorBlockEntity.HOUR_TICKS" u="ticks" />); 1 FE/t counts as <V j="DieselGeneratorBlockEntity.KW_PER_FE_TICK" u="kW" />

## Diesel Generator

A large fixed generator that connects to a building through a steel power port

| | |
|---|---|
| Rated output | <V j="DieselGeneratorBlockEntity.RATED" u="FE/t" /> (<V j="DieselGeneratorBlockEntity.RATED" x="0.25" u="kW" />) |
| Tank | <V j="DieselGeneratorBlockEntity.TANK" u="L" />, diesel only |
| Low fuel warning | <V j="DieselGeneratorBlockEntity.LOW_FUEL" u="L" /> |
| Fuel use | <V j="DieselGeneratorBlockEntity.L_PER_KW" u="L" /> per kWh + <V j="DieselGeneratorBlockEntity.IDLE_L" u="L" /> per hour at idle |
| Noise | Cranking <V j="DieselGeneratorBlockEntity.CRANK_NOISE" u="blocks" />, running <V j="DieselGeneratorBlockEntity.RUN_NOISE" u="blocks" /> |

- It burns idle fuel even with no load; switch it off when you don't need it
- Starts with a key

## Portable Generator

A small pull-start generator with <V j="PortableDieselGeneratorBlockEntity.SOCKETS" /> built-in sockets that power appliances directly through cords

| | |
|---|---|
| Rated output | <V j="PortableDieselGeneratorBlockEntity.RATED" u="FE/t" /> (<V j="PortableDieselGeneratorBlockEntity.RATED" x="0.25" u="kW" />) |
| Tank | <V j="PortableDieselGeneratorBlockEntity.TANK" u="L" />, diesel only |
| Fuel use | <V j="PortableDieselGeneratorBlockEntity.L_PER_KW" u="L" /> per kWh + <V j="PortableDieselGeneratorBlockEntity.IDLE_L" u="L" /> per hour at idle |
| Noise | Pull <V j="PortableDieselGeneratorBlockEntity.PULL_NOISE" u="blocks" />, running <V j="PortableDieselGeneratorBlockEntity.RUN_NOISE" u="blocks" /> |

**Pull start**: your view locks and a timing bar appears; press the key in the good zone for a good pull. Each pull costs <V d="stamina/stamina_v1#costs.recoil_pull" /> stamina

| | Good pull | Poor pull |
|---|---|---|
| Cold engine | <V j="PortableDieselGeneratorBlockEntity.COLD_GOOD" pct /> to start | <V j="PortableDieselGeneratorBlockEntity.COLD_POOR" pct /> |
| Warm engine | <V j="PortableDieselGeneratorBlockEntity.WARM_GOOD" pct /> | <V j="PortableDieselGeneratorBlockEntity.WARM_POOR" pct /> |

The engine stays warm for <V j="PortableDieselGeneratorBlockEntity.WARM_TICKS" x="0.05" u="s" /> after it stops. Plugging in more than the rated load trips it

## Thermal Generator

Burns coal or lava for power

| | |
|---|---|
| Generation | <V d="machine_balance/thermal_generator#generation_fe_per_tick" u="FE/t" /> |
| Storage | <V d="machine_balance/thermal_generator#capacity_fe" u="FE" /> |
| Coal / charcoal | <V d="machine_balance/thermal_generator#fuels.minecraft:coal.energy_fe" u="FE" /> |
| Coal block | <V d="machine_balance/thermal_generator#fuels.minecraft:coal_block.energy_fe" u="FE" /> |
| Lava bucket | <V d="machine_balance/thermal_generator#fuels.minecraft:lava_bucket.energy_fe" u="FE" /> |
| Lava tank | <V j="ThermalFuelDefinitions.TANK_CAPACITY_MB" u="L" /> |

It stops burning when full
