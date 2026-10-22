# Survival Stats

AFL's survival HUD replaces vanilla hearts, hunger and the experience bar: a round module in the lower left, with health on the left arc, stamina on the right arc and a body temperature gauge in the middle. Three small dials beside it show food, thirst and load. They stay small and faint, and grow, show a number and turn red when you are in trouble

<StatusTag s="integrated" /> These values are starting values and will be tuned

## Status badges

<StatusTag s="verified" d="2026-10-20" /> How the status badges announce themselves

A column of round badges above the survival module shows what affects you right now; nothing shows when nothing does. The colour is the kind: red dangerous, brown bad, blue cold or wet, green good. The most urgent sit closest to the module

- **What stays still**: the coloured disc, the highlight at the top left, the thin rim and the white icon do not move
- **A ring spreading out**: when you gain a state, or the same state gets worse (thirst turning into dehydration, cold going up a stage), a ring spreads out once (about 0.7 seconds) and the name and effect slide out to the right for 4 seconds
- **An updated line**: when only the effect line changes (a wound cleaned or bandaged, recovery switching from awake to resting), the name and effect slide out again for 4 seconds, without the ring
- **The outer arc**: a timed state (a stomach bug, petrol or diesel on you) shows the time left round the badge, with the time beside it; a cleaned infection or festering wound shows how far it has healed, and when the arc runs out it eases a stage or heals. A scratch has no arc
- **Pressing Left Alt**: every badge slides its name and effect out together for 4 seconds; once every 6 seconds at most (the key is "Show status labels" in the controls and can be changed)
- **Going away**: when a state ends its badge simply goes and the ones above slide down. If the same state comes back within 10 seconds, no worse than before, it is not announced again

For example, a scratch you clean and then bandage slides its line out once for each step ("Cleaned: less likely to become infected" → "Cleaned and bandaged") and the badge stays until the scratch heals. Warming up by a fire shows a green "Warming up" while you are still cold and your temperature is clearly rising; it goes once you are no longer cold, or once you are close to as warm as the fire gets you and the rise slows

## Health

- <StatusTag s="verified" d="2026-10-20" /> Health V1a

Maximum health is <V d="health/health_v1#max_health" />, the same for every survivor. Each segment of the health arc is 10, with "current/max" under the arc. Below 20% of the maximum the arc flashes

**Damage taken**: armour, resistance and absorption still work as in vanilla; what gets through is then converted to the 100 scale, once per hit. Only damage to players is converted: guns do the same to the infected as before

| Source | Health lost |
|---|---|
| Infected melee | Vanilla damage ×<V d="health/health_v1#damage.rules.0.scale" /> |
| Bullets (hitting a player) | P9-01 about <V d="health/health_v1#damage.rules.1.curve.1.1" />, BR-51 about <V d="health/health_v1#damage.rules.1.curve.2.1" />, HR-55 about <V d="health/health_v1#damage.rules.1.curve.3.1" /> |
| Lightning | ×<V d="health/health_v1#damage.rules.2.scale" />, always lethal |
| Falls | Vanilla damage ×<V d="health/health_v1#damage.rules.3.scale" /> |
| Everything else | Vanilla damage ×<V d="health/health_v1#damage.default_scale" /> |

**Natural healing**

- No healing for <V d="health/health_v1#regen.delay_seconds" u="s" /> after taking damage. Then <V d="health/health_v1#regen.awake_per_minute" /> a minute awake, <V d="health/health_v1#regen.rest_per_minute" /> a minute resting (asleep, in a chair, sitting on the ground, or riding in a vehicle you are not driving)
- No natural healing with food below <V d="health/health_v1#regen.min_food" />, while dehydrated, or at Cold 2 / Heat 2 and worse
- Sleeping through the night heals once for the time skipped, <V d="health/health_v1#regen.sleep_per_minute" /> a minute, at most <V d="health/health_v1#regen.sleep_max" />
- Vanilla's food healing is off

## Wounds

A melee hit from an infected has a <V d="wounds/wounds_v1#scratch_chance" pct /> chance to leave a scratch; you can have up to <V d="wounds/wounds_v1#max_wounds" /> wounds. Wounds show as status badges, do not progress while you are offline, and are gone after death

