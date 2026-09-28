# CdrMoonFishing

Crossplay-first custom fishing gameplay for Paper servers. Core mechanics stay server-side so Java and Bedrock players through Geyser use the same fishing flow.

## Current version

`v0.2.0` — Bait + Fish Behavior

Implemented:

- Vanilla fishing interception
- Fish rarity and weighted encounter selection
- Fish weight
- Depth detection
- Biome/region filters
- Weather and day/night filters
- Crossplay tension minigame
- Custom fish PDC metadata
- Bait registry and selectable bait items
- Bait rarity/species multipliers
- Required bait support for special fish
- Four fish behavior profiles: CALM, ERRATIC, AGGRESSIVE, DIVING
- Configurable behavior surges
- Optional detection for Vault, ItemsAdder, MMOItems, Floodgate and Geyser

## Fishing flow

1. Cast a normal fishing rod.
2. CdrMoonFishing evaluates depth, biome, weather, time and selected bait.
3. When the fish bites, one selected bait item is consumed.
4. Reel normally to enter the tension encounter.
5. Right-click the fishing rod to add reel pressure.
6. Keep tension inside the safe zone while catch progress fills.
7. Fish behavior changes how tension moves during the encounter.
8. Successful catches receive custom metadata for species, rarity, behavior, weight, region, depth, bait and catch time.

No Java-only keyboard controls or inventory-click minigame are required.

## Bait

Default bait definitions are stored in `bait.yml`.

Player usage:

```text
/fishing bait
/fishing bait <id>
/fishing bait none
```

Players can also right-click a CdrMoonFishing bait item to select it.

Admin usage:

```text
/fishing givebait <player> <id> [amount]
```

Default bait IDs:

- `worm`
- `shrimp`
- `glow_worm`
- `moon_worm`
- `ancient_bait`

`Lunar Leviathan` currently requires `ancient_bait` in addition to its environmental requirements.

## Fish behavior

Fish can define one of these profiles in `fish.yml`:

- `CALM` — low variance, easier tension control
- `ERRATIC` — random positive or negative tension surges
- `AGGRESSIVE` — stronger continuous pull with upward surges
- `DIVING` — periodic strong dives that spike tension

Surge timing and strength can be tuned in `config.yml`.

## Requirements

- Paper 1.21.11
- Java 21

Optional integrations detected at runtime:

- Vault
- ItemsAdder
- MMOItems
- Floodgate
- Geyser-Spigot

ItemsAdder/MMOItems are currently detection hooks; visual custom-fish providers will be added later.

## Build

```bash
gradle clean build
```

Output:

```text
build/libs/CdrMoonFishing-0.2.0.jar
```

A GitHub Actions workflow builds the plugin on pushes to `main`.

## Configuration

`config.yml` controls tension balancing and behavior surges.

`fish.yml` controls fish definitions, including material, rarity, behavior, chance, weight, depth, pull strength, environment filters and required bait.

`bait.yml` controls bait items and rarity/species encounter multipliers.

## Commands

```text
/fishing bait [id|none]
/fishing debug
/fishing cancel
/fishing givebait <player> <id> [amount]
/fishing status
/fishing reload
```

## Roadmap

- v0.2.x — bait/behavior balancing and region profiles
- v0.3.x — FishDex and persistent player statistics
- v0.4.x — richer rare/legendary encounter phases
- v0.5.x — economy integration and selling formulas
- v0.6.x — tournament and leaderboard system
- later — ItemsAdder/MMOItems item providers and custom visual assets
