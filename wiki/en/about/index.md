# Data & Versions

<VersionCard />

## Where the numbers come from

This site never copies numbers by hand. When the site is built, a script reads the game data straight from the mod's repository:

| Content | Source |
|---|---|
| Gun stats | `data/apocalypse_firstlight/native_guns/*.json` |
| Weight, inventory size | `item_mass/*.json`, `item_size/*.json` |
| Stamina, thirst, temperature | JSON under `stamina/`, `thirst/`, `temperature/` |
| Backgrounds, body, traits, growth | JSON under `survivor_backgrounds/`, `survivor_body/`, `survivor_traits/`, `survivor_growth/` |
| Machine power and capacity | `machine_balance/*.json` |
| Values kept in code | Read from constants in the Java source |
| Names | The game's `zh_cn` / `en_us` language files |

Amber monospace numbers in the text, like <V d="stamina/stamina_v1#max" />, are read from the game data; hover one to see where it comes from.

## Versions

- The version at the top and bottom of each page comes from the mod's `gradle.properties`; the data commit is the commit these numbers were read from
- This site is an internal development reference: Main rebuilds and deploys it each time a feature is integrated into the main line, and the commit and sync time in the footer are the main-line state of that deploy
- Work still on a workspace branch does not appear here
- A local preview with uncommitted data changes says so on the version card

## Value history

The "Value history" at the bottom of each gun page and the [Value Changes](/en/changelog/values) page are built from the git history of the game data: when, which version, and which number changed from what to what. Handling values such as recoil are only counted

## Status tags

Features move through four steps, only forward and never skipping one:

- <StatusTag s="verified" d="date" /> Integrated into the main line and checked in game; the date of that check follows
- <StatusTag s="integrated" /> Integrated into the main line, not checked in game yet, may still have problems
- <StatusTag s="test" /> Only in test worlds or dev commands, or still on a workspace branch
- <StatusTag s="planned" /> Not built yet, only designed or planned

One more tag is not a step:

- <StatusTag s="creative" /> Finished, but not obtainable in survival yet