<StatusTag s="verified" d="2026-10-20" /> Every wound is on a body part: mostly the arms, then the chest, abdomen and legs, never the head. The body map in your inventory shows where (see [Inventory](./inventory#body-map))

| Stage | Effect | Progress |
|---|---|---|
| Scratch | No health lost | After about <V d="wounds/wounds_v1#scratch.check_days" /> game day, one roll for infection: <V d="wounds/wounds_v1#scratch.infect_chance" pct /> untreated, ×<V d="wounds/wounds_v1#scratch.cleaned_factor" /> cleaned, ×<V d="wounds/wounds_v1#scratch.bandaged_factor" /> bandaged, none if cleaned and bandaged. Heals in about <V d="wounds/wounds_v1#scratch.heal_days" /> game days, sooner when treated |
| Infected wound | Stamina recovery ×<V d="wounds/wounds_v1#infection.stamina_regen" />, now and then the view blurs for a few seconds | Each game day a <V d="wounds/wounds_v1#infection.worsen_chance" pct /> chance to fester (<V d="wounds/wounds_v1#infection.worsen_chance_bandaged" pct /> bandaged), <V d="wounds/wounds_v1#infection.resolve_chance" pct /> to clear up by itself. Once cleaned it gets better in about <V d="wounds/wounds_v1#infection.recover_days_cleaned" /> game day, <V d="wounds/wounds_v1#infection.recover_days_both" /> cleaned and bandaged |
| Festering wound | Stamina recovery ×<V d="wounds/wounds_v1#suppuration.stamina_regen" />, health recovery ×<V d="wounds/wounds_v1#suppuration.health_regen" />, a fever: colours look washed out, bright light glares, the blurring comes more often and harder with dizziness, and now and then a fit of chills | Rarely gets better uncleaned (<V d="wounds/wounds_v1#suppuration.ease_chance" pct /> a game day to ease to an infection). Once cleaned it eases to an infection first, then gets better as one |

A scratch scratched again gets worse and more likely to become infected. The screen effect can be turned down or off under Body in the AFL settings

**Medical supplies** (Creative inventory only for now, no recipe)

| Item | Use |
|---|---|
| <ItemIcon id="medical_alcohol" /> Medical Alcohol | Hold right-click for <V j="MedicalItem.ALCOHOL_TICKS" x="0.05" u="s" /> to clean one wound (the worst first) |
| <ItemIcon id="bandage" /> Bandage | Hold right-click to bandage one wound: <V j="MedicalItem.BANDAGE_TICKS" x="0.05" u="s" /> on an arm, <V j="MedicalItem.BANDAGE_HEAD_TICKS" x="0.05" u="s" /> on the head, <V j="MedicalItem.BANDAGE_TORSO_TICKS" x="0.05" u="s" /> on the chest or abdomen, <V j="MedicalItem.BANDAGE_LEG_TICKS" x="0.05" u="s" /> on a leg (head, chest, abdomen and leg times <StatusTag s="test" />). A festering wound must be cleaned first |
| <ItemIcon id="splint" /> Splint | <StatusTag s="verified" d="2026-10-20" /> Sets one fracture (legs first). A leg is splinted sitting on the ground: right-click standing and you sit down first; once sat, hold right-click for <V j="SplintItem.LEG_USE_TICKS" x="0.05" u="s" />, and you stay sitting after. An arm can be splinted standing or sitting: hold right-click for <V j="SplintItem.USE_TICKS" x="0.05" u="s" /> |

One is used per treatment; letting go early uses nothing; Creative uses nothing. Not while swimming, under water or driving

You see the whole treatment, the same moves in first and third person; in first person the view tips down to the arm. For a wound on an arm: for a bandage you hold out your left forearm and wind the roll round it three times, the cloth pulling off the roll onto the arm, then press the end down; for medical alcohol you unscrew the cap and hold it in your left hand, pour the alcohol over your left forearm and screw the cap back on

Once bandaged, the gauze stays wrapped round the wounded part, for you and for other players to see. It comes off when no wound is bandaged any more: the wound healed, an infection spoiled the dressing, or you died

<StatusTag s="verified" d="2026-10-20" /> The gauze goes on the wounded arm or leg

- <StatusTag s="test" /> A wound elsewhere is treated where it is (mirrored if you are left-handed):
  - Head: you press a gauze pad on the wound, then pass the roll from hand to hand behind your head, three turns round from the brow to the crown. With alcohol you tilt your head and pour on the side facing up
  - Chest and abdomen: a pad first, then the roll passed from hand to hand behind your back, three turns round the body. With alcohol you lean back a little and pour on the front
  - Leg: done sitting on the ground. Right-click standing and you sit down first; once sat, hold right-click, and you stay sitting after. To bandage you raise the knee and pass the roll under the shin; alcohol is poured down the shin
  - Chest and abdomen wounds are only ever on the front and the sides. The burn a slung gun leaves in a lightning strike is on your back, out of your own reach: you cannot clean or bandage it, it has to heal by itself, and the inventory body chart says so

- <StatusTag s="test" /> Wounds are drawn on the skin one step finer than the skin (4 × 4 to a skin pixel, crisp pixels like the skin itself), each in a fixed place: a scratch is three claw marks tapered at both ends, with beads of blood and scuffed skin, which scab over and leave thin pale lines; an infection turns the skin around it red and swollen; suppuration shows a few creamy beads of pus where the wound is deepest. As a wound heals its colour fades, and it is gone once healed. A bandaged wound on the head, chest or abdomen gets a gauze pad over it, taped at the sides, with a little blood soaking through (yellowish brown when infected); a bandaged arm or leg is still wrapped. The skin's second layer and armour hide what is under them. The inventory body chart shows the marks on the front

## Fractures

- <StatusTag s="verified" d="2026-10-20" /> Fractures and splints
- <StatusTag s="verified" d="2026-10-20" /> Sitting on the ground: N sits down; N, jump or sneak gets up. Only on a block's collision face (not in the air, in water or on a ladder). Sitting counts as rest. See [Controls](./controls)

A fall that costs health ends one of two ways:

- <StatusTag s="verified" d="2026-10-20" /> **Hard landing**: you stay on your feet and sink into your knees. Only a leg can break
- <StatusTag s="verified" d="2026-10-20" /> **Falling over**: you lose your footing, pitch forward onto one hand, stay on all fours a moment and get up. For those few seconds you cannot move or use anything. Only a fall-over can break an arm, the one you landed on

Chance to fall over = (health lost − <V d="health/health_v1#fracture.fall_over.chance.0" />) ÷ <V d="health/health_v1#fracture.fall_over.chance.1" />, at most <V d="health/health_v1#fracture.fall_over.chance.2" pct />, then adjusted by how you land:

- A leg already broken: ×<V d="health/health_v1#fracture.fall_over.broken_leg" />
- Carrying more than a light load: +<V d="health/health_v1#fracture.fall_over.load" pct />; jumping down at a sprint: +<V d="health/health_v1#fracture.fall_over.speed" pct />; landing on a block edge or a slope: +<V d="health/health_v1#fracture.fall_over.edge" pct />
- Holding sneak as you land: ×<V d="health/health_v1#fracture.fall_over.sneak" />
- A landing that breaks a leg always falls over

Fractures are judged by the health the fall actually took, so armour and landing in water make them less likely:

- **Leg** (random side): chance = (health lost − <V d="health/health_v1#fracture.leg_fall.0" />) ÷ <V d="health/health_v1#fracture.leg_fall.1" />, at most <V d="health/health_v1#fracture.leg_fall.2" pct />
- **Arm** (only when falling over, the hand you landed on): chance = (health lost − <V d="health/health_v1#fracture.arm_fall.0" />) ÷ <V d="health/health_v1#fracture.arm_fall.1" />, at most <V d="health/health_v1#fracture.arm_fall.2" pct />
- Breaking a broken limb again sets its healing back by <V d="health/health_v1#fracture.rebreak_setback" pct /> and knocks the splint off; it has to be set again

| Effect | Not splinted | Splinted |
|---|---|---|
| Leg: movement speed | ×<V d="health/health_v1#fracture.leg.move.0" /> | ×<V d="health/health_v1#fracture.leg.move.1" /> |
| Leg: jump height (still up one block) | ×<V d="health/health_v1#fracture.leg.jump.0" /> | ×<V d="health/health_v1#fracture.leg.jump.1" /> |
| Leg: sprinting | From <V d="health/health_v1#fracture.sprint_from.0" pct /> healed | From <V d="health/health_v1#fracture.sprint_from.1" pct /> healed |
| Leg: stamina used walking | ×<V d="health/health_v1#fracture.leg.stamina.0" /> | ×<V d="health/health_v1#fracture.leg.stamina.1" /> |
| Gun arm: aim sway | ×<V d="health/health_v1#fracture.main_arm.sway.0" /> | ×<V d="health/health_v1#fracture.main_arm.sway.1" /> |
| Gun arm: mining speed / melee damage | ×<V d="health/health_v1#fracture.main_arm.dig.0" /> / ×<V d="health/health_v1#fracture.main_arm.melee.0" /> | ×<V d="health/health_v1#fracture.main_arm.dig.1" /> / ×<V d="health/health_v1#fracture.main_arm.melee.1" /> |
| Other arm: aim sway | ×<V d="health/health_v1#fracture.support_arm.sway.0" /> | ×<V d="health/health_v1#fracture.support_arm.sway.1" /> |
| Arm: climbing ladders and vines | ×<V d="health/health_v1#fracture.main_arm.climb.0" /> | ×<V d="health/health_v1#fracture.main_arm.climb.1" /> |
| Natural healing ceiling (leg / arm) | <V d="health/health_v1#fracture.leg.cap.0" /> / <V d="health/health_v1#fracture.main_arm.cap.0" /> | <V d="health/health_v1#fracture.leg.cap.1" /> / <V d="health/health_v1#fracture.main_arm.cap.1" /> |

- With a broken leg, landing from 2 blocks or more hurts a little more
- **Healing**: splinted, a leg mends in about <V d="health/health_v1#fracture.leg_heal_days" /> game days, an arm in about <V d="health/health_v1#fracture.arm_heal_days" />; unsplinted it heals ×<V d="health/health_v1#fracture.unsplinted" /> as fast; resting ×<V d="health/health_v1#fracture.rest" />. From <V d="health/health_v1#fracture.fade_from" pct /> healed the effects wear off. Like wounds it only counts while you are online, and nights slept through count too
- The splint stays on that leg or arm and goes when the bone has mended. Death clears fractures
- <StatusTag s="verified" d="2026-10-20" /> A bone breaking makes an audible snap, heard by anyone within <V j="MovementNoiseEvents.VERY_HEAVY_LANDING_RADIUS" /> blocks
- <StatusTag s="verified" d="2026-10-20" /> Falling over makes a thump as your hand hits the ground, heard within <V j="MovementNoiseEvents.HEAVY_LANDING_RADIUS" /> blocks
- A broken arm does not slow reloading yet (later)

## Tinnitus

- <StatusTag s="test" /> Tinnitus V2: a negative state. Checked in game on its branch on 2026-10-21, waiting to be merged

Gunshots, explosions and lightning close by build up a hearing load, and past a point your ears start ringing. While you have tinnitus your ears ring now and then, in the left ear, the right or both; the higher the stage, the more often, and the quieter the sounds around you

| State | Hearing load | Between spells | Each spell | Sounds around you |
|---|---|---|---|---|
| Mild tinnitus | ≥ <V d="hearing/tinnitus_v2#stages.0" /> | <V d="hearing/tinnitus_v2#interval.0.0" />–<V d="hearing/tinnitus_v2#interval.0.1" u="秒" ue="s" /> | <V d="hearing/tinnitus_v2#length.0.0" />–<V d="hearing/tinnitus_v2#length.0.1" u="秒" ue="s" /> | ×<V d="hearing/tinnitus_v2#hearing.0" /> |
| Tinnitus | ≥ <V d="hearing/tinnitus_v2#stages.1" /> | <V d="hearing/tinnitus_v2#interval.1.0" />–<V d="hearing/tinnitus_v2#interval.1.1" u="秒" ue="s" /> | <V d="hearing/tinnitus_v2#length.1.0" />–<V d="hearing/tinnitus_v2#length.1.1" u="秒" ue="s" /> | ×<V d="hearing/tinnitus_v2#hearing.1" /> |
| Severe tinnitus | ≥ <V d="hearing/tinnitus_v2#stages.2" /> | <V d="hearing/tinnitus_v2#interval.2.0" />–<V d="hearing/tinnitus_v2#interval.2.1" u="秒" ue="s" /> | <V d="hearing/tinnitus_v2#length.2.0" />–<V d="hearing/tinnitus_v2#length.2.1" u="秒" ue="s" /> | ×<V d="hearing/tinnitus_v2#hearing.2" /> |

The hearing load tops out at <V d="hearing/tinnitus_v2#max_load" />. Each time the stage goes up, your ears ring at once

- **Gunshots**: each shot's load = 10^((level at the ear − <V d="hearing/tinnitus_v2#reference_db" />) ÷ 10). The level at the ear is in each gun page's stats, for example 170 dB is 1 a shot and 160 dB 0.1. A suppressor takes off <V d="hearing/tinnitus_v2#suppressor_db" u="dB" />. Other people's shots fall off with the square of the distance and do not count beyond <V d="hearing/tinnitus_v2#range" u="格" ue="blocks" />
- **Explosions**: load = <V d="hearing/tinnitus_v2#blast" /> × (blast radius ÷ <V d="hearing/tinnitus_v2#blast_radius" />)² ÷ distance², ignored below <V d="hearing/tinnitus_v2#blast_min" />. A lightning strike counts as a blast of radius <V d="hearing/tinnitus_v2#lightning_radius" />
- With no sky over your head (indoors) the load is ×<V d="hearing/tinnitus_v2#indoor" />
- Wearing [Simple Hearing Protection](../arms/equipment#tools), every load is ×<V j="SimpleHearingProtectionItem.IMPULSE" />; survivor traits can change the tinnitus multiplier too
- **Jolt**: a single load of <V d="hearing/tinnitus_v2#jolt_from" /> or more rings at once and drops the sounds around you to ×<V d="hearing/tinnitus_v2#jolt_hearing" />, coming back over <V d="hearing/tinnitus_v2#jolt_recover.0" />–<V d="hearing/tinnitus_v2#jolt_recover.1" u="秒" ue="s" />; the bigger the load, the slower
- **Recovery**: the hearing load halves every <V d="hearing/tinnitus_v2#half_life_ticks" x="0.05" u="秒" ue="s" />, <V d="hearing/tinnitus_v2#sleep" /> times faster asleep, and only counts while you are online. Death clears it
- The badge's ring shows how bad your ears are: gunfire and blasts fill it, and it shrinks as they recover. There is no countdown

## Lightning injuries

- <StatusTag s="test" /> Lightning injuries V1: on a branch only, not yet checked in game

Lightning goes for the tallest thing around: a person or creature is only picked when its head is at least as high as every block within <V d="lightning/lightning_injury_v1#target_radius" u="格" ue="blocks" />. Open ground and hilltops are the most dangerous; a taller tree or building nearby takes the strike first. Lightning rods come first

There are three ways to be struck:

| Way | When | Killed outright | Health lost | Unconscious | Legs paralysed | Burst eardrum |
|---|---|---|---|---|---|---|
| Direct | Hit dead on | <V d="lightning/lightning_injury_v1#direct.death" pct="true" /> | <V d="lightning/lightning_injury_v1#direct.health.0" />–<V d="lightning/lightning_injury_v1#direct.health.1" /> | <V d="lightning/lightning_injury_v1#direct.unconscious.0" />–<V d="lightning/lightning_injury_v1#direct.unconscious.1" u="秒" ue="s" /> | <V d="lightning/lightning_injury_v1#direct.paralysis.0" />–<V d="lightning/lightning_injury_v1#direct.paralysis.1" u="秒" ue="s" /> | <V d="lightning/lightning_injury_v1#direct.eardrum" pct="true" /> |
| Side flash | Within <V d="lightning/lightning_injury_v1#side_range" u="格" ue="blocks" /> of the strike | <V d="lightning/lightning_injury_v1#side.death" pct="true" /> | <V d="lightning/lightning_injury_v1#side.health.0" />–<V d="lightning/lightning_injury_v1#side.health.1" /> | <V d="lightning/lightning_injury_v1#side.unconscious_chance" pct="true" /> chance, <V d="lightning/lightning_injury_v1#side.unconscious.0" />–<V d="lightning/lightning_injury_v1#side.unconscious.1" u="秒" ue="s" /> | <V d="lightning/lightning_injury_v1#side.paralysis_chance" pct="true" /> chance, <V d="lightning/lightning_injury_v1#side.paralysis.0" />–<V d="lightning/lightning_injury_v1#side.paralysis.1" u="秒" ue="s" /> | <V d="lightning/lightning_injury_v1#side.eardrum" pct="true" /> |
| Ground current | Within <V d="lightning/lightning_injury_v1#ground_range" u="格" ue="blocks" /> of the strike, on the ground or in water (<V d="lightning/lightning_injury_v1#water_range" u="格" ue="blocks" /> in water) | Never | <V d="lightning/lightning_injury_v1#ground.health.0" /> (near) – <V d="lightning/lightning_injury_v1#ground.health.1" /> (far) | Never | <V d="lightning/lightning_injury_v1#ground.paralysis_chance" pct="true" /> chance, <V d="lightning/lightning_injury_v1#ground.paralysis.0" />–<V d="lightning/lightning_injury_v1#ground.paralysis.1" u="秒" ue="s" /> | Never |

- Being struck does not set you on fire, and fire the strike lights right around you goes out at once; grass further off can still catch. Direct strikes and side flashes push your tinnitus straight to severe
- Sitting in a vehicle cab with the doors shut keeps you safe; with no sky over your head you cannot be struck directly or by a side flash
- The moment you are struck your view goes white. Unconscious, the screen is black and the world sounds far away: no moving, no using items, no inventory. Taking at least <V d="lightning/lightning_injury_v1#wake_damage" u="点伤害" ue="damage" /> wakes you. After waking your view stays blurred for <V d="lightning/lightning_injury_v1#wake_blur" u="秒" ue="s" />
- **Legs paralysed**: you can only crawl, at ×<V d="lightning/lightning_injury_v1#crawl" />, and cannot jump. When it wears off you get up off the ground, and your legs stay weak for <V d="lightning/lightning_injury_v1#weak_legs.0" />–<V d="lightning/lightning_injury_v1#weak_legs.1" u="秒" ue="s" />: movement ×<V d="lightning/lightning_injury_v1#weak_move" />, no sprinting or jumping, stamina cost ×<V d="lightning/lightning_injury_v1#weak_stamina" />
- **Burst eardrum**: one ear is muffled, the sounds around you ×<V d="lightning/lightning_injury_v1#eardrum_hearing" />, for <V d="lightning/lightning_injury_v1#eardrum_days" u="天" ue="days" /> (online time), coming back over the second half. Tinnitus mostly rings in that ear
- **Lightning aftermath**: left by a direct strike, a side flash, or ground current within <V d="lightning/lightning_injury_v1#ground_aftermath" u="格" ue="blocks" /> of the strike, for <V d="lightning/lightning_injury_v1#aftermath_days" u="天" ue="days" />. Now and then a headache blurs your view for a few seconds (every <V d="lightning/lightning_injury_v1#headache_gap.0" />–<V d="lightning/lightning_injury_v1#headache_gap.1" u="秒" ue="s" />), aim sway ×<V d="lightning/lightning_injury_v1#aftermath_sway" />, stamina recovery ×<V d="lightning/lightning_injury_v1#aftermath_stamina_regen" />, health recovery ×<V d="lightning/lightning_injury_v1#aftermath_health_regen" />
- Asleep, the eardrum and the aftermath heal <V d="lightning/lightning_injury_v1#sleep" /> times faster. Death clears everything
- Seeing a strike within <V d="lightning/lightning_injury_v1#afterimage_range" u="格" ue="blocks" /> leaves a violet after-image in your eyes for a few seconds; the closer, the longer
- Other creatures can be struck too: a side flash takes <V d="lightning/lightning_injury_v1#mob_side" pct="true" /> of their maximum health, ground current <V d="lightning/lightning_injury_v1#mob_ground" pct="true" />; for four-legged animals ground current reaches ×<V d="lightning/lightning_injury_v1#animal_ground" /> as far
- **Crawling**: lying on the ground on land (legs paralysed, squeezing through a one-block gap, unconscious) you really crawl. With paralysed legs you pull yourself forward on your elbows, one after the other, legs dragging; in a gap your knees push along with your elbows. Reaching out you barely move; the pull is the fastest part. You can look around first and your body slowly turns after you. What you hold is put away while you crawl and can be used once you stop. In first person your reaching hand shows at the bottom of the view, and looking down you see both hands taking turns on the ground. Swimming in water is unchanged
- **Wet**: struck in the rain or standing in water, more of the current runs over your skin: a direct strike kills you outright only <V d="lightning/lightning_injury_v1#direct.wet_death" pct="true" /> of the time, a side flash <V d="lightning/lightning_injury_v1#side.wet_death" pct="true" />, but you get <V d="lightning/lightning_injury_v1#wet_burns" /> more shallow burn
- **Lightning burns**: where the current went into and out of the body. A direct strike leaves <V d="lightning/lightning_injury_v1#direct.burns" /> (in at the head or an arm, out at a leg), a side flash <V d="lightning/lightning_injury_v1#side.burns" /> (the arm toward the strike, or the chest), ground current within <V d="lightning/lightning_injury_v1#ground_burn_range" u="格" ue="blocks" /> of the strike <V d="lightning/lightning_injury_v1#ground.burns" /> (a leg). They heal on their own in <V d="wounds/wounds_v1#burn.heal_days.0" />–<V d="wounds/wounds_v1#burn.heal_days.1" u="天" ue="days" />; left uncovered, each has a <V d="wounds/wounds_v1#burn.infect_chance" pct="true" /> chance of becoming infected after <V d="wounds/wounds_v1#burn.check_days" u="天" ue="days" />, and a bandage prevents that. At most <V d="wounds/wounds_v1#burn.max_burns" />, apart from scratches; they show on the inventory body chart
- **Metal burns**: on a direct strike or side flash, a gun on your back (chest), a pistol in the holster (right leg) and magazines in the rig (belly) get hot and each leaves a deeper burn, costing <V d="lightning/lightning_injury_v1#metal_health" /> more health. They take <V d="wounds/wounds_v1#burn.deep_heal_days.0" />–<V d="wounds/wounds_v1#burn.deep_heal_days.1" u="天" ue="days" /> to heal; uncovered, the chance of infection is <V d="wounds/wounds_v1#burn.deep_infect_chance" pct="true" />
- **What you hold**: on a direct strike or side flash your hand jerks open and what was in it lands <V d="lightning/lightning_injury_v1#drop_range.0" />–<V d="lightning/lightning_injury_v1#drop_range.1" u="格" ue="blocks" /> away
- **Marks on the skin**: a lightning burn is a small charred, cracked patch ringed with raw red flesh and peeling pale skin, with reddened skin around it; a metal burn has the shape of what burned it (a slanting strip on the back, an upright oblong on the outside of the right thigh, one or two small uprights on the belly), with a sharp edge and a leathery middle. They scab over dark, turn into pink or light brown scars, and disappear. After a direct strike or side flash a red Lichtenberg figure spreads from where the current came in over the shoulder, chest and back: a few main branches that fork again and again, finer and denser outwards, ending in feathery tips, and fades within <V d="lightning/lightning_injury_v1#lichtenberg_days" u="天" ue="days" />; it does not hurt and changes nothing
- **Batteries drained**: on a direct strike or side flash, each energy battery and charged tool you carry has a <V d="lightning/lightning_injury_v1#drain_chance" pct="true" /> chance of being drained flat; nothing breaks

## Stamina

Maximum <V d="stamina/stamina_v1#max" />. After <V d="stamina/stamina_v1#delay_seconds" u="s" /> at rest it regenerates <V d="stamina/stamina_v1#regen" /> per second (<V d="stamina/stamina_v1#exhausted_delay_seconds" u="s" /> after running out)

| Action | Cost |
|---|---|
| Walk | <V d="stamina/stamina_v1#costs.walk" u="/s" /> |
| Sneak | <V d="stamina/stamina_v1#costs.sneak" u="/s" /> |
| Sprint | <V d="stamina/stamina_v1#costs.sprint" u="/s" /> |
| Swim | <V d="stamina/stamina_v1#costs.swim" u="/s" /> |
| Climb | <V d="stamina/stamina_v1#costs.climb" u="/s" /> |
| Mine | <V d="stamina/stamina_v1#costs.mining" u="/s" /> |
| Jump (sprint jump) | <V d="stamina/stamina_v1#costs.jump" /> (<V d="stamina/stamina_v1#costs.sprint_jump" />) |
| Empty hand / item / crowbar swing | <V d="stamina/stamina_v1#costs.melee_empty_hand" /> / <V d="stamina/stamina_v1#costs.melee" /> / <V d="stamina/stamina_v1#costs.melee_items.apocalypse_firstlight:crowbar" /> |
| One axe chop | <V d="stamina/stamina_v1#costs.chop" /> |
| One shot | <V d="stamina/stamina_v1#costs.shots_by_ammo.apocalypse_firstlight:9x19mm_round" /> (9 mm) to <V d="stamina/stamina_v1#costs.shots_by_ammo.apocalypse_firstlight:12_7x55mm_round" /> (12.7 mm) |

- Regeneration ×<V d="stamina/stamina_v1#regen_in_water" /> in water, ×<V d="stamina/stamina_v1#regen_low_food" /> at food ≤ <V d="stamina/stamina_v1#low_food_level" />
- **Winded**: after running out you can't sprint until you are back to <V d="stamina/stamina_v1#winded_resume" />; melee damage ×<V d="stamina/stamina_v1#winded.melee_damage" />, digging speed ×<V d="stamina/stamina_v1#winded.dig_speed" />, and people nearby hear you panting
- **Fatigue**: below <V d="stamina/stamina_v1#fatigue.start" /> stamina your aim sways, with up to <V d="stamina/stamina_v1#fatigue.spread_extra" pct /> more spread
- The heavier your load, the more everything costs and the slower you recover. See [Inventory and Weight](./inventory)

## Thirst

Full is <V d="thirst/thirst_v1#max" />. It drops <V d="thirst/thirst_v1#base_per_hour" /> per real-time hour, plus <V d="thirst/thirst_v1#exertion_per_100_stamina" /> for every 100 stamina you spend

| Thirst | Effect |
|---|---|
| Below <V d="thirst/thirst_v1#thirsty_below" /> | Stamina regeneration ×<V d="thirst/thirst_v1#stamina_regen_thirsty" /> |
| Below <V d="thirst/thirst_v1#dehydrated_below" /> | Stamina regeneration ×<V d="thirst/thirst_v1#stamina_regen_dehydrated" />, no natural healing |
| 0 | <V d="health/health_v1#damage.default_scale" /> health every <V d="thirst/thirst_v1#damage_interval_seconds" u="s" /> until you die |

**Drinking**

- Use a glass bottle on a water source to fill **dirty water**. It carries the radiation of the place you filled it
- Boil it in a furnace (10 s), smoker (5 s) or campfire (30 s). Clean water becomes **purified water**; irradiated water becomes **boiled water** and keeps its radiation
- A bottle gives +<V d="thirst/thirst_v1#bottle" />; a sip from a water source (sneak, empty hand) gives +<V d="thirst/thirst_v1#sip" />
- Melon slice +<V d="thirst/thirst_v1#foods.minecraft:melon_slice" />, apple +<V d="thirst/thirst_v1#foods.minecraft:apple" />, stews and soups +<V d="thirst/thirst_v1#foods.minecraft:mushroom_stew" />, honey bottle +<V d="thirst/thirst_v1#foods.minecraft:honey_bottle" />. Milk doesn't quench thirst

**Stomach sickness**: dirty water has a <V d="thirst/thirst_v1#sickness.chance_bottle" pct /> chance per bottle (<V d="thirst/thirst_v1#sickness.chance_sip" pct /> per raw sip) to give you a stomach infection for <V d="thirst/thirst_v1#sickness.duration_seconds" u="s" />: thirst doubles and you get bouts of nausea that cost thirst and food. Milk doesn't cure it

## Temperature

Core temperature starts at <V d="temperature/temperature_v1#normal" u="°C" /> and drifts toward a target set by how it feels around you: air temperature, shelter, being wet, nearby heat sources, exertion and clothing. It feels best between <V d="temperature/temperature_v1#comfort_low" u="°C" /> and <V d="temperature/temperature_v1#comfort_high" u="°C" /> (warm clothing lowers the lower bound)

| Stage | Core temperature | Effect |
|---|---|---|
| Cold 1 | Below <V d="temperature/temperature_v1#cold_stages.0.below" u="°C" /> | Slower stamina regeneration |
| Cold 2 | Below <V d="temperature/temperature_v1#cold_stages.1.below" u="°C" /> | Also slower walking, faster hunger, no natural healing |
| Cold 3 | Below <V d="temperature/temperature_v1#cold_stages.2.below" u="°C" /> | <V d="health/health_v1#damage.default_scale" /> health every <V d="temperature/temperature_v1#damage_interval_seconds" u="s" /> |
| Heat 1 | Above <V d="temperature/temperature_v1#heat_stages.0.above" u="°C" /> | Faster thirst, higher stamina costs |
| Heat 2 | Above <V d="temperature/temperature_v1#heat_stages.1.above" u="°C" /> | Also slower walking, no natural healing |
| Heat 3 | Above <V d="temperature/temperature_v1#heat_stages.2.above" u="°C" /> | <V d="health/health_v1#damage.default_scale" /> health every <V d="temperature/temperature_v1#damage_interval_seconds" u="s" /> |

- Frost creeps in from the screen edges when you are cold; heat makes the screen glare and shimmer
- Rain soaks you in about a minute and being wet makes you colder; without a fire you take about <V d="temperature/temperature_v1#wetness.dry_seconds" u="s" /> to dry
- Campfires, fire, furnaces and AFL's box stove keep you warm
- <StatusTag s="verified" d="2026-10-19" /> In a T15's cab you stay out of the rain; the cab has its own air, with a heater and A/C (see [T15](../vehicles/t15))
- <ItemIcon id="wrist_thermometer" /><ItemIcon id="clinical_thermometer" />**Readings**: a wrist thermometer shows air temperature; a clinical thermometer takes your temperature after a 3 s hold. Without them the HUD shows no numbers

## Food

Food uses the vanilla system; AFL has no foods of its own yet, and vanilla food can no longer be obtained (<StatusTag s="test" />), so new worlds have nothing to eat for now. At food ≤ <V d="stamina/stamina_v1#low_food_level" /> stamina regeneration is ×<V d="stamina/stamina_v1#regen_low_food" />; below <V d="health/health_v1#regen.min_food" /> there is no natural healing; stomach sickness and Cold 2 or worse burn extra food

<StatusTag s="planned" /> Food spoilage and storage
