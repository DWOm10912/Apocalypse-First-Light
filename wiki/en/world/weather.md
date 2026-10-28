# Weather

## Rain

<StatusTag s="verified" d="2026-10-17" />

- Without shaders AFL draws its own rain: slanted streaks that the wind pushes around, with the occasional gust
- Rain stops at roofs, tree crowns and vehicles, and splashes on the ground and on water
- Water gathers on vehicle windows and the wipers clear it. See [T15](../vehicles/t15#wipers)
- With a shader pack the pack handles rain

<Shot k="rain" zh="雨（截图待补）" en="Rain (screenshot coming)" />

## Lightning

<StatusTag s="verified" d="2026-10-18" />

- Thunderstorm lightning is drawn by AFL: one main channel with branches, and each flash strikes 2–5 times, the later strokes lighting only the main channel
- The ground around the strike lights up (without a shader pack) and the sky flashes
- Thunder is delayed by distance at <V j="AflLightning.SOUND_SPEED" u="m/s" />: count the seconds after the flash to tell how far away it struck
- Being struck does not always kill, see [Lightning injuries](../guide/survival#lightning-injuries)

### Where it strikes and lightning rods

- <StatusTag s="test" /> Lightning V2: on a branch only, checked in game 2026-10-22

- Where lightning lands follows the rolling sphere: each strike's striking distance is <V d="lightning/lightning_injury_v1#strike.distance.0" />–<V d="lightning/lightning_injury_v1#strike.distance.2" u="m" /> (most often <V d="lightning/lightning_injury_v1#strike.distance.1" u="m" />). The leader comes down from the cloud, and the first thing within that distance of it is struck. The taller and more alone something stands, the likelier it is struck; metal itself does not draw lightning
- A person alone on open ground is struck by leaders coming down within about 12.6 m of them at a striking distance of 45 m; a lone 20 m tree within about 37 m. Under a lone tree, the current comes down its trunk after the strike, and people beneath it are hurt by side flash or ground current
- There is no warning before a strike
- **Lightning rods**: two kinds, the copper air terminal (modern roofs: a solid copper rod about 1 m tall, braced by a tripod halfway up, its conductor laid along the roof) and the barn lightning rod (the twisted copper rod on a cast-iron tripod with a blue glass ball and an arrow vane, found on old barns and farmhouse ridges, 1.8 m and two blocks tall). The tip counts <V d="lightning/lightning_injury_v1#strike.rod_reach" u="m" /> higher than it is, so it draws the strikes that would have landed on the roof or on people near it. A strike on a rod lights no fire and goes to earth down its conductor, hurting nobody through the ground. For now they are in the Creative inventory only
- **Trees**: a struck tree always throws out splinters and its trunk cracks; with a chance of <V d="lightning/lightning_injury_v1#strike.fell" pct="true" /> the trunk bursts and the whole tree falls. Otherwise its crown may catch fire (<V d="lightning/lightning_injury_v1#strike.fire_wet" pct="true" /> in the rain, <V d="lightning/lightning_injury_v1#strike.fire_dry" pct="true" /> in a dry storm). The fire burns <V d="lightning/lightning_injury_v1#strike.fire_seconds.0" />–<V d="lightning/lightning_injury_v1#strike.fire_seconds.1" u="秒" ue="s" /> at most and the rain puts it out; it does not burn the crown away or spread through the crowns to other trees
- **T15**: the truck can be struck too (on its roof). It is not damaged and does not catch fire. With both doors shut, the people inside are unhurt; people outside it can still be hurt by side flash or ground current

### Thunder

- <StatusTag s="test" /> Thunder V1: on a branch only, checked in game by the user 2026-10-21

- Thunder comes in four bands by the distance to the nearest point of the channel, each its own recording: within <V j="Thunder.CRACK" u="m" /> a crack right overhead, within <V j="Thunder.CLOSE" u="m" /> a ripping crack that rolls into a boom, within <V j="Thunder.MID" u="m" /> a dull boom and a long roll, beyond that only a low rumble. It gets quieter with distance and you can tell which side it comes from
- Strikes within <V j="LightningStrikes.FAR_RANGE" u="m" /> are heard, and far ones beyond the view distance are seen too (without a shader pack); strikes farther than <V j="AflLightning.SKY_RANGE" u="m" /> no longer light up the whole sky
- Indoors, or in the T15's cab with the doors shut, thunder and rain sound muffled, only the low part gets through; with a door open, or near a doorway, it is clearer
- A strike within <V j="LightningStrikes.NOISE_RADIUS" u="m" /> draws the infected like an explosion; distant thunder does not

## Regional weather

- <StatusTag s="test" /> On a branch only, checked in game by the user 2026-10-22

- The overworld no longer rains or clears everywhere at once. The weather in each place follows from the world seed and the game time and keeps pace with the game's day and night (see [Day and night](#day-and-night)); there are no seasons
- The weather comes in regimes lasting half a day to a few days: clear, unstable, front, overcast rain, windy, cold snap and heat wave. Regimes blend over about 6 game hours
- Rain comes from moving rain cells: showers about 1 km in radius, thunderstorms 1–2.5 km, drifting with the wind. Thunderstorms peak in the afternoon and form more over plains and mountains, less on plateaus and at sea; a front sweeps a line of storms across the whole Federation
- Terrain shapes the weather: the western plateau is driest, the south hot and wet; mountains and plateaus are windiest, hills the calmest
- It counts as raining from <V j="RegionalWeather.WET" u="mm/h" />: you get wet and fires go out. The rain and sky you see follow where you stand
- Thunderstorms strike: nearby strikes are real lightning, far ones are only a flash and thunder
- Rain and storms lower the air temperature, and cold snaps and heat waves cool or warm a whole region
- Without shaders the rain's slant and drift follow the local wind
- Known issues: you can't sleep during a daytime thunderstorm; no snow cover or ice; at the edge of a rain cell the rain doesn't thin out in front of you
- Dev command: `/afl weather` shows the weather where you are; `regime <regime> [hours]`, `storm`, `shower` and `reset` are for testing

<StatusTag s="planned" /> Wind reaching more things: wind sounds, particles, being pushed on windy days, wind turbines

## Standing and running water

- <StatusTag s="test" /> On a branch only; first look in game 2026-10-24 (flow right). Without shaders the water now has depth colour, refraction, sky reflection and ripples, not checked in game yet

- Water is stored by real volume: how much a pit holds and where its surface stands follow from the pit's shape (smoothed terrain as its slopes are drawn) and the amount of water; water runs downhill and spills only at the lowest point of a pit's rim
- For now water comes only from dev commands: `/afl dev water fill <litres>` pours where you look, `drain <litres>` takes it out, `here` reads the column at your feet, `rain <mm/h> [radius]` makes test rain around you, `ledger` checks the totals, `perf` shows what the water costs a tick, `clear [radius]` removes the water around you
- Vanilla water is unchanged; natural rain filling pits and trenches drawing in river water are not connected yet
- <StatusTag s="test" /> You can swim and dive in AFL water: chest-deep water floats you, under it you hold your breath, and the camera gets the underwater fog; shallow water slows you down; running water pushes you, and dropped items drift with it. Boats do not float on AFL water yet
- <StatusTag s="test" /> Water in the air, not checked in game yet:
  - Water spilling over a pit's rim or a step falls only where the drop is half a metre or more: the water on top gathers to one place first: a little runs down the wall or drips, more leaves as one stream that thins as it falls and breaks into drops low down, a large flow as a sheet that comes apart at the bottom; on smooth hillsides it runs down the slope instead; without shaders water in the air is not drawn, only its rings where it lands in water; it joins the water below only when it lands
  - Landing on water it splashes and sends out rings; on dry ground it throws up small drops and leaves a wet patch that dries after a while
  - In the rain, open water shows raindrop rings; water under a roof or in a cave does not
  - Dev command `/afl dev water pour <litres> [litres/s]` pours where you look, `pour stop` stops
- <StatusTag s="test" /> The ground holds a thin film of water (about 4 mm on soil) that no longer runs; it soaks in and dries. Without shaders neither that film nor thin water running downhill is drawn, only standing water from 2 cm deep; rain water stands muddy: the ground shows through where it is shallow, a deep pit is a pool of grey-brown water, and rain splashes on its surface; water deeper than that in pits and ponds does not soak away yet
- <StatusTag s="test" /> `/afl dev water fill <litres>` raises the whole pool you look at at once
- <StatusTag s="test" /> Rain from the sky now reaches the world, not checked in game yet:
  - The game keeps how much rain each patch of open ground got and how wet it is: indoors and under roofs no rain lands, under trees the leaves catch some first. Without shaders the ground is not drawn darker; the wet look is left to the shader pack
  - Rain soaks into the soil first: dry ground takes it fast, soaked ground slower; what it cannot take stays where it fell for now and will run downhill later
  - Rain falling on existing water goes into it, so a pit that already holds water rises in the rain
  - Dev command `/afl dev water surface` reads the rain water on the column at your feet

<StatusTag s="planned" /> Rain collecting in pits (by catchment and soil infiltration); trenches that let river water in

## Rain and body temperature

- Rain lowers air temperature by <V d="temperature/temperature_v1#weather.rain" x="-1" u="°C" />, thunderstorms by <V d="temperature/temperature_v1#weather.thunder" x="-1" u="°C" />
- Rain soaks you in about a minute, and being wet makes you colder. See [Survival Stats](../guide/survival#temperature)

## Day and night

- <StatusTag s="test" /> On a branch only; the user tried it in game on 2026-10-24

- A day and night lasts <V j="WorldClock.DEFAULT_MINUTES" u="minutes" /> of real time (vanilla: 20 minutes): 30 minutes of day, 25 of night, 2 min 30 s each for dusk and dawn. A game hour is 2 min 30 s of real time
- The moon still goes through its phases in 8 days, about 8 real hours
- Only the sun, the moon and the date are slower; walking, shooting, vehicles, machines, fuel and power use still run in real time
- Sleeping still skips to the next morning, and the `/time` command works as usual
- A server can change the length of a day: `day_minutes` in the world's `serverconfig/apocalypse_firstlight-time.toml`, <V j="WorldClock.MIN_MINUTES" />–<V j="WorldClock.MAX_MINUTES" u="minutes" />, applied as soon as the file is saved
- The time of day is on a 24-hour clock, and the date turns at midnight: it gets light at 6:00 and dark around 18:00
- Developer commands: `/afl time` shows the date, the time of day (24-hour) and the length of a day; `/afl time set HH:MM` sets the time to that time today, e.g. `/afl time set 14:30`

## Dynamic lights

<StatusTag s="verified" d="2026-10-17" /> Without shaders the T15's lamps really light their surroundings (needs Embeddium). Flashlights and torches don't cast dynamic light yet

## Darkness

<StatusTag s="verified" d="2026-10-24" /> Where there is no light it is truly dark: in caves, unlit rooms and underground, light level 0 shows nothing, and turning up the brightness doesn't help; place torches, switch on lamps or use the truck's lights. Walk from the light into a cave and the far end darkens within half a second

<StatusTag s="verified" d="2026-10-24" /> How bright the night is outdoors depends on the moon phase: a full moon shows paths and the lie of the land, in faint grey-blue with little colour; a half moon barely shows the way; a new moon is nearly black and you need a light. The brightness setting brightens torches and lamps, not the moon
