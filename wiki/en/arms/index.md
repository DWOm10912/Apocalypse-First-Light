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
- <StatusTag s="verified" d="2026-10-19" /> A gun you have just got is empty: nothing in the magazine or the chamber, so reload it first. A finished empty reload chambers a round
- <StatusTag s="verified" d="2026-10-19" /> P9-01: with rounds in the magazine but none chambered (an empty reload cut short, for example), the next draw racks the slide first, and you cannot fire or aim until that is done
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
