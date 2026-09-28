# CdrMoonFishing

Crossplay-first custom fishing gameplay for Paper servers. Core mechanics stay server-side so Java and Bedrock players through Geyser use the same fishing flow.

## Current version

`v0.5.0` — Economy & Fish Market

## Implemented

- Vanilla fishing interception
- Fish rarity, weight, depth, biome, weather and time conditions
- Crossplay tension minigame
- Bait system and required bait support
- CALM, ERRATIC, AGGRESSIVE and DIVING fish behaviors
- Rare/Epic/Legendary multi-phase encounter engine
- Persistent FishDex and player statistics
- Vault economy payout through a runtime-safe optional hook
- Per-species `base-price-per-kg`
- Configurable rarity and global price multipliers
- Daily Featured Catch price bonus
- Standard chest Fish Market GUI
- Sell held fish / sell all custom fish
- Transaction rollback when an economy deposit fails

## Fish Market

Open the market:

```text
/fishmarket
/fishmarket open
```

Other commands:

```text
/fishmarket price
/fishmarket sellhand
/fishmarket sellall
/fishmarket featured
```

Aliases: `/fmarket`, `/fishshop`.

The GUI uses a normal server-side chest inventory, so it stays compatible with Java and Bedrock/Geyser players.

### Price formula

Fish value is calculated at sale time:

```text
base-price-per-kg
× fish weight
× rarity multiplier
× global multiplier
× Featured Catch multiplier (when active)
```

The item itself stores species and weight, not a fixed money value. This means economy balancing changes also affect fish that were caught before the configuration change.

Default rarity multipliers:

```text
COMMON     1.00x
UNCOMMON   1.10x
RARE       1.30x
EPIC       1.65x
LEGENDARY  2.25x
```

Default species base prices:

```text
River Carp       12 / kg
Silver Salmon    18 / kg
Moon Koi         55 / kg
Abyss Eel        90 / kg
Lunar Leviathan 300 / kg
```

Every fish can override its value in `fish.yml`:

```yaml
base-price-per-kg: 55.0
```

Older fish configs without this field remain compatible and receive a rarity-based fallback base price.

## Featured Catch

One sellable species is selected deterministically each day. By default it receives a `1.35x` market bonus.

```yaml
economy:
  market:
    featured-enabled: true
    featured-multiplier: 1.35
    timezone: "Asia/Jakarta"
```

The same date always selects the same featured fish even after a server restart.

## Vault

Vault remains optional for the plugin as a whole. Fishing, FishDex and encounter gameplay still load without it.

Selling requires:

1. Vault
2. an economy provider registered through Vault

The plugin hooks Vault at runtime, so CdrMoonFishing does not need a hard compile dependency on VaultAPI.

If a payout fails, removed fish are restored instead of being lost.

## Encounter phases

Fish without a `phases:` section use normal behavior. Multi-phase encounters can change behavior, pull strength, safe-zone width, reel power, progress speed and transition title/sound as catch progress increases.

Default phased species:

- Moon Koi — 2 phases
- Abyss Eel — 3 phases
- Lunar Leviathan — 3 phases

## FishDex & statistics

```text
/fishdex [page]
/fishing stats [player]
```

FishDex records discoveries only after a successful encounter. Player data is stored under:

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
build/libs/CdrMoonFishing-0.5.0.jar
```

## Commands

```text
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

- v0.5.x — market polish, sale statistics and economy balancing
- v0.6.x — tournament and leaderboard system
- later — ItemsAdder/MMOItems item providers and custom visual assets
