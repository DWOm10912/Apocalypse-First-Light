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

Held in hand it shows the current dose rate on a digital display and clicks. It measures the rate only, not how much dose you have built up

<StatusTag s="creative" />
