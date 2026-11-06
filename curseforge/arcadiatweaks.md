# ArcadiaTweaks - CurseForge publication

## Recommendation

Use this as the main CurseForge page: **ArcadiaTweaks**.

ArcadiaTweaks is published as a single file, `arcadiatweaks-0.3.1.jar`. It contains every module, and each module stays dormant when its target mod is absent:

- BotanyPots
- Refined Storage
- Mekanism
- Waystones (team visibility through FTB Teams)
- Simply Swords (Runic Forge tablet fix)
- CosmeticWeapons and CosmeticArmours (item presence gate)
- Accessories (`contains` fast path and lighter entity tick)
- Create: Copycats+ (client crash fix for copycat chunk meshes)
- FastSuite, Simply Swords, Crabber's Delight and vanilla structure lookups (work repeated for nothing, skipped with the same results)
- Safety guards (entity movement guard, client block entity thread guard)

## File metadata

| Field | Value |
|---|---|
| File | `arcadiatweaks-0.3.1.jar` |
| Version | `0.3.1` |
| Release type | Release |
| Minecraft | `1.21.1` |
| Loader | NeoForge |
| Side | Server + client (client crash fixes and an optional rendering patch) |
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
| CosmeticWeapons | Optional | Cosmetic module only applies when CosmeticWeapons or CosmeticArmours is installed. |
| CosmeticArmours | Optional | Same cosmetic module, for the armour advancement procedures. |
| Curios API | Optional | Read by the cosmetic presence gate when Curios is installed. |
| Accessories | Optional | Accessories module only applies on Accessories 1.1.0-beta.54. |
| Create: Copycats+ | Optional | Copycats+ crash fix only applies when Copycats+ is installed. |
| FastSuite | Optional | Spare work patch only applies on FastSuite 6.0.7. |
| Crabber's Delight | Optional | Spare work patch only applies on Crabber's Delight 1.3.1. |

## Project summary

```text
Optimization patches for BotanyPots, Refined Storage, Mekanism, CosmeticWeapons, CosmeticArmours, Accessories, FastSuite and Crabber's Delight, fixes for Waystones and Simply Swords, a Copycats+ client crash fix, a server freeze guard and a client guard for block entity maps. Mostly server-side; the client gets two crash fixes and one optional Mekanism rendering safety patch. Mixin-based, configurable, with per-patch kill switches and runtime safety checks.
```

## Release title

```text
ArcadiaTweaks 0.3.1
```

Every file uses the display name `ArcadiaTweaks <version>` (for example `ArcadiaTweaks 0.3.1`), never the jar file name.

## Changelog

