# Fuel and Refueling

AFL measures liquids by real volume: **1 mB = 1 litre**, and screens always show litres (L). Vanilla buckets can't carry fuel

## Fuels

| Fuel | Density | Used by |
|---|---|---|
| Gasoline | 0.74 kg/L | Nothing runs on gasoline yet |
| Diesel | 0.84 kg/L | T15, diesel generator, portable generator |

## Containers

<StatusTag s="creative" />

| Container | Capacity | Notes |
|---|---|---|
| Jerry can | 20 L | Hold it and pour into a filler, about 4 L/s |
| Small fuel drum | 60 L | Placed on the ground; use a hand pump |
| Fuel drum | 200 L | Placed on the ground; use a hand pump |
| Hand fuel pump | — | Sits on a drum, 1 L every <V j="HandFuelPumpBlockEntity.TICKS_PER_LITRE" u="ticks" /> (2 L/s) |

Containers placed in buildings start with an amount of fuel set by the building data

## Refueling the T15

1. Open the fuel filler on the rear left side
2. Hold Use with a diesel jerry can at the filler, or use a dispenser nozzle
3. A nozzle delivers about 10 L/s

The wrong fuel won't go in: the T15 takes diesel only

## Fuel stations

<StatusTag s="test" /> Station equipment works, but stations don't generate in the world yet

- **Fuel dispenser**: lift the nozzle; the hose reaches <V j="FuelDispenserBlockEntity.HOSE_LENGTH" u="blocks" /> and breaks away at <V j="FuelDispenserBlockEntity.BREAKAWAY" u="blocks" />. Needs power
- **Underground tank**: 3×3×7, holds <V j="UndergroundFuelTankBlockEntity.CAPACITY_MB" u="L" />, feeds the dispensers through a submersible pump
- **Canopy**: lit, needs power

Power: see [Building Power](../industry/power)
