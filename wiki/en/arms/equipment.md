# Equipment

You have five equipment slots: back, rig, wrist, ears and holster

## Carrying

<StatusTag s="creative" />

| Item | Slot | Effect |
|---|---|---|
| <ItemIcon id="field_chest_rig" /> Field Chest Rig | Rig | 4 magazine pouches: three 1×2, one 2×2 |
| <ItemIcon id="canvas_backpack" /> Canvas Backpack | Back | 5×6 cells |
| <ItemIcon id="hiking_backpack" /> Hiking Backpack | Back | 6×10 cells |
| <ItemIcon id="pistol_holster" /> Pistol Holster | Holster | Lets a pistol use slot 2 |
| <ItemIcon id="gun_sling" /> Gun Sling | Fitted to a long gun | Lets the long gun use slot 1 |

See [Inventory and Weight](../guide/inventory)

## Tools

| Item | Effect | How to get |
|---|---|---|
| <ItemIcon id="simple_hearing_protection" /> Simple Hearing Protection | Worn on the ears; eases ringing from gunfire and muffles outside sound | Crafted: iron ingot, leather, wool |
| <ItemIcon id="wrist_thermometer" /> Wrist Thermometer | Worn on the wrist; always shows air temperature | Crafted: leather, glass pane, copper ingot |
| <ItemIcon id="clinical_thermometer" /> Clinical Thermometer | Hold right click for about 3 s to take your temperature | Crafted: glass panes, red dye |
| <ItemIcon id="geiger_counter" /> Geiger Counter | Shows the current dose rate | <StatusTag s="creative" /> |
| <ItemIcon id="crowbar" /> Crowbar | Melee weapon; also removes tunnel fixtures | <StatusTag s="creative" /> |
| <ItemIcon id="flip_lighter" /> Flip Lighter | Right click to flip it open and strike it; lit, it gives light and lights stoves, campfires, candles and TNT | <StatusTag s="creative" /> |

## Flip Lighter

<StatusTag s="test" /> An old silver-plated flip lighter, used from the main hand

- Right click: flips the lid open when shut; strikes it when open (it sometimes fails, so strike again)
- Sneak + right click: shuts the lid; if lit, this puts the flame out
- In first person the view glances down at the lighter as you flip and strike it
- Lit, the flame lights the area around you; right click a campfire, candle, TNT or stove to light it; it cannot set other blocks on fire
- A lit lighter burns fuel; the fuel bar in the inventory shows what is left. It goes out when the fuel runs out or under water
- Infected within <V j="FlipLighterItem.SOUND_RADIUS" /> blocks can hear it flip and strike
- For now it comes only from the creative inventory, and its sounds are placeholders

## Armor

<StatusTag s="planned" /> AFL has no armor of its own yet. Body armor, riot shields, protective suits and radiation gear are planned

## Vanilla gear

<StatusTag s="test" /> Vanilla swords, shields and armor do not work in AFL

- They cannot be crafted and never turn up in loot, villager trades or the gear mobs spawn with
- Ones already in old worlds are not deleted. You can still hold or wear them, but they do nothing:
  - Swords hit, swing and mine like a bare hand, with no sweep
  - Shields cannot be raised
  - Armor gives no armor points, toughness, knockback resistance or warmth
  - Enchantments have no effect; leather boots no longer walk on powder snow, leather armor no longer stops freezing, and the turtle shell gives no water breathing
- Enchanting tables, anvils, brewing stands, grindstones, smithing tables and ender chests cannot be used in survival or crafted; enchanted books and bottles o' enchanting cannot be obtained
- The creative inventory no longer lists this gear; `/give` still hands it out, and it does nothing in any game mode
- Development switch: `vanilla_retirement.enabled` in `config/apocalypse_firstlight-common.toml`; `false` brings the vanilla rules back
