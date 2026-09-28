# CdrMoonFishing

Crossplay-first custom fishing gameplay for Paper servers. Core mechanics stay server-side so Java and Bedrock players through Geyser use the same fishing flow.

## Current version

`v0.8.0` — Fishing Rod Progression / Rod Tier

## Implemented

- Custom fishing encounters with rarity, weight, depth, biome, weather and time conditions
- Bait system
- CALM, ERRATIC, AGGRESSIVE and DIVING behavior profiles
- Rare/Epic/Legendary multi-phase encounters
- Persistent FishDex and lifetime player statistics
- Vault-backed Fish Market with per-species price/kg
- Daily Featured Catch market bonus
- Live fishing tournaments + lifetime leaderboards
- Runtime ItemsAdder/MMOItems custom fish item providers with vanilla fallback
- Persistent progression fishing rods with PDC identity, XP and auto tier upgrades
- Per-tier reel-power bonus
- Per-tier rarity-luck multiplier
- Per-tier rod-XP gain multiplier

## Fishing Rod Progression

Only CdrMoonFishing progression rods earn rod XP. Normal vanilla fishing rods still work, but use neutral `1.00x` fishing bonuses and never level up.

Admin distribution:

```text
/fishrod give <player>
/fishrod give <player> <tier>
```

Player inspection:

```text
/fishrod
/fishrod info
/fishrod tiers
```

Aliases: `/frod`, `/fishingrod`.

Default tiers:

```text
Driftwood   0 XP     • reel 1.00x • luck +0%  • XP 1.00x
Reinforced  250 XP   • reel 1.06x • luck +5%  • XP 1.05x
Oceanic     750 XP   • reel 1.12x • luck +12% • XP 1.10x
Abyssal     1750 XP  • reel 1.20x • luck +22% • XP 1.18x
Lunar       4000 XP  • reel 1.30x • luck +35% • XP 1.30x
```

Tier configuration lives in `rod.yml`. XP is awarded only after a successful custom fishing encounter. The default XP formula is rarity base XP plus a weight contribution, multiplied by the current rod tier's XP multiplier.

Default base XP:

```text
COMMON      5
UNCOMMON    8
RARE       16
EPIC       35
LEGENDARY 100
```

Default weight contribution is `0.5 XP per kg` before the tier multiplier.

Rarity luck changes weighted encounter selection rather than guaranteeing a rarity. COMMON stays at its normal weight; higher rarities receive progressively stronger multipliers, with LEGENDARY receiving the largest benefit from a high-tier rod.

Rod metadata is stored directly on the fishing rod using PDC (`rod_id`, `rod_xp`, `rod_tier`). Rod lore automatically refreshes after catches and shows tier, XP progress, reel multiplier and rarity luck.

## Custom fish item providers

Custom visuals remain optional. Every fish keeps its configured Bukkit `material` as a vanilla fallback.

Supported provider modes:

```text
VANILLA
ITEMSADDER
MMOITEMS
AUTO
```

Provider-backed catches retain CdrMoonFishing PDC, so Fish Market, FishDex, statistics and tournaments continue to work normally.

## Tournament

```text
/fishtournament
/fishtournament status
/fishtournament top [page]
/fishtournament start <minutes> [points|weight|biggest]
/fishtournament stop
/fishtournament cancel
```

Modes: `POINTS`, `TOTAL_WEIGHT`, `BIGGEST`. Tournament participation is automatic on successful catches. Top-3 Vault rewards support pending payout retry when the economy provider is unavailable.

## Global leaderboard

```text
/fishleaderboard [catches|weight|biggest|legendary] [page]
```

Aliases: `/flb`, `/fishlb`.

## Fish Market

```text
/fishmarket
/fishmarket price
/fishmarket sellhand
/fishmarket sellall
/fishmarket featured
```

Fish value is calculated from species base price, weight, rarity multiplier, global multiplier and optional Featured Catch multiplier.

## FishDex & statistics

```text
/fishdex [page]
/fishing stats [player]
```

Player profiles are stored under `plugins/CdrMoonFishing/players/<uuid>.yml`.

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
build/libs/CdrMoonFishing-0.8.0.jar
```

## Commands

```text
/fishrod [info|tiers|give]
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

- v0.8.x — rod balancing, rod visuals and progression polish
- next — Daily/weekly fishing missions or FishDex collection rewards
