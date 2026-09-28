# CdrMoonFishing

Crossplay-first custom fishing gameplay for Paper servers. Core mechanics stay server-side so Java and Bedrock players through Geyser use the same fishing flow.

## Current version

`v0.3.0` — FishDex + Player Statistics

## Implemented

- Vanilla fishing interception
- Fish rarity and weighted encounter selection
- Fish weight
- Depth, biome, weather and time filters
- Crossplay tension minigame
- Custom fish PDC metadata
- Bait registry and selectable bait items
- Bait rarity/species multipliers
- Required bait support for special fish
- CALM, ERRATIC, AGGRESSIVE and DIVING fish behaviors
- Persistent FishDex discovery tracking
- Per-species catch counts and personal best weights
- Total catches and total caught weight
- Rarity catch breakdown
- Legendary catch count
- Overall biggest catch record
- YAML statistics storage per player UUID
- Optional detection for Vault, ItemsAdder, MMOItems, Floodgate and Geyser

## FishDex

FishDex is updated only after a player successfully completes a fishing encounter. Moving, selling or dropping a fish item does not alter progression.

```text
/fishdex
/fishdex <page>
```

Undiscovered species are hidden as `???`. Discovered entries show species name, catch count and the player's best recorded weight for that species.

FishDex completion is calculated against the currently loaded `fish.yml`, so adding new species automatically expands the collection target.

## Player statistics

```text
/fishing stats
/fishing stats <player>   # admin inspection of an online player
```

Tracked values include:

- total catches
- total caught weight
- discovered species
- FishDex completion percentage
- rarity totals
- legendary catch count
- biggest catch species and weight
- per-species catch count
- per-species best weight
- first and last catch timestamps

Player profiles are stored in:

```text
plugins/CdrMoonFishing/players/<uuid>.yml
```

Profiles are saved on successful catches and flushed again on plugin shutdown.

## Bait

```text
/fishing bait
/fishing bait <id>
/fishing bait none
/fishing givebait <player> <id> [amount]
```

Players can also right-click a CdrMoonFishing bait item to select it.

Default bait IDs:

- `worm`
- `shrimp`
- `glow_worm`
- `moon_worm`
- `ancient_bait`

`Lunar Leviathan` requires `ancient_bait` in addition to its environmental requirements.

## Fish behavior

- `CALM` — low variance and easier tension control
- `ERRATIC` — random positive or negative tension surges
- `AGGRESSIVE` — stronger continuous pull with upward surges
- `DIVING` — periodic strong dives that spike tension

## Requirements

- Paper 1.21.11
- Java 21

Optional integrations detected at runtime:

- Vault
- ItemsAdder
- MMOItems
- Floodgate
- Geyser-Spigot

## Build

```bash
gradle clean build
```

Output:

```text
build/libs/CdrMoonFishing-0.3.0.jar
```

A GitHub Actions workflow builds the plugin on pushes to `main`.

## Commands

```text
/fishdex [page]
/fishing stats [player]
/fishing bait [id|none]
/fishing debug
/fishing cancel
/fishing givebait <player> <id> [amount]
/fishing status
/fishing reload
```

## Roadmap

- v0.3.x — FishDex/statistics polish and collection rewards
- v0.4.x — rare/legendary encounter phases
- v0.5.x — economy integration and selling formulas
- v0.6.x — tournament and leaderboard system
- later — ItemsAdder/MMOItems item providers and custom visual assets
