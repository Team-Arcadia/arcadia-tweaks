# ArcadiaTweaks

Umbrella mod-patch for the **Arcadia** modpack. Mostly server-side, non-invasive, Mixin-based optimizations and tweaks for NeoForge **1.21.1** (build 21.1.221+). The Mekanism module also contains one optional client-side rendering safety patch.

Current version: **0.2.0** (see [CHANGELOG.md](CHANGELOG.md)).

| Module | Target | Kind |
|--------|--------|------|
| `botany` | [BotanyPots](https://github.com/Darkhax-Minecraft/BotanyPots) | Server tick optimizations (match memoization, tick coalescing, hopper export backoff, growth tick cache) |
| `refinedstorage` | Refined Storage 2 | Coalesced network node and transmitter activeness checks (debounce-correct), single component lookup, cached disk saves, quieter debug logs |
| `mekanism` | Mekanism | Idle cable, network and Logistical Transporter backoff, plus an optional client rendering safety patch |
| `waystones` | Waystones + FTB Teams | "Visible to Team" waystones follow the owner's FTB Teams party |
| `simplyswords` | Simply Swords | Runic Forge no longer creates free Runic Tablets |
| `cosmetic` | CosmeticWeapons + CosmeticArmours | Item presence gate for the advancement tick procedures (fewer inventory scans per player tick) |
| `accessories` | Accessories | `contains` fast path and a lighter per-entity tick |
| `[safety]` | Vanilla | Entity movement guard against runaway velocities that freeze the server |

Every target mod is optional: a module whose mod is absent stays dormant.

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
│   │   │   └── modules/                         # one folder per module: Module + Config (kill switches)
│   │   │       ├── botany/  mekanism/  refinedstorage/
│   │   │       ├── waystones/  simplyswords/
│   │   │       └── cosmetic/  accessories/
│   │   └── neoforge/
│   │       ├── ArcadiaTweaksNeoForge.java       # @Mod entry point
│   │       ├── admin/                           # /arcadia_tweaks gui (server-side inventory menu)
│   │       ├── gametest/                        # NeoForge GameTests (dev only, not shipped)
│   │       ├── support/                         # shared helpers (interval gate, tick coalescing)
│   │       └── mixin/                           # base Mixins + one sub-package and plugin per module
│   └── resources/
│       ├── arcadiatweaks.mixins.json            # Base Mixin config (pipeline check, safety guards)
│       ├── arcadiatweaks.<module>.mixins.json   # One Mixin config per module
│       ├── pack.mcmeta
│       └── META-INF/neoforge.mods.toml
├── src/test/                                    # JUnit tests (helpers, Mixin targets, jar packaging)
└── README.md
```

### Adding a new module

1. Create `common/modules/<id>/<Id>Module.java` implementing `ArcadiaModule`.
2. Create `common/modules/<id>/<Id>Config.java` carrying the ModConfigSpec entries (build them in the constructor, push/pop a `[<id>]` section).
3. Wire it into `ArcadiaConfig` (instantiate the config + add a `MODULE_<ID>_ENABLED` toggle under `[modules]`).
4. Register the module in `ModuleRegistry.bootstrap()`.
5. Drop your Mixin classes under `neoforge/mixin/<id>/`, list them in a new `arcadiatweaks.<id>.mixins.json` with a Mixin plugin that checks the target structure, and register that config in `neoforge.mods.toml` and in the jar variants of `build.gradle`.
6. Cover the plugin in `MixinPluginCompatibilityTest` and the jar contents in `PatchJarContentsTest`.

## Build

Requires **JDK 21**. The Gradle wrapper is included.

```bash
# build every jar and run the unit tests
./gradlew build

# the jar lands in build/libs/arcadiatweaks-<version>.jar
```

Patch-specific jars can be built from the same compiled classes. The default
`build` jar keeps every patch enabled; the variant jars filter the Mixin configs
and optional dependencies declared in `META-INF/neoforge.mods.toml`.

Only the default jar, `arcadiatweaks-<version>.jar`, is published. The variant jars
are kept for internal testing (bisecting a problem down to one module).

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
| all patches | `/arcadia_tweaks gui` |
| BotanyPots only | `/botanypatch gui` |
| Refined Storage only | `/refinedstoragepatch gui` |
| Mekanism only | `/mekanismpatch gui` |
| Waystones only | `/waystonespatch gui` |

`admin` remains available as an alias for `gui`.

### Admin menu

`/arcadia_tweaks gui` opens a paged 9x6 chest menu. It shows one row per module
whose mod is present, with the module switch first, plus a row for the safety
guards. Left and right click change a number, shift-click resets one value to its
default (shown in every tooltip), and the "Recommended defaults" button resets
everything after a second click to confirm. Changes are saved at once and read by
the patches on their next run, with no restart.

Patch compatibility is guarded by structure, not by version string. If a newer
target mod still exposes the same classes, fields and method descriptors, the
patch applies. If a target moved or changed signature, only the affected Mixin is
skipped and ArcadiaTweaks writes one short warning instead of hard-crashing on a
missing injection target. In the all-patches jar, a skipped BotanyPots Mixin does
not disable Refined Storage or Mekanism Mixins, and vice versa.

### Dev runtime mods (`libs/`)

ArcadiaTweaks targets other mods via Mixin, so dev runs need the **exact** jars from production on the classpath. We do not consume them from a public maven; we copy the jars from the `mods/` folder of the production Arcadia modpack instance directly into `libs/`.

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
| `simplyswords-neoforge-<ver>.jar`              | `simplyswords_version` |
| `SimplyTooltips-neoforge-<ver>.jar` (dev runtime) | `simplytooltips_version` |
| `fzzy_config-<ver>+1.21+neoforge.jar` (dev runtime) | `fzzyconfig_version` |
| `kotlinforforge-<ver>-all.jar` (dev runtime)   | `kotlinforforge_version` |
| `CosmeticWeapons - <ver> - 1.21.1 - NeoForge.jar` | `cosmeticweapons_version` |
| `CosmeticArmours - <ver> - 1.21.1 - NeoForge.jar` | `cosmeticarmours_version` |
| `curios-neoforge-<ver>.jar`                    | `curios_version`       |
| `accessories-neoforge-<ver>.jar`               | `accessories_version`  |
| `owo-lib-neoforge-<ver>.jar`                   | `owolib_version`       |
| `fabric-api-base-<ver>.jar` (compile only)     | `fabricapibase_version` |
| `endec-<ver>.jar` (compile only)               | `endec_version`        |
| `Patchouli-1.21.1-<ver>-NEOFORGE.jar` (dev runtime) | `patchouli_version` |
| `geckolib-neoforge-1.21.1-<ver>.jar` (dev runtime) | `geckolib_version`  |
| `recipeessentials-1.21.1-<ver>.jar` (dev runtime) | `recipeessentials_version` |
| `cupboard-1.21.1-<ver>.jar` (dev runtime)      | `cupboard_version`     |

`libs/*.jar` is gitignored - each developer copies their own from the production pack. When the pack updates a targeted mod, refresh `libs/` and bump the corresponding `gradle.properties` values to match the new filenames.

### Run a dev client / server

```bash
./gradlew runClient
./gradlew runServer
```

### Run automated tests

```bash
# JUnit: helpers, Mixin target checks against the jars in libs/, contents of every jar variant
./gradlew test

# NeoForge GameTests in a headless dev server
./gradlew runGameTestServer
```

The suite is 83 JUnit tests and 39 GameTests. `./gradlew runGameTestServer -ParcadiaBench` also runs the microbenchmarks.

The GameTests cover the entity movement guard, the Waystones team visibility (with real FTB Teams parties) and the Runic Forge (a FakePlayer driving the real Simply Swords menu). Simply Swords needs SimplyTooltips, fzzy_config and Kotlin for Forge at runtime, hence the three dev runtime jars above.

The dev runs are pre-configured with `mixin.debug=true` and `mixin.debug.export=true`, so the bytecode of every Mixin-applied class is dumped to `run/.mixin.out/class/` for inspection.

## Install (production)

Drop the jar into your server `mods/` folder. For the Mekanism M4 rendering safety patch, also ship the same jar on clients through the Arcadia launcher. Every target mod is declared **`optional`**: when one is absent, its module's Mixins are skipped and stay dormant. The Simply Swords and Waystones fixes are server logic, but they are also listed for the integrated server, so singleplayer and LAN worlds are covered.

Install `arcadiatweaks-<version>.jar`, the published jar: it carries every module. The entity movement guard applies on dedicated servers only. Keep only one ArcadiaTweaks jar in `mods/` (remove an older `-all` or single-module jar).

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

Defaults: every optimization proven identical by the tests is on by default.
BotanyPots A2 (`light_flag_downgrade_enabled`) stays off because it changes
neighbour updates.

### Module toggles

| Setting | Recommended | Notes |
|---------|-------------|-------|
| `modules.botany` | `true` | Enable BotanyPots patch when BotanyPots is present. |
| `modules.refinedstorage` | `true` | Enable Refined Storage patch when RS2 is present. |
| `modules.mekanism` | `true` | Enable Mekanism cable/network patch when Mekanism is present. |
| `modules.waystones` | `true` | Enable the Waystones team visibility fix when Waystones and FTB Teams are present. |
| `modules.cosmetic` | `true` | Enable the CosmeticWeapons / CosmeticArmours presence gate when those mods are present. |
| `modules.accessories` | `true` | Enable the Accessories fast paths when Accessories 1.1.0-beta.54 is present. |

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
single_component_lookup_enabled = true
storage_save_cache_enabled = true
storage_save_cache_verify_interval = 20
quiet_hot_debug_logs_enabled = true
```

| Setting | Best value | What it does |
|---------|------------|--------------|
| `activeness_check_coalescing_enabled` | `true` | Coalesces node/transmitter activeness state checks while keeping importer/exporter work cadence intact. The skipped ticks are added to Refined Storage's own 20-tick debounce, so its timing is preserved. |
| `activeness_check_interval_ticks` | `20` | Best conservative value. A redstone/energy/network change lands at most `interval - 1` ticks earlier or later than in plain Refined Storage. |
| `single_component_lookup_enabled` | `true` | One hash lookup instead of two for every network component access. Same results, same exception for a missing component. |
| `storage_save_cache_enabled` | `true` | Reuses the encoded form of storage disks that did not change since the last autosave, instead of re-encoding every disk of the world. The saved data is identical. |
| `storage_save_cache_verify_interval` | `20` | Safety net: every Nth save re-encodes the cached disks and compares. On any difference the cache turns itself off until the next restart. `0` never verifies. |
| `quiet_hot_debug_logs_enabled` | `true` | Drops two Refined Storage debug log lines written from the tick loop ("External storage has changed", transmitter retry). Only `logs/debug.log` changes. |

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

### CosmeticWeapons / CosmeticArmours

This module is an optimization.

```toml
[cosmetic]
contains_presence_gate = true
```

| Setting | Best value | What it does |
|---------|------------|--------------|
| `contains_presence_gate` | `true` | Item-presence gate for the 15 advancement tick procedures of the two mods: once a call has scanned the inventory, items that are not in it are answered without a new scan. |

Every player tick, these procedures test up to 22 items each with
`Inventory.contains`, and every call scans the inventory, Curios and Accessories.
With the gate on, the first test of a call runs normally; if it finds nothing,
the items present are listed once and the later tests skip the scan for any item
that is not in that list, since it cannot match. The answers and the advancements
are identical (the differential GameTests compare 822 placements, near misses and
500 random inventories with the gate on and off), and the bench goes from 434 to
100 microseconds per player tick for the 26 procedures. A startup audit turns the
gate off, with a warning, if another mod hooks `Inventory.contains` or the
ItemStack comparison it relies on.

### Accessories

This module is an optimization, pinned to Accessories 1.1.0-beta.54.

```toml
[accessories]
contains_fast_path = true
tick_allocation_trimmed = true
```

| Setting | Best value | What it does |
|---------|------------|--------------|
| `contains_fast_path` | `true` | Skips the accessory slot scan in `Inventory.contains(ItemStack)`, which can never match, while keeping every state-changing call in order. |
| `tick_allocation_trimmed` | `true` | Runs an allocation-trimmed copy of the per-entity Accessories tick (about 45% less allocation per player tick). |

Accessories' extension of `contains(ItemStack)` tests `isEmpty()` where it should
test the opposite, so its scan never changes the answer; the fast path keeps the
calls that change state and drops the rest (1.6 to 0.5-0.7 microseconds per
call). The tick copy makes the same calls in the same order and only creates
slot references, dirty maps and attribute builders when a slot needs them. Both
are matched to the Accessories bytecode by fingerprints: on any other build they
switch themselves off with one warning and Accessories keeps its own behaviour.

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

Reference: the BotanyPots server optimization research notes (2026-04-25, section 5), kept outside this repository.

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
2. Enable `matches_cache_enabled` (S1) only. Bench, validate.
3. Enable `hopper_backoff_enabled` (S3). Bench, validate.
4. Enable `tick_coalescing_enabled` (S2) with `tick_coalescing_n=2`, then `=4`. Bench at each step.
5. Enable `required_growth_ticks_cache_enabled` (A1), and `light_flag_downgrade_enabled` (A2) if the profile still shows headroom.

Keep `safety_revalidate_period_ticks = 200` while ramping — it bounds any cache-staleness bug to ~10 seconds of weirdness max.

## License

MIT — see [LICENSE](LICENSE).

## Credits

Authors: Curveo, Team Arcadia, vyrriox

- **Darkhax** — author of BotanyPots, the mod we're optimizing.
- **NeoForged** team — modding platform.
- **SpongePowered** — Mixin framework.

---

# ArcadiaTweaks (Version Française)

Mod-patch parapluie pour le modpack **Arcadia**. Optimisations et ajustements en grande partie côté serveur, non invasifs, basés sur Mixin, pour NeoForge **1.21.1** (build 21.1.221+). Le module Mekanism contient aussi un correctif optionnel de sécurité du rendu côté client.

Version actuelle : **0.2.0** (voir [CHANGELOG.md](CHANGELOG.md)).

| Module | Cible | Type |
|--------|-------|------|
| `botany` | [BotanyPots](https://github.com/Darkhax-Minecraft/BotanyPots) | Optimisations du tick serveur (mémoïsation des correspondances, regroupement des ticks, temporisation des exports de hopper, cache du tick de croissance) |
| `refinedstorage` | Refined Storage 2 | Vérifications d'activité des nœuds réseau et des transmetteurs regroupées (temporisation respectée), recherche unique de composant, sauvegardes de disques en cache, logs debug allégés |
| `mekanism` | Mekanism | Temporisation des câbles, réseaux et Logistical Transporters inactifs, plus un correctif optionnel de sécurité du rendu client |
| `waystones` | Waystones + FTB Teams | Les waystones « Visible to Team » suivent le groupe FTB Teams du propriétaire |
| `simplyswords` | Simply Swords | La Runic Forge ne crée plus de Runic Tablets gratuits |
| `cosmetic` | CosmeticWeapons + CosmeticArmours | Filtre de présence des objets pour les procédures de progrès exécutées à chaque tick (moins de parcours d'inventaire par tick joueur) |
| `accessories` | Accessories | Raccourci de `contains` et tick d'entité allégé |
| `[safety]` | Vanilla | Garde-fou de mouvement des entités contre les vitesses incontrôlées qui figent le serveur |

Chaque mod cible est optionnel : un module dont le mod est absent reste dormant.

## Principes de conception

1. **Mixin uniquement.** Aucun patch par réflexion, aucun transformateur de classes, aucun AccessTransformer au-delà de ce que NeoForge fournit déjà. Le mod est un empilement de Mixins `@Inject` / `@Redirect` / `@WrapOperation`.
2. **Non invasif.** Aucun changement de surface d'API publique dans les mods ciblés, aucune mutation de recettes / datapacks / registres (KubeJS s'occupe de ce côté du pack), et les changements de rendu côté client se limitent aux options de sécurité explicites de Mekanism.
3. **Modulaire.** Chaque domaine d'optimisation vit derrière un module de fonctionnalité (`botany`, futurs modules…). Chaque module peut être désactivé à l'exécution via le fichier de configuration.
4. **Coupe-circuit par stratégie.** Dans un module, **chaque** chemin Mixin dispose de son propre interrupteur TOML. Tout peut être désactivé sans recompiler.
5. **Compatible avec les addons.** Les Mixins utilisent la priorité par défaut (1000) et ciblent des classes internes stables ; les surfaces SPI / API consommées par les addons (par exemple BotanyTrees) ne sont jamais modifiées.

## Organisation du projet

Un seul projet Gradle. Le code source est séparé au niveau des **packages** en `common/` (logique indépendante du loader) et `neoforge/` (point d'entrée + Mixins). Cela laisse la porte ouverte à une séparation Architectury plus tard si Fabric devient une cible, sans payer la complexité de build dès maintenant.

```
ArcadiaTweaks/
├── build.gradle, settings.gradle, gradle.properties
├── src/main/
│   ├── java/com/teamarcadia/arcadiatweaks/
│   │   ├── ArcadiaTweaks.java                   # MOD_ID, LOGGER
│   │   ├── common/
│   │   │   ├── config/                          # ModConfigSpec root + per-module specs
│   │   │   ├── module/                          # ArcadiaModule SPI + ModuleRegistry
│   │   │   └── modules/                         # one folder per module: Module + Config (kill switches)
│   │   │       ├── botany/  mekanism/  refinedstorage/
│   │   │       ├── waystones/  simplyswords/
│   │   │       └── cosmetic/  accessories/
│   │   └── neoforge/
│   │       ├── ArcadiaTweaksNeoForge.java       # @Mod entry point
│   │       ├── admin/                           # /arcadia_tweaks gui (server-side inventory menu)
│   │       ├── gametest/                        # NeoForge GameTests (dev only, not shipped)
│   │       ├── support/                         # shared helpers (interval gate, tick coalescing)
│   │       └── mixin/                           # base Mixins + one sub-package and plugin per module
│   └── resources/
│       ├── arcadiatweaks.mixins.json            # Base Mixin config (pipeline check, safety guards)
│       ├── arcadiatweaks.<module>.mixins.json   # One Mixin config per module
│       ├── pack.mcmeta
│       └── META-INF/neoforge.mods.toml
├── src/test/                                    # JUnit tests (helpers, Mixin targets, jar packaging)
└── README.md
```

### Ajouter un nouveau module

1. Créer `common/modules/<id>/<Id>Module.java` implémentant `ArcadiaModule`.
2. Créer `common/modules/<id>/<Id>Config.java` portant les entrées ModConfigSpec (les construire dans le constructeur, avec push/pop d'une section `[<id>]`).
3. Le brancher dans `ArcadiaConfig` (instancier la config + ajouter un interrupteur `MODULE_<ID>_ENABLED` sous `[modules]`).
4. Enregistrer le module dans `ModuleRegistry.bootstrap()`.
5. Déposer les classes Mixin sous `neoforge/mixin/<id>/`, les lister dans un nouveau `arcadiatweaks.<id>.mixins.json` avec un plugin Mixin qui vérifie la structure de la cible, puis enregistrer cette configuration dans `neoforge.mods.toml` et dans les variantes de jar de `build.gradle`.
6. Couvrir le plugin dans `MixinPluginCompatibilityTest` et le contenu du jar dans `PatchJarContentsTest`.

## Compilation

Nécessite **JDK 21**. Le wrapper Gradle est inclus.

```bash
# build every jar and run the unit tests
./gradlew build

# the jar lands in build/libs/arcadiatweaks-<version>.jar
```

Des jars spécifiques à un patch peuvent être construits à partir des mêmes classes compilées. Le jar
`build` par défaut garde tous les patchs activés ; les jars de variante filtrent les configurations Mixin
et les dépendances optionnelles déclarées dans `META-INF/neoforge.mods.toml`.

Seul le jar par défaut, `arcadiatweaks-<version>.jar`, est publié. Les jars de variante
sont gardés pour les tests internes (isoler un problème sur un seul module).

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

La commande d'administration en jeu dépend de la variante de jar :

| Variante de jar | Commande |
|-----------------|----------|
| tous les patchs | `/arcadia_tweaks gui` |
| BotanyPots seul | `/botanypatch gui` |
| Refined Storage seul | `/refinedstoragepatch gui` |
| Mekanism seul | `/mekanismpatch gui` |
| Waystones seul | `/waystonespatch gui` |

`admin` reste disponible comme alias de `gui`.

### Menu d'administration

`/arcadia_tweaks gui` ouvre un menu de coffre paginé de 9x6. Il affiche une ligne par module
dont le mod est présent, avec l'interrupteur du module en premier, plus une ligne pour les
gardes-fous. Le clic gauche et le clic droit modifient un nombre, le clic avec Maj remet une
valeur à son défaut (indiqué dans chaque infobulle), et le bouton « Recommended defaults »
remet tout à zéro après un second clic de confirmation. Les changements sont enregistrés
immédiatement et lus par les patchs à leur prochaine exécution, sans redémarrage.

La compatibilité des patchs est vérifiée par la structure, pas par le numéro de version. Si un mod
cible plus récent expose toujours les mêmes classes, champs et descripteurs de méthodes, le patch
s'applique. Si une cible a été déplacée ou a changé de signature, seul le Mixin concerné est
ignoré et ArcadiaTweaks écrit un court avertissement au lieu de planter brutalement sur une cible
d'injection manquante. Dans le jar à tous les patchs, un Mixin BotanyPots ignoré ne désactive pas
les Mixins Refined Storage ou Mekanism, et inversement.

### Mods d'exécution de développement (`libs/`)

ArcadiaTweaks cible d'autres mods via Mixin, donc les exécutions de développement ont besoin des jars **exacts** de production dans le classpath. Ils ne sont pas récupérés depuis un maven public ; les jars sont copiés depuis le dossier `mods/` de l'instance de production du modpack Arcadia directement dans `libs/`.

Jars requis (et la version à faire correspondre dans `gradle.properties`) :

| Fichier dans `libs/`                           | Clé de gradle.properties |
|------------------------------------------------|--------------------------|
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
| `simplyswords-neoforge-<ver>.jar`              | `simplyswords_version` |
| `SimplyTooltips-neoforge-<ver>.jar` (exécution de dev) | `simplytooltips_version` |
| `fzzy_config-<ver>+1.21+neoforge.jar` (exécution de dev) | `fzzyconfig_version` |
| `kotlinforforge-<ver>-all.jar` (exécution de dev) | `kotlinforforge_version` |
| `CosmeticWeapons - <ver> - 1.21.1 - NeoForge.jar` | `cosmeticweapons_version` |
| `CosmeticArmours - <ver> - 1.21.1 - NeoForge.jar` | `cosmeticarmours_version` |
| `curios-neoforge-<ver>.jar`                    | `curios_version`       |
| `accessories-neoforge-<ver>.jar`               | `accessories_version`  |
| `owo-lib-neoforge-<ver>.jar`                   | `owolib_version`       |
| `fabric-api-base-<ver>.jar` (compilation seulement) | `fabricapibase_version` |
| `endec-<ver>.jar` (compilation seulement)      | `endec_version`        |
| `Patchouli-1.21.1-<ver>-NEOFORGE.jar` (exécution de dev) | `patchouli_version` |
| `geckolib-neoforge-1.21.1-<ver>.jar` (exécution de dev) | `geckolib_version` |
| `recipeessentials-1.21.1-<ver>.jar` (exécution de dev) | `recipeessentials_version` |
| `cupboard-1.21.1-<ver>.jar` (exécution de dev) | `cupboard_version`     |

`libs/*.jar` est ignoré par git : chaque développeur copie les siens depuis le pack de production. Lorsque le pack met à jour un mod ciblé, rafraîchir `libs/` et mettre à jour les valeurs correspondantes de `gradle.properties` selon les nouveaux noms de fichiers.

### Lancer un client / serveur de développement

```bash
./gradlew runClient
./gradlew runServer
```

### Lancer les tests automatisés

```bash
# JUnit: helpers, Mixin target checks against the jars in libs/, contents of every jar variant
./gradlew test

# NeoForge GameTests in a headless dev server
./gradlew runGameTestServer
```

La suite compte 83 tests JUnit et 39 GameTests. `./gradlew runGameTestServer -ParcadiaBench` lance aussi les microbenchmarks.

Les GameTests couvrent le garde-fou de mouvement des entités, la visibilité d'équipe de Waystones (avec de vraies parties FTB Teams) et la Runic Forge (un FakePlayer qui manipule le vrai menu de Simply Swords). Simply Swords a besoin de SimplyTooltips, fzzy_config et Kotlin for Forge à l'exécution, d'où les trois jars d'exécution de dev ci-dessus.

Les exécutions de développement sont préconfigurées avec `mixin.debug=true` et `mixin.debug.export=true`, de sorte que le bytecode de chaque classe modifiée par Mixin est exporté dans `run/.mixin.out/class/` pour inspection.

## Installation (production)

Déposer le jar dans le dossier `mods/` du serveur. Pour le correctif de sécurité du rendu Mekanism M4, fournir aussi le même jar aux clients via le launcher Arcadia. Chaque mod cible est déclaré **`optional`** : lorsque l'un est absent, les Mixins de son module sont ignorés et restent dormants. Les correctifs Simply Swords et Waystones relèvent de la logique serveur, mais ils sont aussi listés pour le serveur intégré, ce qui couvre les mondes solo et LAN.

Installer `arcadiatweaks-<version>.jar`, le jar publié : il contient tous les modules. Le garde-fou de mouvement des entités ne s'applique que sur les serveurs dédiés. Ne garder qu'un seul jar ArcadiaTweaks dans `mods/` (retirer un ancien jar `-all` ou à module unique).

Le fichier de configuration est généré au premier lancement à l'emplacement suivant :

```
<server or client>/config/arcadiatweaks-common.toml
```

Le modifier, redémarrer le serveur ou le client, et c'est terminé. Les changements faits via l'interface serveur n'affectent que la configuration du serveur ; les réglages de rendu client doivent être présents sur chaque client.

## Réglages recommandés

Voici les réglages de production recommandés pour Arcadia. Ils privilégient un faible
risque de régression tout en réduisant le travail de tick répété. Chaque réglage est aussi
disponible dans l'interface d'administration en jeu, sauf les réglages de rendu exclusivement client qui
doivent être configurés sur chaque client.

Valeurs par défaut : toute optimisation dont les tests ont prouvé qu'elle donne des résultats
identiques est activée par défaut. BotanyPots A2 (`light_flag_downgrade_enabled`) reste
désactivée, car elle change les mises à jour de voisinage.

### Interrupteurs de modules

| Réglage | Recommandé | Notes |
|---------|------------|-------|
| `modules.botany` | `true` | Active le patch BotanyPots lorsque BotanyPots est présent. |
| `modules.refinedstorage` | `true` | Active le patch Refined Storage lorsque RS2 est présent. |
| `modules.mekanism` | `true` | Active le patch des câbles/réseaux Mekanism lorsque Mekanism est présent. |
| `modules.waystones` | `true` | Active le correctif de visibilité d'équipe des Waystones lorsque Waystones et FTB Teams sont présents. |
| `modules.cosmetic` | `true` | Active le filtre de présence de CosmeticWeapons / CosmeticArmours lorsque ces mods sont présents. |
| `modules.accessories` | `true` | Active les raccourcis d'Accessories lorsque Accessories 1.1.0-beta.54 est présent. |

### BotanyPots

Meilleur profil équilibré :

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

| Réglage | Meilleure valeur | Effet |
|---------|------------------|-------|
| `matches_cache_enabled` | `true` | Le plus gros gain sûr ; met en cache les vérifications de correspondance de recettes plante/sol entre les changements de slot ou d'état. |
| `tick_coalescing_enabled` | `true` | Exécute la logique complète du pot une fois tous les `tick_coalescing_n` ticks, avec compensation par accumulateur. |
| `tick_coalescing_n` | `4` | Meilleur compromis pour les fermes denses. Utiliser `2` si les joueurs remarquent une latence de récolte ou de comparateur, `1` pour désactiver. |
| `hopper_backoff_enabled` | `true` | Évite les exports échoués répétés lorsque l'inventaire en dessous est plein ou indisponible. |
| `hopper_backoff_min_ticks` | `16` | Bon délai après un premier échec, sans que la reprise des exports paraisse figée. |
| `hopper_backoff_max_ticks` | `64` | Plafonne la temporisation exponentielle à environ 3,2 secondes. À augmenter uniquement pour les fermes qui poussent en permanence vers des inventaires pleins. |
| `required_growth_ticks_cache_enabled` | `true` | Met en cache le calcul des ticks de croissance requis jusqu'à un changement de slot ou d'outil. |
| `light_flag_downgrade_enabled` | `false` | À laisser désactivé par défaut. À activer uniquement si Spark montre un coût de mise à jour des voisins ou de la lumière dû aux mises à jour d'inventaire de Botany. |
| `safety_revalidate_period_ticks` | `200` | Revalide les invariants en cache toutes les ~10 secondes. À garder activé en production. |

Profil Botany agressif pour les fermes statiques :

```toml
tick_coalescing_n = 8
hopper_backoff_max_ticks = 200
```

À utiliser uniquement lorsque les fermes sont surtout sans surveillance et que la latence des
comparateurs et des exports est acceptable.

### Refined Storage

Meilleur profil équilibré :

```toml
[refinedstorage]
activeness_check_coalescing_enabled = true
activeness_check_interval_ticks = 20
single_component_lookup_enabled = true
storage_save_cache_enabled = true
storage_save_cache_verify_interval = 20
quiet_hot_debug_logs_enabled = true
```

| Réglage | Meilleure valeur | Effet |
|---------|------------------|-------|
| `activeness_check_coalescing_enabled` | `true` | Regroupe les vérifications d'état d'activité des nœuds et transmetteurs tout en conservant la cadence de travail des importers/exporters. Les ticks sautés sont ajoutés à la temporisation de 20 ticks de Refined Storage, dont le timing est donc préservé. |
| `activeness_check_interval_ticks` | `20` | Meilleure valeur prudente. Un changement de redstone, d'énergie ou de réseau arrive au plus `interval - 1` ticks plus tôt ou plus tard que dans Refined Storage normal. |
| `single_component_lookup_enabled` | `true` | Une seule recherche dans la table au lieu de deux pour chaque accès à un composant du réseau. Mêmes résultats, même exception pour un composant absent. |
| `storage_save_cache_enabled` | `true` | Réutilise la forme encodée des disques de stockage inchangés depuis la dernière sauvegarde automatique, au lieu de réencoder tous les disques du monde. Les données sauvegardées sont identiques. |
| `storage_save_cache_verify_interval` | `20` | Filet de sécurité : une sauvegarde sur N réencode les disques en cache et compare. À la moindre différence, le cache se coupe jusqu'au prochain redémarrage. `0` ne vérifie jamais. |
| `quiet_hot_debug_logs_enabled` | `true` | Supprime deux lignes de log debug de Refined Storage écrites depuis la boucle de tick (« External storage has changed », nouvel essai des transmetteurs). Seul `logs/debug.log` change. |

Utiliser `10` pour une réponse plus rapide de l'état d'activité à la redstone ou à l'énergie. Utiliser `1` pour
désactiver le regroupement sans désactiver tout le module.

### Mekanism

Meilleur profil équilibré :

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

| Réglage | Meilleure valeur | Effet |
|---------|------------------|-------|
| `transmitter_pull_backoff_enabled` | `true` | Temporise les scans de tirage des Universal Cables, Mechanical Pipes et Pressurized Tubes lorsqu'ils ne tirent rien de façon répétée. |
| `transmitter_pull_backoff_max_ticks` | `20` | Plafonne le délai de nouvel essai de tirage à 1 seconde après des tirages à vide répétés. |
| `network_emit_backoff_enabled` | `true` | Temporise les scans d'émission des réseaux d'énergie/fluides/chimiques lorsque le réseau n'émet rien de façon répétée. |
| `network_emit_backoff_max_ticks` | `20` | Plafonne le délai de nouvel essai d'émission à 1 seconde. Les réseaux de chaleur ne sont pas touchés. |
| `logistical_transporter_idle_backoff_enabled` | `true` | Temporise les recalculs répétés de chemin inactif sans cible pour les Logistical Transporters. Cela vise `TransporterStack.calculateIdle` / `TransporterPathfinder.getIdlePath`. |
| `logistical_transporter_idle_backoff_max_ticks` | `20` | Plafonne le délai de nouvel essai du chemin inactif à 1 seconde. Les chemins de destination et de retour ne sont pas limités. |
| `client_logistical_transporter_safe_rendering_enabled` | `true` | Côté client uniquement. Garde les Logistical Transporters colorés visibles, mais évite le chemin de couche colorée translucide de Mekanism soupçonné de provoquer une dégradation progressive des FPS et de la mémoire. Les couleurs de routage des objets restent côté serveur. |
| `client_transmitter_model_cache_max_entries` | `4096` | Côté client uniquement. Vide le cache de modèles baked des transmetteurs de Mekanism s'il grossit de façon anormale. Garde-fou défensif contre les fuites ; les scènes normales devraient rester en dessous. |

Profil Mekanism agressif pour de très grands réseaux de câbles inactifs :

```toml
transmitter_pull_backoff_max_ticks = 40
network_emit_backoff_max_ticks = 40
logistical_transporter_idle_backoff_max_ticks = 40
```

À utiliser uniquement si Spark montre encore des scans de câbles/réseaux Mekanism élevés après le
profil équilibré. Cela peut ajouter jusqu'à ~2 secondes avant qu'une connexion inactive qui échoue
de façon répétée soit réessayée, ou avant les recalculs de chemin d'un transporter inactif.

### Waystones

Ce module est un correctif de comportement, pas une optimisation.

```toml
[waystones]
team_visibility_uses_ftb_teams = true
```

| Réglage | Meilleure valeur | Effet |
|---------|------------------|-------|
| `team_visibility_uses_ftb_teams` | `true` | Propose une waystone « Visible to Team » uniquement à son propriétaire et aux joueurs du groupe FTB Teams du propriétaire. |

Waystones détermine la visibilité TEAM à partir de l'équipe de scoreboard vanilla du propriétaire.
Tout mod qui place des joueurs dans des équipes de scoreboard pour une autre raison (tri de la
liste des joueurs, couleurs de noms) fait alors proposer les waystones TEAM à tous les membres de
cette équipe de scoreboard, y compris dans leur liste de téléportation. Avec ce réglage activé, la
recherche par scoreboard est remplacée par le groupe FTB Teams du propriétaire : les membres du
groupe voient la waystone, les alliés non, et un joueur sans groupe ne voit que les siennes. Le
Mixin est ignoré lorsque FTB Teams n'est pas installé, et Waystones conserve son propre
comportement.

### CosmeticWeapons / CosmeticArmours

Ce module est une optimisation.

```toml
[cosmetic]
contains_presence_gate = true
```

| Réglage | Meilleure valeur | Effet |
|---------|------------------|-------|
| `contains_presence_gate` | `true` | Filtre de présence des objets pour les 15 procédures de progrès exécutées à chaque tick par les deux mods : une fois l'inventaire parcouru par un appel, les objets qui n'y sont pas obtiennent leur réponse sans nouveau parcours. |

À chaque tick joueur, ces procédures testent jusqu'à 22 objets chacune avec
`Inventory.contains`, et chaque appel parcourt l'inventaire, Curios et Accessories.
Avec le filtre, le premier test d'un appel s'exécute normalement ; s'il ne trouve rien,
les objets présents sont listés une fois et les tests suivants sautent le parcours pour
tout objet absent de cette liste, puisqu'il ne peut pas correspondre. Les réponses et les
progrès sont identiques (les GameTests différentiels comparent 822 placements,
quasi-correspondances et 500 inventaires aléatoires filtre activé et désactivé), et le banc
passe de 434 à 100 microsecondes par tick joueur pour les 26 procédures. Un audit au
démarrage coupe le filtre, avec un avertissement, si un autre mod modifie
`Inventory.contains` ou la comparaison d'ItemStack dont il dépend.

### Accessories

Ce module est une optimisation, liée à Accessories 1.1.0-beta.54.

```toml
[accessories]
contains_fast_path = true
tick_allocation_trimmed = true
```

| Réglage | Meilleure valeur | Effet |
|---------|------------------|-------|
| `contains_fast_path` | `true` | Saute le parcours des emplacements d'accessoires dans `Inventory.contains(ItemStack)`, qui ne peut jamais correspondre, tout en gardant dans l'ordre chaque appel qui modifie un état. |
| `tick_allocation_trimmed` | `true` | Exécute une copie du tick d'entité d'Accessories qui alloue moins (environ 45 % de moins par tick joueur). |

L'extension d'Accessories pour `contains(ItemStack)` teste `isEmpty()` là où elle devrait
tester l'inverse, donc son parcours ne change jamais la réponse ; le raccourci garde les
appels qui modifient un état et supprime le reste (1,6 à 0,5-0,7 microseconde par
appel). La copie du tick fait les mêmes appels dans le même ordre et ne crée les références
d'emplacement, tables de modifications et constructeurs d'attributs que lorsqu'un emplacement
en a besoin. Les deux sont liés au bytecode d'Accessories par des empreintes : sur toute autre
version, ils se coupent seuls avec un avertissement et Accessories garde son comportement.

### Gardes-fous

```toml
[safety]
entityMoveGuard = true
entityMoveGuardLimit = 100.0
runicForgeImplicitLevelLock = true
```

| Réglage | Meilleure valeur | Effet |
|---------|------------------|-------|
| `entityMoveGuard` | `true` | Arrête une entité dont le déplacement sur un tick est NaN, infini ou supérieur à `entityMoveGuardLimit`, au lieu de laisser la recherche de collisions figer le serveur. |
| `runicForgeImplicitLevelLock` | `true` | Runic Forge de Simply Swords : aucun Runic Tablet gratuit à partir d'une arme unique dont le niveau d'éveil n'est qu'implicite (voir ci-dessous). |

Simply Swords lit une arme unique sans son composant `simplyswords:awakening` comme entièrement
éveillée (niveau 8) : les armes antérieures au système d'éveil et toutes les armes uniques
distribuées comme objet simple (lootboxes, quêtes, commandes). La Runic Forge transforme le niveau
d'une arme en autant d'objets Runic Tablet, si bien qu'une telle arme donnait 8 tablets qui
n'avaient jamais existé (bug Arcadia #321). Avec ce réglage activé, le niveau implicite est
enregistré sur l'arme sous forme de verrou la première fois qu'elle entre dans la forge : la forge
ne fournit aucun tablet pour elle, l'arme conserve sa puissance, ses gemmes peuvent toujours être
changées, et seuls les emplacements de tablet au-dessus du verrou acceptent un tablet. Les armes
éveillées avec de vrais tablets n'ont jamais de verrou. Les Mixins `simplyswords` sont ignorés
lorsque Simply Swords est absent ou que les composants internes de sa forge ont changé.

## Valider le pipeline Mixin

Au démarrage du serveur, chercher cette ligne dans le journal :

```
[ArcadiaTweaks/INFO]: [Mixin] Pipeline OK - HelloWorldMixin attached to MinecraftServer.runServer.
```

Si elle apparaît, la transformation Mixin fonctionne et de vraies optimisations peuvent être livrées par-dessus.

## Protocole de benchmark (Spark)

Référence : les notes de recherche sur l'optimisation serveur de BotanyPots (2026-04-25, section 5), conservées en dehors de ce dépôt.

### Mods requis sur le serveur de bench

- [Spark](https://spark.lucko.me/) : sampler + moniteur de ticks
- BotanyPots + les addons présents en production (BotanyTrees, etc.)
- ArcadiaTweaks (le build à évaluer)

### Monde de test reproductible

- 30 chunks `entity-ticking` autour du spawn, chargés en permanence (`/forceload add ...`)
- 400 pots : 200 `BASIC` + 200 `HOPPER`
- Mélange de plantes : 50 % de plante simple (blé), 50 % de plante avec `pot_predicate` (pire cas pour le chemin critique de `matches()`)
- Inventaires des hoppers : 100 pots au-dessus d'un coffre vide, 100 pots au-dessus d'un coffre **plein** (pire cas pour S3)
- 1 faux joueur (`/player`) pour garder les chunks chargés

### Mesure

```text
# Baseline (ArcadiaTweaks botany module disabled or jar absent):
warm up 2 minutes, then:
/spark profiler --timeout 180 --thread "Server thread"
/spark tickmonitor 60

# Run with module enabled, repeat the same commands.
# Compare flame graphs in the Spark web viewer.
```

Objectifs : **−80 % de temps cumulé sur `BotanyPotBlockEntity#tickPot`**, MSPT réduit d'au moins 30 % sur le scénario de ferme dense. Voir le document de recherche §5.3 / §5.5 pour les seuils go/no-go de chaque stratégie.

### Passer en production sans surprise

Activer les coupe-circuits un par un :

1. Démarrer avec tout **désactivé** dans `[botany]`, confirmer que le module se charge proprement et que le Mixin hello-world journalise toujours.
2. Activer uniquement `matches_cache_enabled` (S1). Benchmarker, valider.
3. Activer `hopper_backoff_enabled` (S3). Benchmarker, valider.
4. Activer `tick_coalescing_enabled` (S2) avec `tick_coalescing_n=2`, puis `=4`. Benchmarker à chaque étape.
5. Activer `required_growth_ticks_cache_enabled` (A1), et `light_flag_downgrade_enabled` (A2) si le profil montre encore de la marge.

Garder `safety_revalidate_period_ticks = 200` pendant la montée en charge : cela limite tout bug de cache périmé à ~10 secondes d'anomalies au maximum.

## Licence

MIT, voir [LICENSE](LICENSE).

## Crédits

Auteurs : Curveo, Team Arcadia, vyrriox

- **Darkhax** : auteur de BotanyPots, le mod que nous optimisons.
- L'équipe **NeoForged** : plateforme de modding.
- **SpongePowered** : framework Mixin.
