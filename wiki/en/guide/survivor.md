# Survivor & Traits

A new world starts with picking a survivor: a past life (background), two body levels and a few traits. Points come out of the background's budget, and points left over are lost. Nothing can be changed once you start

- <StatusTag s="verified" d="2026-10-17" /> Background, body and trait setup screen
- <StatusTag s="verified" d="2026-10-18" /> Character page in the inventory (name, background, traits, body and growth levels)
- <StatusTag s="verified" d="2026-10-20" /> The character page's Status panel: every state affecting you now, each with its name, effect and time left; point at one to see where it comes from and how it goes. Many states or traits scroll with the mouse wheel

## Survivor mode

| Mode | After death |
|---|---|
| Normal | No respawn; you carry on as another survivor, generated at random |
| Hard | No respawn; the run is over. Worlds with vanilla hardcore on can only use Hard |

In both modes everything you carried stays on the body, and keepInventory does not help. The mode cannot be changed after the world is created

<StatusTag s="verified" d="2026-10-19" /> Normal mode's handover to the next survivor

## Backgrounds

Traits that come with a background are free and cannot be removed. In Normal mode the next survivor is drawn from the backgrounds marked "Successors", with body levels rolled between <V j="SurvivorSetup.SUCCESSOR_MIN" /> and <V j="SurvivorSetup.SUCCESSOR_MAX" /> and no extra traits

<BackgroundTable />

## Body

Each level of strength or fitness above the default costs 1 point, each level below gives 1 back. Both keep growing with use; see [Growth](./growth)

<LevelTable id="strength" />

<LevelTable id="fitness" />

## Traits

Good traits cost points, drawbacks give points back, at most <V j="SurvivorCatalog.NEGATIVE_POINT_CAP" /> in total. Traits that conflict cannot be taken together

<TraitTable />

Attribute values either multiply the base value (×0.9 is 10% less) or add to it. Cold, heat and firearms traits change no attribute: they start the matching [growth skill](./growth) at a higher level, or cap how far it can go
