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

- The overworld no longer rains or clears everywhere at once. The weather in each place follows from the world seed and the game time; a day is 20 minutes as in vanilla, and there are no seasons
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

## Rain and body temperature

- Rain lowers air temperature by <V d="temperature/temperature_v1#weather.rain" x="-1" u="°C" />, thunderstorms by <V d="temperature/temperature_v1#weather.thunder" x="-1" u="°C" />
- Rain soaks you in about a minute, and being wet makes you colder. See [Survival Stats](../guide/survival#temperature)

## Dynamic lights

<StatusTag s="verified" d="2026-10-17" /> Without shaders the T15's lamps really light their surroundings (needs Embeddium). Flashlights and torches don't cast dynamic light yet
