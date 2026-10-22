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

<StatusTag s="planned" /> Regional dynamic weather: rain areas moving in from afar, different weather in different places, wind rising before a storm, and later windy days

## Rain and body temperature

- Rain lowers air temperature by <V d="temperature/temperature_v1#weather.rain" x="-1" u="°C" />, thunderstorms by <V d="temperature/temperature_v1#weather.thunder" x="-1" u="°C" />
- Rain soaks you in about a minute, and being wet makes you colder. See [Survival Stats](../guide/survival#temperature)

## Dynamic lights

<StatusTag s="verified" d="2026-10-17" /> Without shaders the T15's lamps really light their surroundings (needs Embeddium). Flashlights and torches don't cast dynamic light yet
