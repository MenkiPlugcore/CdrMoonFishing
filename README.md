# CdrMoonFishing

Crossplay-first custom fishing gameplay for Paper servers. Core mechanics stay server-side so Java and Bedrock players through Geyser use the same fishing flow.

## Current version

`v0.9.5` — FishDex Milestones + Collection Rewards

## Implemented

- Custom fishing encounters with rarity, weight, depth, biome, weather and time conditions
- Bait system
- CALM, ERRATIC, AGGRESSIVE and DIVING behavior profiles
- Rare/Epic/Legendary multi-phase encounters
- Persistent FishDex and lifetime player statistics
- FishDex milestone rewards with permanent Collection Luck
- Vault-backed Fish Market with per-species price/kg
- Daily Featured Catch market bonus
- Live fishing tournaments + lifetime leaderboards
- Runtime ItemsAdder/MMItems custom fish item providers with vanilla fallback
- Persistent progression fishing rods with XP, auto tier upgrades, reel bonus and rarity luck
- Daily fishing contracts with deterministic daily rotation
- Contract rewards through Vault money, custom bait and Rod XP

## FishDex Milestones

FishDex completion now provides permanent player progression rather than acting only as a checklist.

```text
/fishmilestones
/fishmilestones status
```

Aliases: `/fmilestones`, `/fishdexrewards`, `/fdrewards`.

Admin utilities:

```text
/fishmilestones reload
/fishmilestones reset <player>
```

Default milestones:

```text
25% FishDex   FishDex Explorer
50% FishDex   FishDex Collector
75% FishDex   FishDex Hunter
100% FishDex  Master of the FishDex
First LEGENDARY catch  Legendary Discovery
```

Rewards can combine:

```text
Vault money
CdrMoonFishing bait
Rod XP
Permanent Collection Luck
```

Default total Collection Luck reaches +15% after completing all default percentage milestones and the First Legendary milestone. Collection Luck stacks with progression-rod rarity luck and affects weighted fish selection, but never bypasses species requirements such as bait, depth, weather or time.

Milestone player state is stored under:

```text
plugins/CdrMoonFishing/milestones/players/<uuid>.yml
```

Milestone definitions live in:

```text
plugins/CdrMoonFishing/milestones.yml
```

Supported milestone types:

```text
COLLECTION_PERCENT
FIRST_LEGENDARY
RARITY_COMPLETE
```

`RARITY_COMPLETE` is included for larger future fish catalogs, for example rewarding a player after discovering every RARE species.

## Daily Fishing Contracts

Three contracts are selected globally each day by default. Every player sees the same daily set, but progression and rewards are tracked per UUID.

```text
/fishcontracts
```

Aliases: `/fcontracts`, `/fishingcontracts`, `/fq`.

Admin utilities:

```text
/fishcontracts reload
/fishcontracts reset <player>
```

Default contract pool:

```text
Rare Hunter        Catch 5 RARE-or-better fish
Heavy Haul         Catch 30 kg total
Moonlit Koi        Catch 1 Moon Koi at night
Shrimp Specialist  Catch 3 fish using Shrimp bait
Deep Water Hunt    Catch 3 fish at depth 20+
Storm Fisher       Catch 2 fish during thunder
```

A deterministic subset is selected from `contracts.yml` each date. Default timezone is `Asia/Jakarta`.

## Fishing Rod Progression

Only CdrMoonFishing progression rods earn rod XP. Normal vanilla fishing rods still work and receive permanent Collection Luck, but do not receive tier-specific Rod Luck or rod XP progression.

```text
/fishrod
/fishrod info
/fishrod tiers
/fishrod give <player> [tier]
```

Default tiers:

```text
Driftwood   0 XP     • reel 1.00x • luck +0%  • XP 1.00x
Reinforced  250 XP   • reel 1.06x • luck +5%  • XP 1.05x
Oceanic     750 XP   • reel 1.12x • luck +12% • XP 1.10x
Abyssal     1750 XP  • reel 1.20x • luck +22% • XP 1.18x
Lunar       4000 XP  • reel 1.30x • luck +35% • XP 1.30x
```

Tier configuration lives in `rod.yml`.

## Custom fish item providers

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

Modes: `POINTS`, `TOTAL_WEIGHT`, `BIGGEST`.

## Global leaderboard

```text
/fishleaderboard [catches|weight|biggest|legendary] [page]
```

## Fish Market

```text
/fishmarket
/fishmarket price
/fishmarket sellhand
/fishmarket sellall
/fishmarket featured
```

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
build/libs/CdrMoonFishing-0.9.5.jar
```

## Commands

```text
/fishmilestones [status|reload|reset]
/fishcontracts [reload|reset]
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

- v1.0.0 — production polish, GUI pass, balancing, anti-exploit and config/message cleanup
- later — rod perks/builds, fishing streak/combo mechanics, larger fish catalog, mutations/variants and richer collection progression
