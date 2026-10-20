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

## Rain and body temperature

- Rain lowers air temperature by <V d="temperature/temperature_v1#weather.rain" x="-1" u="°C" />, thunderstorms by <V d="temperature/temperature_v1#weather.thunder" x="-1" u="°C" />
- Rain soaks you in about a minute, and being wet makes you colder. See [Survival Stats](../guide/survival#temperature)

## Dynamic lights

<StatusTag s="verified" d="2026-10-17" /> Without shaders the T15's lamps really light their surroundings (needs Embeddium). Flashlights and torches don't cast dynamic light yet
