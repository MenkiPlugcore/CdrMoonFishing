# CdrMoonFishing

Crossplay-first custom fishing gameplay for Paper servers. Core mechanics stay server-side so Java and Bedrock players through Geyser use the same fishing flow.

## Current version

`v0.6.0` — Tournament + Leaderboard

## Implemented

- Custom fishing encounters with rarity, weight, depth, biome, weather and time conditions
- Bait system
- CALM, ERRATIC, AGGRESSIVE and DIVING behavior profiles
- Rare/Epic/Legendary multi-phase encounters
- Persistent FishDex and lifetime player statistics
- Vault-backed Fish Market with per-species price/kg
- Daily Featured Catch market bonus
- Live fishing tournaments
- Three tournament modes: POINTS, TOTAL_WEIGHT and BIGGEST
- Automatic tournament participation from successful catches
- Persistent tournament state across restarts
- Tournament history archive
- Vault rewards for top 3
- Pending reward queue if Vault/economy is unavailable during payout
- Lifetime global fishing leaderboards

## Tournament

Player commands:

```text
/fishtournament
/fishtournament status
/fishtournament top [page]
```

Admin commands:

```text
/fishtournament start <minutes> [points|weight|biggest]
/fishtournament stop
/fishtournament cancel
```

Aliases: `/ftourney`, `/ftournament`.

Players do not need to join manually. A successful CdrMoonFishing catch while an event is active automatically creates/updates their tournament entry.

### Tournament modes

`POINTS` combines rarity points with a weight bonus.

Default rarity points:

```text
COMMON      1
UNCOMMON    3
RARE        8
EPIC       20
LEGENDARY  60
```

Default weight contribution is `0.25 points per kg`.

`TOTAL_WEIGHT` ranks by total kilograms caught during the event.

`BIGGEST` ranks by the single heaviest valid catch during the event.

Tie-break order is score, biggest catch, catch count, then player name.

### Tournament rewards

Default Vault rewards:

```text
#1  5000
#2  2500
#3  1000
```

If Vault or the economy provider is unavailable when the event ends, the reward is stored in `tournament.yml` as a pending payout and retried automatically.

Active state is stored in:

```text
plugins/CdrMoonFishing/tournament.yml
```

Completed results are archived in:

```text
plugins/CdrMoonFishing/tournament-history.yml
```

## Global leaderboard

```text
/fishleaderboard [catches|weight|biggest|legendary] [page]
```

Aliases: `/flb`, `/fishlb`.

Metrics:

- `catches` — lifetime successful catches
- `weight` — lifetime total caught weight
- `biggest` — personal biggest fish
- `legendary` — lifetime legendary catches

The global board reads the persistent player profiles created by the FishDex/statistics system.

## Fish Market

```text
/fishmarket
/fishmarket price
/fishmarket sellhand
/fishmarket sellall
/fishmarket featured
```

Fish value is calculated at sale time:

```text
base-price-per-kg
× weight
× rarity multiplier
× global multiplier
× featured multiplier (when active)
```

Vault is optional for fishing gameplay, but selling and tournament cash rewards require a Vault economy provider.

## FishDex & statistics

```text
/fishdex [page]
/fishing stats [player]
```

Player profiles are stored under:

```text
plugins/CdrMoonFishing/players/<uuid>.yml
```

## Bait

```text
/fishing bait [id|none]
/fishing givebait <player> <id> [amount]
```

Players can also right-click CdrMoonFishing bait items to select them.

## Requirements

- Paper 1.21.11
- Java 21

Optional integrations:

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
build/libs/CdrMoonFishing-0.6.0.jar
```

## Commands

```text
/fishtournament [status|top|start|stop|cancel]
/fishleaderboard [catches|weight|biggest|legendary] [page]
/fishmarket [open|price|sellhand|sellall|featured]
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

- v0.6.x — tournament polish, history browsing and seasonal boards
- next — ItemsAdder/MMOItems item providers, custom visual assets and expanded fish catalog
