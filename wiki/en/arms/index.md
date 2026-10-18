# Arms & Gear

Every AFL gun is modelled from scratch and driven by data files. The table below and each gun's page read the game's `native_guns` data directly, so when the game changes a number, this site follows

<GunTable />

## Basics

| Key | Action |
|---|---|
| Left mouse | Fire |
| Right mouse | Aim |
| R | Reload |
| B | Switch fire mode (guns with two or more modes) |
| V | Inspect |
| Z | Field attachment change |

- Reloading an empty gun is an empty reload, slower than a tactical one
- Rounds come from the hotbar and main inventory first, then pockets, rig and backpack. Creative has unlimited ammo
- Reloading and firing cost stamina; when you are tired, your aim sways and the spread opens up
- Bullets stop at ordinary blocks; only glass tagged as breakable is shot through. Hits leave bullet holes that fade after a few minutes

## Accuracy

Spread is a cone around the crosshair, scaled by stance:

<AccuracyTable />

## Noise

Every gun has a noise radius: infected inside it hear the shot and come to look. A suppressor cuts the radius to <V j="NativeSuppressorItem.DEFAULT_NOISE_MULTIPLIER" pct />, and other players stop hearing it past <V j="NativeGunNoise.SUPPRESSED_SOUND_RADIUS" u="blocks" />. See [Infected](/en/infected/)

## How to get them

<StatusTag s="creative" /> Guns, ammo, attachments and the maintenance bench are creative-only for now; survival has no recipes or loot for them yet
