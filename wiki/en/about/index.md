# Data & Versions

<VersionCard />

## Where the numbers come from

This site never copies numbers by hand. When the site is built, a script reads the game data straight from the mod's repository:

| Content | Source |
|---|---|
| Gun stats | `data/apocalypse_firstlight/native_guns/*.json` |
| Weight, inventory size | `item_mass/*.json`, `item_size/*.json` |
| Stamina, thirst, temperature | JSON under `stamina/`, `thirst/`, `temperature/` |
| Machine power and capacity | `machine_balance/*.json` |
| Values kept in code | Read from constants in the Java source |
| Names | The game's `zh_cn` / `en_us` language files |

Amber monospace numbers in the text, like <V d="stamina/stamina_v1#max" />, are read from the game data; hover one to see where it comes from.

## Versions

- The version at the top and bottom of each page comes from the mod's `gradle.properties`; the data commit is the commit these numbers were read from
- The public site is only updated when a new version is released, so its numbers match the version you download
- A local preview with uncommitted data changes says so on the version card

## Value history

The "Value history" at the bottom of each gun page and the [Value Changes](/en/changelog/values) page are built from the git history of the game data: when, which version, and which number changed from what to what. Handling values such as recoil are only counted

## Status tags

- <StatusTag s="live" /> In normal play
- <StatusTag s="new" /> Just added, numbers may still change
- <StatusTag s="creative" /> Finished, but not obtainable in survival yet
- <StatusTag s="test" /> Only appears in test worlds
- <StatusTag s="planned" /> Not built yet, only planned
