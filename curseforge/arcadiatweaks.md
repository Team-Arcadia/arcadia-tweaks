# ArcadiaTweaks All - CurseForge publication

## Recommendation

Use this as the main CurseForge page: **ArcadiaTweaks**.

This is the default build and the recommended file for most servers. It contains every currently available patch module:

- BotanyPots
- Refined Storage
- Mekanism

Do not install this jar together with the single-patch variants. All variants use the same mod id: `arcadiatweaks`.

## File metadata

| Field | Value |
|---|---|
| File | `arcadiatweaks-0.1.1-all.jar` or `arcadiatweaks-0.1.1.jar` |
| Version | `0.1.1` |
| Release type | Beta |
| Minecraft | `1.21.1` |
| Loader | NeoForge |
| Side | Server + optional client patch |
| License | MIT |

## CurseForge relations

| Mod | Relation | Notes |
|---|---|---|
| NeoForge | Required | Loader, tested on `21.1.221`. |
| BotanyPots | Optional | Botany module only applies when BotanyPots is installed. |
| Bookshelf | Optional | BotanyPots dependency. |
| Prickle | Optional | BotanyPots dependency. |
| Refined Storage | Optional | Refined Storage module only applies when RS2 is installed. |
| Mekanism | Optional | Mekanism module only applies when Mekanism is installed. |

## Project summary

```text
Optimization patches for BotanyPots, Refined Storage and Mekanism. Mostly server-side, with one optional client-side Mekanism rendering safety patch. Mixin-based, configurable, with per-patch kill switches and runtime safety checks.
```

## Release title

```text
ArcadiaTweaks 0.1.1 - All optimization patches
```

## Changelog

```markdown
## 0.1.1 - All patch modules

Initial all-in-one ArcadiaTweaks release for NeoForge 1.21.1.

### Included modules

- BotanyPots: crop/soil memoization, tick coalescing, hopper export backoff and growth tick caching.
- Refined Storage: coalesced network node/transmitter activeness checks.
- Mekanism: conservative backoff for idle transmitter pull scans, network emit scans and logistical transporter idle path recalculations.
- Mekanism client: safe rendering path for colored Logistical Transporters, keeping colors visible while guarding against progressive client FPS/memory degradation.

### Admin GUI

Use:

```text
/arcadiatweaks gui
```

The menu is server-side only and uses a Minecraft inventory screen. Operators can enable/disable modules and tune patch settings without editing the config by hand.

### Safety

- Target mod classes are checked structurally, not by version string.
- If a target method/class changes, only the affected Mixin is skipped.
- One skipped patch does not disable the other modules.
- Every strategy has a config kill switch.

### Production notes

Install only one ArcadiaTweaks variant at a time. Do not combine this jar with `botany`, `refinedstorage` or `mekanism` single-patch jars.
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

## Commands

```text
/arcadiatweaks gui
```

`admin` is also available as an alias for `gui`.

## Installation

Drop the jar into the server `mods/` folder.

Most patches are server-side. The Mekanism M4 rendering safety patch requires the jar on the client too.

Install only one ArcadiaTweaks variant at a time:

- `all`: every patch module
- `botany`: BotanyPots only
- `refinedstorage`: Refined Storage only
- `mekanism`: Mekanism only

## Safety model

ArcadiaTweaks does not rely on target mod version strings. It checks the target class/method structure. If a future target mod update changes a patched method, the affected Mixin is skipped and a short warning is logged instead of crashing the whole server.

## License

MIT
```
