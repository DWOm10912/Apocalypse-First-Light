# Radiation

Fallout from the war still lies on the ground. The starting area is clean; the farther out you go, the higher the radiation

## Zones

| Zone | Dose rate |
|---|---|
| Safe | 0 |
| Irradiated | 1–10 RU/h |
| Heavy fallout | 10–60 RU/h |
| Extreme | 60–240 RU/h |

The dose rate is how much dose you build up per real-time hour. After leaving a zone you carry some residual radiation for a while; it fades only once you are back in a safe area

## Dose and radiation sickness

Your accumulated dose does not go down on its own. The higher it gets, the sicker you are

| Stage | Dose | Effect |
|---|---|---|
| 1 | ≥ <V j="RadiationSicknessManager.MILD_DOSE_THRESHOLD" /> | Movement −5% |
| 2 | ≥ <V j="RadiationSicknessManager.MODERATE_DOSE_THRESHOLD" /> | Movement −10%, healing ×0.75 |
| 3 | ≥ <V j="RadiationSicknessManager.SEVERE_DOSE_THRESHOLD" /> | Movement −15%, healing ×0.5, bouts of nausea |
| 4 | ≥ <V j="RadiationSicknessManager.CRITICAL_DOSE_THRESHOLD" /> | Movement −20%, healing ×0.25, more frequent nausea, 1 health every 5 s |

<StatusTag s="planned" /> Medicine for radiation sickness

## Shielding

Walls between you and the fallout block part of it:

- Each layer of reinforced concrete lets through only <V j="RadiationShielding.RC_TRANSMISSION" pct />
- Each layer of lead shielding bricks lets through only <V j="RadiationShielding.LEAD_TRANSMISSION" pct />
- Only blocks within <V j="RadiationShielding.MAX_DISTANCE" u="blocks" /> count

## Water

Dirty water carries the radiation of where you filled it, and boiling doesn't remove it. See [Survival Stats](./survival#thirst)

## Geiger counter

<ItemIcon id="geiger_counter" />Held in hand it shows the current dose rate on a digital display and clicks. It measures the rate only, not how much dose you have built up

<StatusTag s="integrated" /> Battery and switch:

- It runs on one 9 V battery. Right-click with it in hand to switch it on or off; switching on lights the whole display for <V j="GeigerCounterItem.SELF_TEST_TICKS" x="0.05" u="s" /> as a self-test, then the reading appears
- It draws power whenever it is on, in your hand, on a clip, in a backpack, in a chest or on the ground: 1 FE every <V j="GeigerCounterItem.TICKS_PER_FE" x="0.05" u="s" />. A fresh alkaline battery lasts about 3 game days, a NiMH rechargeable about 2. Switch it off when you are done
- The battery icon on the right of the display: 3 bars above 66%, 2 above 33%, 1 above 15%; below that only the outline is left, blinking, and a flat battery blanks the whole display
- Off or flat it stays silent, and the counter window on the HUD is not shown
- Batteries are changed on the [inspect screen](./inventory#inspecting-items): turn it over, open the battery cover, take the old battery out and fit a new one
- With a NiMH battery fitted the whole counter can go on a charging station tray; an alkaline battery cannot be charged, only replaced. The crosshair hint at the tray says why

### 9 V batteries

| | <ItemIcon id="battery_9v_alkaline" />Alkaline | <ItemIcon id="battery_9v_nimh" />NiMH rechargeable |
|---|---|---|
| Capacity | <V j="Battery9vItem.ALKALINE_CAPACITY" u="FE" /> | <V j="Battery9vItem.NIMH_CAPACITY" u="FE" /> |
| Runs the counter for | about 3 game days | about 2 game days |
| Charging | no | on a charging station tray, at most <V j="Battery9vItem.NIMH_MAX_RECEIVE" x="20" u="FE" /> a second; flat to full in about 2.5 minutes |
| Stacking | fresh full batteries stack up to 4 | does not stack |

The tooltip shows the charge left as a percentage

<StatusTag s="creative" />