```markdown
## 0.3.1

### Performance

Work the pack repeats for nothing, found with Spark on our live servers and skipped with the same results. Dedicated server only, new module `sparework` with one switch per patch.

- FastSuite: a recipe type with no parallel recipe (all of Create's) no longer goes through the thread pool to stream an empty list. Pinned to FastSuite 6.0.7.
- Simply Swords: a read of a chest slot no longer allocates a stack for the pity loot, and chests roll their pity loot as before. Pinned to Simply Swords 1.70.2.
- Structure lookups, which Twilight Forest makes for every natural spawn: a fully loaded chunk is read directly instead of asking the chunk system for an older status of it.
- Crabber's Delight: the search of the 343 positions around each player, every tick, is skipped when the loaded chunks prove it finds nothing and changes nothing. Pinned to Crabber's Delight 1.3.1.
- CosmeticArmours 1.6: the two new item chains go through the item presence gate.

## 0.3.0

### Performance

- Refined Storage: exporters, interfaces and constructors with an autocrafting upgrade no longer cost a lot when they ask for an item they cannot craft. Each autocrafting calculation copied the whole network first; calculations on the server thread now read it directly, with the same results (bench: 22.7 ms to 0.29 ms per tick). Kill switch `[refinedstorage] autocrafting_live_storage_view_enabled`.
- Refined Storage: idle importers, exporters and crafting steps refused by their machine try again after 2, 4, 8... up to 10 ticks instead of every tick, and work on every cycle again as soon as something moves. Busy nodes are not slowed down, and the first item after an idle period moves at most 10 ticks later (bench: 5.06 ms to 0.72 ms per tick). Kill switches `[refinedstorage] importer_idle_backoff_enabled`, `exporter_idle_backoff_enabled`, `autocrafting_sink_backoff_enabled`, maximum `idle_backoff_max_ticks`.

Also includes the 0.2.2 fixes below.

## 0.2.2

### Fixes

- Copycats+: the client no longer crashes with "Encountered exception while building chunk meshes" (NullPointerException in `isCTEnabled`, seen on copycat slabs). A copycat removed when its chunk unloads or is resent emptied its materials while Sodium was still building a mesh with it; it now keeps them. Kill switch `[safety] copycatsKeepMaterialsOnRemove`.
- Client: no more `ConcurrentModificationException` in `LevelChunk.updateBlockEntityTicker` or `ArrayIndexOutOfBoundsException` in `Object2ObjectOpenHashMap.rehash` while chunks render. A block entity read or write on a client chunk from another thread than the render thread changed the chunk's block entity maps under it; such reads now only read and such writes run on the render thread. The first five calls are written to `latest.log` with the calling mod's stack trace. Kill switch `[safety] clientBlockEntityThreadGuard` in each player's own config.

## 0.2.1

### Fixes

- Simply Swords: the gloam stain purge no longer crashes the server. It removed stain entities while walking the level's entity list, which broke the list under it (Arcadia bug #328, seen when a player was teleported into a dungeon). Kill switch `[safety] gloamStainPurgeDeferredDiscard`.

## 0.2.0

### Fixes

- Simply Swords: the Runic Forge no longer creates 8 free Runic Tablets from a unique weapon that never got an awakening level (lootbox, quest or command weapons). The implicit level is locked on the weapon instead: it keeps its power and its gems can still be changed. Kill switch `[safety] runicForgeImplicitLevelLock`.
- Waystones: a "Visible to Team" waystone is only offered to its owner and the owner's FTB Teams party, instead of everyone sharing a vanilla scoreboard team. Kill switches `[modules] waystones` and `[waystones] team_visibility_uses_ftb_teams`.
- Server freeze guard: an entity whose movement for one tick is NaN, infinite or above 100 blocks on one axis is stopped instead of freezing the server. Dedicated servers only, kill switch `[safety] entityMoveGuard`.
- Refined Storage: activeness changes (power back, redstone mode switched) could be applied up to about 19 seconds late with the activeness coalescing. A change now lands at most `interval - 1` ticks away from plain Refined Storage.
- BotanyPots: the `[modules] botany` switch now really turns every BotanyPots strategy off.

### Performance

- CosmeticWeapons and CosmeticArmours: the 15 advancement procedures that test several items every player tick no longer rescan the inventory, Curios and Accessories for items that are not there. Same answers and advancements (434 to 100 microseconds per player tick in the bench). Switches itself off if another mod hooks `Inventory.contains`. New module `cosmetic`, switch `[cosmetic] contains_presence_gate`.
- Accessories: skips a scan in `Inventory.contains(ItemStack)` that can never match, and the per-entity tick allocates about 45% less. Pinned to Accessories 1.1.0-beta.54 and off on any other build. New module `accessories`, switches `[accessories] contains_fast_path` and `tick_allocation_trimmed`.
- Refined Storage: autosave reuses the encoded form of unchanged disks (95 to 8 ms per save for 200 disks with 5% changed, identical saved data, verified every 20th save), one hash lookup instead of two per network component access, and two debug log lines no longer flood `debug.log`. Switches `storage_save_cache_enabled`, `storage_save_cache_verify_interval`, `single_component_lookup_enabled`, `quiet_hot_debug_logs_enabled`.

### Other changes

- Built against the mod versions the Arcadia modpack runs (BotanyPots 21.1.44, Refined Storage 2.0.9, Waystones 21.1.46, Simply Swords 1.70.2).
- The admin command is now `/arcadia_tweaks gui` (was `/arcadiatweaks gui`).
- The admin GUI is now a paged 9x6 menu: one row per module with its switch first, a safety guards row, the default value in every tooltip and a two-click "Recommended defaults" reset.
- Every optimization proven identical by the tests is on by default. BotanyPots A2 stays off.
- One published file, `arcadiatweaks-0.3.0.jar`, with every module.

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

Targets repeated network node/transmitter activeness checks, autosaves, autocrafting requests that cannot be crafted, and idle nodes. Importers and exporters that keep moving items keep their work cadence; idle ones try less often.

Recommended balanced settings:

```toml
[refinedstorage]
activeness_check_coalescing_enabled = true
activeness_check_interval_ticks = 20
single_component_lookup_enabled = true
storage_save_cache_enabled = true
storage_save_cache_verify_interval = 20
quiet_hot_debug_logs_enabled = true
autocrafting_live_storage_view_enabled = true
importer_idle_backoff_enabled = true
exporter_idle_backoff_enabled = true
autocrafting_sink_backoff_enabled = true
idle_backoff_max_ticks = 10
```

A change of activeness lands at most `interval - 1` ticks earlier or later than in plain Refined Storage. Autosaves reuse the encoded form of unchanged storage disks (the saved data is identical, and a periodic check turns the cache off on any difference), network component lookups use one hash lookup, and two debug log lines written from the tick loop are dropped.

Autocrafting calculations on the server thread read the network directly instead of copying its whole content first (same results). An idle importer, exporter or crafting step refused by its machine tries again after 2, 4, 8... up to `idle_backoff_max_ticks` ticks, and works on every cycle again as soon as something moves; the first item after an idle period moves at most that many ticks later than in plain Refined Storage.

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

### CosmeticWeapons / CosmeticArmours

Every player tick, 17 advancement procedures of the two mods test several items with `Inventory.contains`, and each call scans the inventory, Curios and Accessories. With the gate on, items that are not in the inventory are answered without a new scan. The answers and advancements are identical, and the module turns itself off at startup if another mod hooks `Inventory.contains` or the ItemStack comparison.

```toml
[cosmetic]
contains_presence_gate = true
```

### Accessories

Skips a scan in `Inventory.contains(ItemStack)` that can never match (Accessories' predicate tests `isEmpty()`), and runs an allocation-trimmed copy of the per-entity Accessories tick. Both are matched to the Accessories 1.1.0-beta.54 bytecode and switch themselves off on any other build.

```toml
[accessories]
contains_fast_path = true
tick_allocation_trimmed = true
```

### Spare work

Work other mods and the game repeat for nothing: FastSuite's thread pool for recipe types without parallel recipes, a stack per read of a chest slot in Simply Swords, chunk system calls for the structure data of loaded chunks, and Crabber's Delight's search around every player. Same results at the same moments, on dedicated servers only. The patches on FastSuite, Simply Swords and Crabber's Delight switch themselves off on any other build of those mods.

```toml
[sparework]
fastsuite_skip_empty_parallel_lookup = true
simplyswords_reuse_pity_stack = true
structure_lookup_uses_loaded_chunk = true
crabbersdelight_skip_empty_tackle_box_search = true
```

### Safety guards

```toml
[safety]
entityMoveGuard = true
entityMoveGuardLimit = 100.0
runicForgeImplicitLevelLock = true
gloamStainPurgeDeferredDiscard = true
copycatsKeepMaterialsOnRemove = true
clientBlockEntityThreadGuard = true
```

`entityMoveGuard` stops an entity whose movement for one tick is NaN, infinite or above the limit, instead of letting the collision search freeze the server. An elytra with rockets moves about 3 blocks per tick, so nothing legitimate comes close to the limit.

## Commands

```text
/arcadia_tweaks gui
```

`admin` is also available as an alias for `gui`.

The menu is paged, with one row per present module (module switch first) and a safety guards row. Left and right click change a number, shift-click resets one value, and the "Recommended defaults" button asks for a second click. Changes are saved at once.

## Installation

Drop the jar into the server `mods/` folder.

Most patches are server-side. The Mekanism M4 rendering safety patch requires the jar on the client too.

Keep only one ArcadiaTweaks jar in `mods/`.

## Safety model

ArcadiaTweaks does not rely on target mod version strings. It checks the target class/method structure. If a future target mod update changes a patched method, the affected Mixin is skipped and a short warning is logged instead of crashing the whole server.

## License

MIT
```
