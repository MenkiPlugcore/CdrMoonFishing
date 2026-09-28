# CdrMoonFishing

Crossplay-first custom fishing gameplay for Paper servers. Core mechanics stay server-side so Java and Bedrock players through Geyser use the same fishing flow.

## Current version

`v0.4.0` — Rare & Legendary Encounter Phases

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
- Configurable multi-phase encounter engine
- Per-phase behavior overrides
- Per-phase pull, safe-zone, reel-power and progress modifiers
- Title, subtitle and sound phase transitions
- Persistent FishDex discovery tracking
- Per-species catch counts and personal best weights
- Total catches and total caught weight
- Rarity catch breakdown
- Legendary catch count
- Overall biggest catch record
- YAML statistics storage per player UUID
- Optional detection for Vault, ItemsAdder, MMOItems, Floodgate and Geyser

## Encounter phases

Fish without a `phases:` section keep the normal v0.3 behavior. Multi-phase encounters are opt-in per species.

A phase becomes active when catch progress reaches its `start-progress`. Each phase can change:

- behavior (`CALM`, `ERRATIC`, `AGGRESSIVE`, `DIVING`)
- pull multiplier
- safe-zone minimum/maximum offsets
- reel-power multiplier
- catch-progress multiplier
- transition title/subtitle
- transition sound

Example:

```yaml
phases:
  phase_1:
    display-name: "Phase I - Awakening"
    start-progress: 0
    behavior: AGGRESSIVE
    pull-multiplier: 1.05
    safe-min-offset: 0
    safe-max-offset: -3
    reel-power-multiplier: 1.0
    progress-multiplier: 1.0
    title: "§6§lLUNAR LEVIATHAN"
    subtitle: "§ePHASE I §7- The ancient beast awakens"
    sound: ENTITY_ENDER_DRAGON_GROWL
```

Phase transitions reset danger-grace accumulation but do not reset tension or catch progress.

Default phased species:

- `Moon Koi` — 2 phases
- `Abyss Eel` — 3 phases
- `Lunar Leviathan` — 3 phases

### Lunar Leviathan default fight

1. **Phase I — Awakening** (`0%`) — aggressive opening pressure.
2. **Phase II — Abyssal Dive** (`35%`) — switches to diving behavior, stronger pull and narrower safe zone.
3. **Phase III — Final Struggle** (`72%`) — aggressive final assault, much stronger pull, weaker reel power and a very narrow safe zone.

The Leviathan still requires `ancient_bait`, night, thunder and deep water according to the default fish configuration.

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
/fishing stats <player>
```

Tracked values include total catches, total weight, discovered species, rarity totals, legendary catches, biggest catch, per-species counts and best weights.

Player profiles are stored in:

```text
plugins/CdrMoonFishing/players/<uuid>.yml
```

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
build/libs/CdrMoonFishing-0.4.0.jar
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

- v0.4.x — encounter balancing, phase effects and boss-fish polish
- v0.5.x — economy integration and selling formulas
- v0.6.x — tournament and leaderboard system
- later — ItemsAdder/MMOItems item providers and custom visual assets
