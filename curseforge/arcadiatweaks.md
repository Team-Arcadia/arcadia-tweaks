# ArcadiaTweaks - CurseForge publication

## Recommendation

Use this as the main CurseForge page: **ArcadiaTweaks**.

ArcadiaTweaks is published as a single file, `arcadiatweaks-0.2.0.jar`. It contains every module, and each module stays dormant when its target mod is absent:

- BotanyPots
- Refined Storage
- Mekanism
- Waystones (team visibility through FTB Teams)
- Simply Swords (Runic Forge tablet fix)
- Safety guards (entity movement guard)

## File metadata

| Field | Value |
|---|---|
| File | `arcadiatweaks-0.2.0.jar` |
| Version | `0.2.0` |
| Release type | Beta |
| Minecraft | `1.21.1` |
| Loader | NeoForge |
| Side | Server + optional client patch |
| License | MIT |

## CurseForge relations

| Mod | Relation | Notes |
|---|---|---|
| NeoForge | Required | Loader, built against `21.1.221`, used in the Arcadia modpack on `21.1.264`. |
| BotanyPots | Optional | Botany module only applies when BotanyPots is installed. |
| Bookshelf | Optional | BotanyPots dependency. |
| Prickle | Optional | BotanyPots dependency. |
| Refined Storage | Optional | Refined Storage module only applies when RS2 is installed. |
| Mekanism | Optional | Mekanism module only applies when Mekanism is installed. |
| Waystones | Optional | Waystones module only applies when Waystones and FTB Teams are installed. |
| FTB Teams | Optional | Party lookup for the Waystones module. |
| Simply Swords | Optional | Runic Forge fix only applies when Simply Swords is installed. |

## Project summary

```text
Optimization patches for BotanyPots, Refined Storage and Mekanism, fixes for Waystones and Simply Swords, and a server freeze guard. Mostly server-side, with one optional client-side Mekanism rendering safety patch. Mixin-based, configurable, with per-patch kill switches and runtime safety checks.
```

## Release title

```text
ArcadiaTweaks 0.2.0
```

## Changelog

```markdown
## 0.2.0

### Fixes

- Simply Swords: the Runic Forge no longer creates 8 free Runic Tablets from a unique weapon that never got an awakening level (lootbox, quest or command weapons). The implicit level is locked on the weapon instead: it keeps its power and its gems can still be changed. Kill switch `[safety] runicForgeImplicitLevelLock`.
- Waystones: a "Visible to Team" waystone is only offered to its owner and the owner's FTB Teams party, instead of everyone sharing a vanilla scoreboard team. Kill switches `[modules] waystones` and `[waystones] team_visibility_uses_ftb_teams`.
- Server freeze guard: an entity whose movement for one tick is NaN, infinite or above 100 blocks on one axis is stopped instead of freezing the server. Dedicated servers only, kill switch `[safety] entityMoveGuard`.

### Other changes

- Built against the mod versions the Arcadia modpack runs (BotanyPots 21.1.44, Refined Storage 2.0.9, Waystones 21.1.46, Simply Swords 1.70.2).
- The admin command is now `/arcadia_tweaks gui` (was `/arcadiatweaks gui`).
- The admin GUI has a fourth row for the Waystones module.
- One published file, `arcadiatweaks-0.2.0.jar`, with every module.

### Admin GUI

Use:

```text
/arcadia_tweaks gui
```

### Production notes

Replace any previous ArcadiaTweaks jar (`arcadiatweaks-0.1.1-all.jar` or a single-patch jar) with this one. Keep only one ArcadiaTweaks jar in `mods/`.
```

## Project description

```markdown
# ArcadiaTweaks

ArcadiaTweaks is an optimization patch mod for NeoForge 1.21.1.

It is built for large modded production servers where a few specific mods dominate the Server thread in Spark. The mod uses Mixins to reduce repeated tick work while keeping the original gameplay behavior as close as possible.

## Included patches

### BotanyPots

- Caches repeated crop/soil compatibility checks.
- Coalesces expensive pot tick work while compensating growth progress.
- Adds backoff for repeated failed hopper exports.
- Caches required growth tick calculations.

Recommended balanced settings:

```toml
[botany]
matches_cache_enabled = true
tick_coalescing_enabled = true
tick_coalescing_n = 4
hopper_backoff_enabled = true
hopper_backoff_min_ticks = 16
hopper_backoff_max_ticks = 64
required_growth_ticks_cache_enabled = true
light_flag_downgrade_enabled = false
safety_revalidate_period_ticks = 200
```

### Refined Storage

Targets repeated network node/transmitter activeness checks. Importer/exporter work cadence is not slowed; the patch focuses on the repeated active-state checks visible in Spark.

Recommended balanced settings:

```toml
[refinedstorage]
activeness_check_coalescing_enabled = true
activeness_check_interval_ticks = 20
```

### Mekanism

Targets idle transmitter/network scans and Logistical Transporter idle routing, especially large cable/pipe/tube networks repeatedly trying to pull, emit or route items into unavailable destinations.

Recommended balanced settings:

```toml
[mekanism]
transmitter_pull_backoff_enabled = true
transmitter_pull_backoff_max_ticks = 20
network_emit_backoff_enabled = true
network_emit_backoff_max_ticks = 20
logistical_transporter_idle_backoff_enabled = true
logistical_transporter_idle_backoff_max_ticks = 20
```

### Waystones

Waystones decides who sees a "Visible to Team" waystone from the vanilla scoreboard team. Mods that use scoreboard teams for other reasons (tab list sorting, name colors) then show team waystones to unrelated players. With this module, a team waystone is offered to its owner and to the owner's FTB Teams party only. Allies are not included.

```toml
[waystones]
team_visibility_uses_ftb_teams = true
```

### Simply Swords

The Runic Forge turned the implicit awakening level of a unique weapon without awakening data into 8 real Runic Tablets. ArcadiaTweaks locks that implicit level on the weapon the first time it enters the forge: no free tablets, the weapon keeps its power, and its gems can still be changed. Weapons awakened with real tablets are untouched.

### Safety guards

```toml
[safety]
entityMoveGuard = true
entityMoveGuardLimit = 100.0
runicForgeImplicitLevelLock = true
```

`entityMoveGuard` stops an entity whose movement for one tick is NaN, infinite or above the limit, instead of letting the collision search freeze the server. An elytra with rockets moves about 3 blocks per tick, so nothing legitimate comes close to the limit.

## Commands

```text
/arcadia_tweaks gui
```

`admin` is also available as an alias for `gui`.

## Installation

Drop the jar into the server `mods/` folder.

Most patches are server-side. The Mekanism M4 rendering safety patch requires the jar on the client too.

Keep only one ArcadiaTweaks jar in `mods/`.

## Safety model

ArcadiaTweaks does not rely on target mod version strings. It checks the target class/method structure. If a future target mod update changes a patched method, the affected Mixin is skipped and a short warning is logged instead of crashing the whole server.

## License

MIT
```
