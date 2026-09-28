# CdrMoonFishing

Crossplay-first custom fishing progression for Paper servers. Core gameplay stays server-side so Java and Bedrock players through Geyser share the same fishing loop.

## Current version

`v1.0.1` — Fishing Hub GUI

## v1.0.1 Fishing Hub GUI

The main player entry point is now:

```text
/cdrfish
/fish
```

`/cdrfish` is the primary command. `/fish` is an alias and may be unavailable if another installed plugin owns that alias.

The 27-slot chest hub is intentionally compact and exposes only the core player flow:

```text
FishDex
Fishing Rod
Daily Contracts
Fishing Status
Fish Market
Tournament
Stats & Rankings
```

The center status card summarizes FishDex completion, selected bait and the current Featured Catch. Feature cards show live data such as rod tier/XP, completed contracts, tournament timer, collection progress and fishing statistics.

FishDex, Tournament and Leaderboard GUIs now include a `Fishing Hub` back button for quick navigation.

## Core gameplay

- Fish rarity, weight, biome, depth, weather and time conditions
- Bait system
- CALM, ERRATIC, AGGRESSIVE and DIVING behavior profiles
- Rare/Epic/Legendary multi-phase encounters
- Persistent FishDex and lifetime player statistics
- FishDex milestones with permanent Collection Luck
- Progression fishing rods with tier XP, reel power and rarity luck
- Daily fishing contracts
- Vault-backed Fish Market + Featured Catch
- Live tournaments + lifetime leaderboards
- ItemsAdder/MMOItems custom fish item providers with vanilla fallback

## v1.0.0 production polish

### FishDex GUI

`/fishdex [page]` opens a crossplay-safe chest GUI.

- 45 species per page
- undiscovered species stay hidden as `???`
- discovered cards show rarity, catch count and personal best weight
- depth and required bait hints
- FishDex completion and permanent Collection Luck summary
- direct access to milestone progress

### Leaderboard GUI

`/fishleaderboard [catches|weight|biggest|legendary] [page]`

Players get an interactive chest GUI and can switch between metrics without retyping commands. Console still receives text output.

### Tournament GUI

`/fishtournament` or `/fishtournament top`

Players get a live tournament board with score, catch count, mode, remaining time and participant count. Admin start/stop/cancel commands remain command based.

### Market anti-duplicate protection

New v1.0 catches receive a unique `catch_uid` PDC identity. The Fish Market stores redeemed identities in:

```text
plugins/CdrMoonFishing/sold-catches.yml
```

A duplicated/copied catch with the same identity cannot be sold twice. Custom ItemsAdder/MMOItems fish preserve the identity when their base item is replaced.

Batch selling also blocks:

- already redeemed catch IDs
- duplicate IDs inside the same sale batch
- identified fish stacked above amount 1

Legacy pre-v1 fish remain sellable by default. After old stock has left the economy, strict mode can be enabled:

```yaml
security:
  market:
    require-catch-uid: true
```

### Safer upgrades

`config.yml` has `config-version`. Missing default keys are copied into existing configs automatically during startup and `/fishing reload`, so normal upgrades do not require deleting configuration files.

### Production diagnostics

```text
/fishdoctor
```

Aliases: `/fdoctor`, `/fishingdoctor`.

The doctor reports plugin/server/Java versions, registry counts, encounter state, statistics cache, tournament state, Vault/integration status, anti-dupe ledger state and data-folder writability.

### Stability fixes

- caches hook location before removing the fishing bobber
- FishDex percentage milestones count only species still present in the active fish registry
- FishDex GUI independently counts active species, preventing removed/renamed fish from pushing completion above 100%

## FishDex milestones

```text
/fishmilestones
/fishmilestones status
```

Aliases: `/fmilestones`, `/fishdexrewards`, `/fdrewards`.

Default progression:

```text
25% FishDex   FishDex Explorer
50% FishDex   FishDex Collector
75% FishDex   FishDex Hunter
100% FishDex  Master of the FishDex
First LEGENDARY catch  Legendary Discovery
```

Rewards can combine Vault money, bait, Rod XP and permanent Collection Luck.

## Daily Fishing Contracts

```text
/fishcontracts
```

Three contracts are selected globally each day by default, with per-player progress. Conditions can use rarity, total weight, species, bait, depth, time and weather.

## Fishing Rod Progression

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

## Fish Market

```text
/fishmarket
/fishmarket price
/fishmarket sellhand
/fishmarket sellall
/fishmarket featured
```

Fish value uses species base price, weight, rarity multiplier, global multiplier and optional Featured Catch bonus.

## Tournament

```text
/fishtournament
/fishtournament top [page]
/fishtournament start <minutes> [points|weight|biggest]
/fishtournament stop
/fishtournament cancel
```

Modes: `POINTS`, `TOTAL_WEIGHT`, `BIGGEST`.

## Requirements

- Paper 1.21.11
- Java 21

Optional:

- Vault + economy provider
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
build/libs/CdrMoonFishing-1.0.1.jar
```

## Commands

```text
/cdrfish
/fish
/fishdex [page]
/fishmilestones [status|reload|reset]
/fishcontracts [reload|reset]
/fishrod [info|tiers|give]
/fishmarket [open|price|sellhand|sellall|featured]
/fishtournament [status|top|start|stop|cancel]
/fishleaderboard [catches|weight|biggest|legendary] [page]
/fishdoctor
/fishing stats [player]
/fishing bait [id|none]
/fishing givebait <player> <id> [amount]
/fishing debug
/fishing cancel
/fishing status
/fishing reload
```

## Post-1.0 direction

The next progression layer is intended to move further toward collection-heavy fishing games: fish mutations/variants, larger species catalogs, rod perk builds and fishing streak/combo systems.
