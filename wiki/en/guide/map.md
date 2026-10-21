# Map

- <StatusTag s="verified" d="2026-10-21" /> <StatusTag s="creative" />

AFL has its own world map and minimap. Roads and towns are drawn along their real shape, so even angled ramps come out smooth

## The Map

A folded map and a pencil. Carry it (backpack, hotbar or grid inventory) and the places you walk through get drawn in

- Records the ground within <V j="MapClient.RADIUS" u="m" />: biome colour, relief, water
- Roads, towns and buildings need no recording: wherever you go, the roads and towns there show up. The old house and its gravel drive are on it too
- For now only in the Creative tab (Melee & Equipment); no recipe
- The record belongs to the map and stays when you leave the world and come back. Drop it and pick it up again and the record is still there; a new map starts blank. Lose the map and you lose the record

## Minimap

Top right of the screen. It turns with your view, forward is up and the N on the frame points north; the settings can switch it to north up

- Carrying a map: shows what the map has recorded
- Without a map: only a live circle of <V j="MapClient.RADIUS" u="m" /> around you, dark beyond it, nothing recorded
- <V j="MinimapHud.SPAN" u="m" /> across on foot; the faster you drive the further it zooms out, up to <V j="MinimapHud.SPAN_MAX" u="m" />
- Below it: your position, the biome and, in a town, the town's name
- Show or Hide Minimap is in the key settings, unbound by default

## Compass

- <StatusTag s="verified" d="2026-10-21" /> The compass at the top of the screen

A strip of headings at the top centre of the screen that turns with your view. A number every 15°, a letter every 45° (N, NE, E…), and under the middle the way you face in degrees: 0 is north, 90 is east

- Needs a pocket compass, in your hand or on a chest rig clip (see [Equipment](../arms/equipment)). Packed in a bag it shows nothing
- Hide it under Map in the settings

## World Map

Hold the map and right click: it opens like a book and unfolds into the world map. The game keeps running

| Input | Action |
|---|---|
| Left drag | Move |
| Scroll wheel | Zoom |
| Space | Back to me |
| Right click / Esc | Fold away |

- Places you have not been are dark
- Zoomed into a town you see street names, buildings and gas stations
- Town names, street names and route numbers on the map are in English, like the road signs. Pointing at a town shows a card with the name in your game language below
- Pointing anywhere else shows that spot's position and biome

<StatusTag s="planned" /> Markers; state road maps (once found, that whole state's roads and towns show on the map)
