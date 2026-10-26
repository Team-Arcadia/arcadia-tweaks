# ArcadiaTweaks

Umbrella mod-patch for the **Arcadia** modpack. Mostly server-side, non-invasive, Mixin-based optimizations and tweaks for NeoForge **1.21.1** (build 21.1.221+). The Mekanism module also contains one optional client-side rendering safety patch.

> **First module shipped:** `botany` — server-side hot-path optimizations for [BotanyPots](https://github.com/Darkhax-Minecraft/BotanyPots) (Crop/Soil match memoization, hopper-pot export backoff, growth-tick coalescing, etc.).

## Design principles

1. **Mixin only.** No reflection-based patching, no class transformers, no AccessTransformers beyond what NeoForge already provides. The mod is a stack of `@Inject` / `@Redirect` / `@WrapOperation` Mixins.
2. **Non-invasive.** No public API surface change in the targeted mods, no recipe / datapack / registry mutation (KubeJS handles that side of the pack), and client-side rendering changes are limited to explicit Mekanism safety toggles.
3. **Modular.** Each optimization domain lives behind a feature module (`botany`, future modules…). Each module can be disabled at runtime via the config file.
4. **Per-strategy kill-switch.** Inside a module, **every** Mixin path has a dedicated TOML toggle. Anything can be turned off without rebuilding.
5. **Addon-friendly.** Mixins use default priority (1000) and target stable internal classes; we never patch SPI / API surfaces consumed by addons (e.g. BotanyTrees).

## Project layout

Single Gradle project. Source code is split at the **package** level into `common/` (loader-agnostic logic) and `neoforge/` (entry point + Mixins). This keeps the door open for an Architectury split later if Fabric becomes a target, without paying the build complexity now.

```
ArcadiaTweaks/
├── build.gradle, settings.gradle, gradle.properties
├── src/main/
│   ├── java/com/teamarcadia/arcadiatweaks/
│   │   ├── ArcadiaTweaks.java                   # MOD_ID, LOGGER
│   │   ├── common/
│   │   │   ├── config/                          # ModConfigSpec root + per-module specs
│   │   │   ├── module/                          # ArcadiaModule SPI + ModuleRegistry
│   │   │   └── modules/
│   │   │       └── botany/                      # BotanyModule + BotanyConfig (kill-switches)
│   │   └── neoforge/
│   │       ├── ArcadiaTweaksNeoForge.java       # @Mod entry point
│   │       └── mixin/                           # all Mixin classes live here
│   └── resources/
│       ├── arcadiatweaks.mixins.json            # Base Mixin config
│       ├── pack.mcmeta
│       └── META-INF/neoforge.mods.toml
└── README.md
```

### Adding a new module

1. Create `common/modules/<id>/<Id>Module.java` implementing `ArcadiaModule`.
2. Create `common/modules/<id>/<Id>Config.java` carrying the ModConfigSpec entries (build them in the constructor, push/pop a `[<id>]` section).
3. Wire it into `ArcadiaConfig` (instantiate the config + add a `MODULE_<ID>_ENABLED` toggle under `[modules]`).
4. Register the module in `ModuleRegistry.bootstrap()`.
5. Drop your Mixin classes under `neoforge/mixin/<id>/` and list them in `arcadiatweaks.mixins.json`.

## Build

Requires **JDK 21** and the Gradle wrapper.

```bash
# first time only - generate the wrapper jar
gradle wrapper --gradle-version 8.10

# build
./gradlew build

# the jar lands in build/libs/arcadiatweaks-<version>.jar
```

Patch-specific jars can be built from the same compiled classes. The default
`build` jar keeps every patch enabled; the variant jars filter the Mixin configs
and optional dependencies declared in `META-INF/neoforge.mods.toml`.

```bash
# one jar with every patch
./gradlew jarAllPatches

# one jar with only the BotanyPots patch loaded
./gradlew jarBotanyPatch

# one jar with only the Refined Storage patch loaded
./gradlew jarRefinedStoragePatch

# one jar with only the Mekanism patch loaded
./gradlew jarMekanismPatch

# one jar with only the Waystones patch loaded
./gradlew jarWaystonesPatch

# property-based selection
./gradlew jarSelectedPatch -ParcadiaPatch=botany
./gradlew jarSelectedPatch -ParcadiaPatch=refinedstorage
./gradlew jarSelectedPatch -ParcadiaPatch=mekanism
./gradlew jarSelectedPatch -ParcadiaPatch=waystones
./gradlew jarSelectedPatch -ParcadiaPatch=all

# build all variants at once
./gradlew jarPatchVariants
```

The in-game admin command follows the jar variant:

| Jar variant | Command |
|-------------|---------|
| all patches | `/arcadiatweaks gui` |
| BotanyPots only | `/botanypatch gui` |
| Refined Storage only | `/refinedstoragepatch gui` |
| Mekanism only | `/mekanismpatch gui` |
| Waystones only | `/waystonespatch gui` |

`admin` remains available as an alias for `gui`.

Patch compatibility is guarded by structure, not by version string. If a newer
target mod still exposes the same classes, fields and method descriptors, the
patch applies. If a target moved or changed signature, only the affected Mixin is
skipped and ArcadiaTweaks writes one short warning instead of hard-crashing on a
missing injection target. In the all-patches jar, a skipped BotanyPots Mixin does
not disable Refined Storage or Mekanism Mixins, and vice versa.

### Dev runtime mods (`libs/`)

ArcadiaTweaks targets other mods via Mixin, so dev runs need the **exact** jars from production on the classpath. We do not consume them from a public maven; we drop the jars from the production instance directly into `libs/`.

Source path on the maintainer's machine:

```
C:\Users\curveo\curseforge\minecraft\Instances\Arcadia Echoes Of Power V2\mods
```

Required jars (and the version each must match in `gradle.properties`):

| File in `libs/`                                | gradle.properties key  |
|------------------------------------------------|------------------------|
| `botanypots-neoforge-1.21.1-<ver>.jar`         | `botanypots_version`   |
| `bookshelf-neoforge-1.21.1-<ver>.jar`          | `bookshelf_version`    |
| `prickle-neoforge-1.21.1-<ver>.jar`            | `prickle_version`      |
| `refinedstorage-neoforge-<ver>.jar`            | `refinedstorage_version` |
| `Mekanism-1.21.1-<ver>.jar`                    | `mekanism_version`     |
| `waystones-neoforge-1.21.1-<ver>.jar`          | `waystones_version`    |
| `balm-neoforge-1.21.1-<ver>.jar`               | `balm_version`         |
| `ftb-teams-neoforge-<ver>.jar`                 | `ftbteams_version`     |
| `ftb-library-neoforge-<ver>.jar`               | `ftblibrary_version`   |
| `architectury-<ver>-neoforge.jar`              | `architectury_version` |
| `simplyswords-neoforge-<ver>.jar` (compile only) | `simplyswords_version` |

`libs/*.jar` is gitignored - each developer copies their own from the production pack. When the pack updates a targeted mod, refresh `libs/` and bump the corresponding `gradle.properties` values to match the new filenames.

### Run a dev client / server

```bash
./gradlew runClient
./gradlew runServer
```

### Run automated tests

The `test` branch carries a NeoForge GameTest scaffolding (work in progress). On `main`, no automated tests yet.

The dev runs are pre-configured with `mixin.debug=true` and `mixin.debug.export=true`, so the bytecode of every Mixin-applied class is dumped to `run/.mixin.out/class/` for inspection.

## Install (production)

Drop the jar into your server `mods/` folder. For the Mekanism M4 rendering safety patch, also ship the same jar on clients through the Arcadia launcher. ArcadiaTweaks declares **BotanyPots as `optional`** — if BotanyPots is absent the `botany` Mixins simply do not match anything and stay dormant.

The config file is generated on first run at:

```
<server or client>/config/arcadiatweaks-common.toml
```

Edit it, restart the server or client, done. Server GUI changes affect the server config only; client rendering settings must be present on each client.

## Recommended Settings

These are the recommended production settings for Arcadia. They prioritize low
regression risk while still cutting repeated tick work. Every setting is also
available in the in-game admin GUI, except client-only rendering settings that
must be configured on each client.

### Module toggles

| Setting | Recommended | Notes |
|---------|-------------|-------|
| `modules.botany` | `true` | Enable BotanyPots patch when BotanyPots is present. |
| `modules.refinedstorage` | `true` | Enable Refined Storage patch when RS2 is present. |
| `modules.mekanism` | `true` | Enable Mekanism cable/network patch when Mekanism is present. |
| `modules.waystones` | `true` | Enable the Waystones team visibility fix when Waystones and FTB Teams are present. |

### BotanyPots

Best balanced profile:

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

| Setting | Best value | What it does |
|---------|------------|--------------|
| `matches_cache_enabled` | `true` | Biggest safe win; caches crop/soil recipe match checks between slot/state changes. |
| `tick_coalescing_enabled` | `true` | Runs full pot logic once every `tick_coalescing_n` ticks, with accumulator compensation. |
| `tick_coalescing_n` | `4` | Best balance for dense farms. Use `2` if players notice harvest/comparator latency, `1` to disable. |
| `hopper_backoff_enabled` | `true` | Skips repeated failed exports when the inventory below is full or unavailable. |
| `hopper_backoff_min_ticks` | `16` | Good first failure delay without making export recovery feel stale. |
| `hopper_backoff_max_ticks` | `64` | Caps exponential backoff at ~3.2 seconds. Increase only for farms constantly pushing into full inventories. |
| `required_growth_ticks_cache_enabled` | `true` | Caches required growth tick calculation until slot/tool changes. |
| `light_flag_downgrade_enabled` | `false` | Keep off by default. Enable only if Spark shows neighbor/light update cost from Botany inventory updates. |
| `safety_revalidate_period_ticks` | `200` | Revalidates cached invariants every ~10 seconds. Keep this on in production. |

Aggressive Botany profile for static farms:

```toml
tick_coalescing_n = 8
hopper_backoff_max_ticks = 200
```

Use this only when farms are mostly unattended and comparator/export latency is
acceptable.

### Refined Storage

Best balanced profile:

```toml
[refinedstorage]
activeness_check_coalescing_enabled = true
activeness_check_interval_ticks = 20
```

| Setting | Best value | What it does |
|---------|------------|--------------|
| `activeness_check_coalescing_enabled` | `true` | Coalesces node/transmitter activeness state checks while keeping importer/exporter work cadence intact. |
| `activeness_check_interval_ticks` | `20` | Best conservative value. Worst-case redstone/energy active-state visual delay is 19 ticks. |

Use `10` if you want quicker redstone/energy activeness response. Use `1` to
disable coalescing without disabling the whole module.

### Mekanism

Best balanced profile:

```toml
[mekanism]
transmitter_pull_backoff_enabled = true
transmitter_pull_backoff_max_ticks = 20
network_emit_backoff_enabled = true
network_emit_backoff_max_ticks = 20
logistical_transporter_idle_backoff_enabled = true
logistical_transporter_idle_backoff_max_ticks = 20
client_logistical_transporter_safe_rendering_enabled = true
client_transmitter_model_cache_max_entries = 4096
```

| Setting | Best value | What it does |
|---------|------------|--------------|
| `transmitter_pull_backoff_enabled` | `true` | Backs off Universal Cable, Mechanical Pipe and Pressurized Tube pull scans when they repeatedly pull nothing. |
| `transmitter_pull_backoff_max_ticks` | `20` | Caps pull retry delay at 1 second after repeated no-op pulls. |
| `network_emit_backoff_enabled` | `true` | Backs off energy/fluid/chemical network emit scans when the network repeatedly emits nothing. |
| `network_emit_backoff_max_ticks` | `20` | Caps emit retry delay at 1 second. Heat networks are not touched. |
| `logistical_transporter_idle_backoff_enabled` | `true` | Backs off repeated no-target idle path recalculations for Logistical Transporters. This targets `TransporterStack.calculateIdle` / `TransporterPathfinder.getIdlePath`. |
| `logistical_transporter_idle_backoff_max_ticks` | `20` | Caps idle-path retry delay at 1 second. Destination and home paths are not throttled. |
| `client_logistical_transporter_safe_rendering_enabled` | `true` | Client-side only. Keeps colored Logistical Transporters visible, but avoids Mekanism's translucent colored layer path suspected of causing progressive FPS/memory degradation. Item routing colors remain server-side. |
| `client_transmitter_model_cache_max_entries` | `4096` | Client-side only. Clears Mekanism's transmitter baked-model cache if it grows abnormally high. Defensive leak guard; normal scenes should stay below this. |

Aggressive Mekanism profile for very large idle cable networks:

```toml
transmitter_pull_backoff_max_ticks = 40
network_emit_backoff_max_ticks = 40
logistical_transporter_idle_backoff_max_ticks = 40
```

Use this only if Spark still shows Mekanism cable/network scans high after the
balanced profile. It can add up to ~2 seconds before an idle, repeatedly failing
connection retries or idle transporter path recalculations.

### Waystones

This module is a behaviour fix, not an optimization.

```toml
[waystones]
team_visibility_uses_ftb_teams = true
```

| Setting | Best value | What it does |
|---------|------------|--------------|
| `team_visibility_uses_ftb_teams` | `true` | Offers a "Visible to Team" waystone only to its owner and to players in the owner's FTB Teams party. |

Waystones resolves TEAM visibility from the owner's vanilla scoreboard team. Any
mod that puts players into scoreboard teams for another reason (tab list
sorting, name colours) then offers TEAM waystones to everyone in that scoreboard
team, including in their teleport list. With this setting on, the scoreboard
lookup is replaced by the owner's FTB Teams party: party members see the
waystone, allies do not, and a player without a party only sees their own. The
Mixin is skipped when FTB Teams is not installed, and Waystones keeps its own
behaviour.

### Safety guards

```toml
[safety]
entityMoveGuard = true
entityMoveGuardLimit = 100.0
runicForgeImplicitLevelLock = true
```

| Setting | Best value | What it does |
|---------|------------|--------------|
| `entityMoveGuard` | `true` | Stops an entity whose movement for one tick is NaN, infinite or above `entityMoveGuardLimit`, instead of letting the collision search freeze the server. |
| `runicForgeImplicitLevelLock` | `true` | Simply Swords Runic Forge: no free Runic Tablets from a unique weapon whose awakening level is only implicit (see below). |

Simply Swords reads a unique weapon without its `simplyswords:awakening` component as fully
awakened (level 8): weapons from before the awakening system and every unique handed out as a
plain item (lootboxes, quests, commands). The Runic Forge turns a weapon's level into that many
Runic Tablet items, so such a weapon gave 8 tablets that never existed (Arcadia bug #321). With
this setting on, the implicit level is stored on the weapon as a lock the first time it enters
the forge: the forge hands out no tablet for it, the weapon keeps its power, its gems can still
be changed, and only the tablet slots above the lock accept a tablet. Weapons awakened with real
tablets never get a lock. The `simplyswords` Mixins are skipped when Simply Swords is absent or
its forge internals changed.

## Validate the Mixin pipeline

On server start, look for this line in the log:

```
[ArcadiaTweaks/INFO]: [Mixin] Pipeline OK - HelloWorldMixin attached to MinecraftServer.runServer.
```

If you see it, the Mixin transform is working and you can ship real optimizations on top.

## Benchmark protocol (Spark)

Reference: [`_bmad-output/planning-artifacts/botanypots-server-optimization-research-2026-04-25.md`](../_bmad-output/planning-artifacts/botanypots-server-optimization-research-2026-04-25.md), section 5.

### Required mods on the bench server

- [Spark](https://spark.lucko.me/) — sampler + tick monitor
- BotanyPots + the addons present in production (BotanyTrees, etc.)
- ArcadiaTweaks (the build to evaluate)

### Reproducible test world

- 30 chunks `entity-ticking` around spawn, force-loaded (`/forceload add ...`)
- 400 pots: 200 `BASIC` + 200 `HOPPER`
- Crop mix: 50% simple crop (wheat), 50% crop with `pot_predicate` (worst case for the matches() hot path)
- Hopper inventories: 100 pots over an empty chest, 100 pots over a **full** chest (worst case for S3)
- 1 fake player (`/player`) to keep the chunks loaded

### Measurement

```text
# Baseline (ArcadiaTweaks botany module disabled or jar absent):
warm up 2 minutes, then:
/spark profiler --timeout 180 --thread "Server thread"
/spark tickmonitor 60

# Run with module enabled, repeat the same commands.
# Compare flame graphs in the Spark web viewer.
```

Targets: **−80% cumulative time on `BotanyPotBlockEntity#tickPot`**, MSPT cut by at least 30% on the dense farm scenario. See research doc §5.3 / §5.5 for per-strategy go/no-go thresholds.

### Going live without surprises

Ramp the kill-switches one at a time:

1. Boot with everything **off** in `[botany]` — confirm the module loads cleanly and the hello-world Mixin still logs.
2. Enable `s1_matches_cache_enabled` only. Bench, validate.
3. Enable `s3_hopper_backoff_enabled`. Bench, validate.
4. Enable `s2_tick_coalescing_enabled` with `tick_coalescing_n=2`, then `=4`. Bench at each step.
5. Enable `a1_required_growth_ticks_cache_enabled`, `a2_light_flag_downgrade_enabled` if the profile still shows headroom.

Keep `safety_revalidate_period_ticks = 200` while ramping — it bounds any cache-staleness bug to ~10 seconds of weirdness max.

## License

MIT — see [LICENSE](LICENSE).

## Credits

- **Darkhax** — author of BotanyPots, the mod we're optimizing.
- **NeoForged** team — modding platform.
- **SpongePowered** — Mixin framework.
