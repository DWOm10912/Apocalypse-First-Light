# Arms & Gear

Every AFL gun is modelled from scratch and driven by data files. The table below and each gun's page read the game's `native_guns` data directly, so when the game changes a number, this site follows

<GunTable />

## Basics

| Key | Action |
|---|---|
| Left mouse | Fire |
| Right mouse | Aim |
| R | Reload. Guns with magazines: tap to remove the magazine, hold to swap magazines |
| B | Switch fire mode (guns with two or more modes) |
| V | Inspect |
| Z | Field attachment change |

- <StatusTag s="verified" d="2026-10-22" /> With a gun in your hand, its keys are listed at the top right (like the driving key hints), as you have them bound; the reload line says what pressing it would do right now. Caps Lock hides and shows them, the same setting as when driving

- Reloading an empty gun is an empty reload, slower than a tactical one
- <StatusTag s="verified" d="2026-10-19" /> A gun you have just got is empty: nothing in the magazine or the chamber, so reload it first. A finished empty reload chambers a round
- <StatusTag s="verified" d="2026-10-19" /> P9-01: with rounds in the magazine but none chambered (an empty reload cut short, for example), the next draw racks the slide first, and you cannot fire or aim until that is done
- Guns without magazine items (HR-55, Silverwood 12): loose rounds come from the hotbar and main inventory first, then pockets, rig and backpack. In creative these guns have unlimited ammo
- <StatusTag s="verified" d="2026-10-20" /> The P9-01 reloads with magazine items; see Magazines below
- <StatusTag s="verified" d="2026-10-22" /> The BR-51 reloads with magazine items too. Its reloads do not change with your level yet: every level gets the same tactical and empty reload, and the empty one ends by pulling the operating rod handle to chamber a round

## Magazines

<StatusTag s="verified" d="2026-10-20" /> The P9-01 and the BR-51 (BR-51 <StatusTag s="verified" d="2026-10-22" />). Rounds live in the magazine and you can only use what is loaded in it; carrying loose rounds is not enough to reload

| Magazine | Capacity |
|---|---|
| <ItemIcon id="p9_01_magazine" /> P9-01 17-Round Magazine | 17 rounds |
| <ItemIcon id="p9_01_extended_magazine" /> P9-01 28-Round Extended Magazine | 28 rounds |
| <ItemIcon id="br51_01_magazine" /> BR-51 20-Round Magazine <StatusTag s="verified" d="2026-10-22" /> | 20 rounds |
| <ItemIcon id="br51_01_extended_magazine" /> BR-51 30-Round Extended Magazine <StatusTag s="verified" d="2026-10-22" /> | 30 rounds |
| <ItemIcon id="br51_drum_magazine_50" /> BR-51 50-Round Drum <StatusTag s="verified" d="2026-10-22" /> | 50 rounds |

- The gun holds one magazine plus a round in the chamber: the P9-01 up to 17+1 with the standard magazine and 28+1 with the extended one, the BR-51 20+1, 30+1 or 50+1. With no magazine fitted, only the chambered round can be fired
- A magazine keeps its own round count, shown on its item bar and tooltip. Empty magazines stack; loaded ones do not
- <StatusTag s="verified" d="2026-10-22" /> The reload key (R by default) has a tap and a hold: a **tap** (press and let go) takes the gun's magazine out; a **hold** (about 0.3 s) swaps in another magazine. With no magazine in the gun, a tap puts one in too
- Reloads only look in your chest rig and pockets, never the backpack, hotbar or main inventory. The fullest magazine goes in (on a tie, the one in the rig), even if it holds fewer rounds than the one in the gun. With no magazine to swap to on you, a hold just shows "No spare magazine". Creative reloads follow the same rule
- What is in the backpack is only reached through the inventory: move the magazine you want into your rig or pockets first. With a magazine only in the backpack, a hold just shows "Magazine in backpack"
- **Removing the magazine**: a tap on the reload key takes the magazine out (a full one too) with its remaining rounds and puts it in your hand (the hand slot). The gun stays where it was and the chambered round stays in it. Taking it out is a short action of about 1 s; switch items before it ends and the magazine stays in the gun. Once the gun is empty and you have no magazine to swap to, the HUD shows "Remove magazine". Load it in your hand; switch to another slot and it goes back on you, or place it yourself on the inventory page
- The magazine that comes out keeps its rounds and goes where the new one was. If it does not fit there, it goes by the pickup order, and if there is still no room it drops
- The chambered round stays in the gun: 3+1 swapped for a 15-round magazine gives 15+1
- Inspecting a gun with no magazine in it shows the gun and the empty magwell, without taking a magazine out and putting it back
- <StatusTag s="verified" d="2026-10-22" /> Reloading a gun with no magazine in it goes straight for the new magazine without first miming the old one coming out, so it is quicker than the usual reload: 0.1 s for the P9-01, 0.65 s for the BR-51
- The HUD's big number is the rounds in the magazine, with a small "+1" when a round is chambered. Reserve is the total in usable magazines in your rig and pockets
- Changing magazines at the maintenance bench keeps each magazine's rounds
- **Loading**: hold an empty or part-loaded magazine in your main hand and press Use (right mouse by default) to start loading; press it again to stop. One round goes in each second, taken from loose rounds anywhere on you, backpack included. While loading, the view tilts down to the magazine in your hands and goes back when you stop. You can walk while loading; it stops when you sprint, swap the magazine out of your hand, fill it or run out of rounds
- The creative tab has each magazine twice, empty and full. Loading in creative uses no rounds
- <StatusTag s="planned" /> Showing carried magazines on the chest rig is planned
- <StatusTag s="integrated" /> In old saves the BR-51's 35-round extended magazine becomes the 30-round one
- Reloading and firing cost stamina; when you are tired, your aim sways and the spread opens up
- Bullets stop at ordinary blocks; only glass tagged as breakable is shot through. Hits leave bullet holes that fade after a few minutes

## Accuracy

Spread is a cone around the crosshair, scaled by stance:

<AccuracyTable />

## Noise

Every gun has a noise radius: infected inside it hear the shot and come to look. A suppressor cuts the radius to <V j="NativeSuppressorItem.DEFAULT_NOISE_MULTIPLIER" pct />, and other players stop hearing it past <V j="NativeGunNoise.SUPPRESSED_SOUND_RADIUS" u="blocks" />. See [Infected](/en/infected/)

## How to get them

<StatusTag s="creative" /> Guns, ammo, attachments and the maintenance bench are creative-only for now; survival has no recipes or loot for them yet
