# CdrMoonFishing

Crossplay-first custom fishing gameplay for Paper servers. The core mechanic stays server-side so Java and Bedrock players through Geyser can use the same fishing flow.

## Current version

`v0.1.0` — Core fishing prototype

Implemented:

- Vanilla fishing interception
- Fish rarity and weighted encounter selection
- Fish weight
- Depth detection
- Biome/region filter framework
- Weather and day/night filters
- Crossplay tension minigame
- Custom fish item metadata through PersistentDataContainer
- Optional plugin detection for Vault, ItemsAdder, MMOItems, Floodgate and Geyser
- `/fishing debug`
- `/fishing cancel`
- `/fishing status`
- `/fishing reload`

## Gameplay prototype

1. Cast a normal fishing rod.
2. When a fish bites, CdrMoonFishing chooses the encounter based on environment conditions.
3. Reel in normally to start the custom encounter.
4. During the tension encounter, right-click with the fishing rod to add reel pressure.
5. Keep tension inside the configured safe range while catch progress fills.
6. Too much tension snaps the line. Too little tension lets the fish escape.
7. A successful catch gives a custom fish item containing fish ID, rarity, weight, region, depth and catch timestamp.

The interaction deliberately uses the normal fishing rod plus ActionBar/Title/Sound feedback. No Java-only keyboard input or inventory-click minigame is required.

## Requirements

- Paper 1.21.11
- Java 21

Optional integrations detected at runtime:

- Vault
- ItemsAdder
- MMOItems
- Floodgate
- Geyser-Spigot

These integrations are currently detection hooks only. Typed economy/custom-item integration will be added in later versions.

## Build

```bash
gradle clean build
```

Output:

```text
build/libs/CdrMoonFishing-0.1.0.jar
```

A GitHub Actions workflow is included to build the plugin on pushes to `main`.

## Configuration

`config.yml` controls tension behavior and balancing.

`fish.yml` controls fish definitions, including:

- material
- rarity
- chance
- weight range
- depth range
- pull strength
- biome filters
- weather filters
- time filters

## Planned roadmap

- v0.2.x — bait system and better region profiles
- v0.3.x — richer fish behavior/patterns and tension phases
- v0.4.x — FishDex and persistent player statistics
- v0.5.x — economy integration and selling formulas
- v0.6.x — tournament and leaderboard system
- later — ItemsAdder/MMOItems item providers, rare encounters and legendary multi-phase fights
