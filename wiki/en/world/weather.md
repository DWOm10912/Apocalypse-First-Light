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
- A direct hit kills

<StatusTag s="planned" /> High places and metal drawing strikes, lightning splitting trees, sheltering from lightning in a car

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
