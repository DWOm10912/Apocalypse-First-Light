# Infected

<StatusTag s="new" /> The infected currently use the vanilla zombie's body and stats. What AFL changes is how they see, hear and break in

## Stats

| | |
|---|---|
| Health | 20 |
| Attack | 3 |
| Speed | 0.23 |

- They don't burn in daylight
- They don't call reinforcements; babies grow up
- The infected are the only hostile mobs that spawn naturally

## Sight

- They see <V j="InfectedVisionSystem.MAX_VISION_DISTANCE" u="blocks" /> with a <V j="InfectedVisionSystem.HORIZONTAL_FOV_DEGREES" u="°" /> field of view, so approaching from behind works
- Within <V j="InfectedVisionSystem.CLOSE_AWARENESS_DISTANCE" u="blocks" /> they notice you without looking
- Walls block their view, and they need to watch you for a moment before locking on
- After losing sight they keep chasing for <V j="InfectedVisionSystem.LOST_VISUAL_GRACE_TICKS" x="0.05" u="s" />

## Hearing

Infected walk to where a sound came from, wait <V j="InvestigateNoiseGoal.WAIT_TICKS" x="0.05" u="s" />, then check <V j="InvestigateNoiseGoal.SEARCH_POINT_COUNT" /> spots within <V j="InvestigateNoiseGoal.SEARCH_RADIUS" u="blocks" />

How far your sounds carry:

| Sound | Range |
|---|---|
| Sneaking | 2 blocks |
| Walking | 4 blocks |
| Sprinting | 8 blocks |
| Landing | 5 / <V j="MovementNoiseEvents.HEAVY_LANDING_RADIUS" /> / <V j="MovementNoiseEvents.VERY_HEAVY_LANDING_RADIUS" u="blocks" /> |
| Pull-starting a generator | <V j="PortableDieselGeneratorBlockEntity.PULL_NOISE" u="blocks" /> |
| Vehicle engine | <V j="PickupTruckEntity.ENGINE_NOISE" u="blocks" /> |
| Horn | <V j="PickupTruckEntity.HORN_NOISE" u="blocks" /> |

- Each gun's noise range is in its stats table and drops sharply with a suppressor. See [Guns](../arms/)
- Wool and carpet muffle footsteps
- You hear sounds exactly as far as the infected do: if you can hear it, so can they

## Breaking in

Sounds of intensity <V j="InfectedBreachAuthorization.HIGH_INTENSITY_BREACH_THRESHOLD" /> or more make infected break blocks in their way to reach the source:

| Block | Time |
|---|---|
| Leaves, glass pane | 1 s |
| Glass | 1.5 s |
| Wooden trapdoor | 2 s |
| Fence gate | 2.25 s |
| Wooden door | 2.5 s |
| Wooden fence | 3 s |

Stone, iron doors and everything else hold

<Shot k="infected" zh="感染体（截图待补）" en="Infected (screenshot coming)" />

## Not yet

<StatusTag s="planned" /> AFL's own infected models, different infected types and threat levels
