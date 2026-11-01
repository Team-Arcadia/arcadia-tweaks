# Error log

## [2026-10-26 13:44] - GameTest structure loaded with no blocks
**Context:** new SNBT GameTest template `gameteststructures/move_guard_floor.snbt` (stone floor).
**Error:** the floor was air in game; armor stands fell through.
**Root cause:** text structures (`.snbt`) are read with `NbtUtils.snbtToStructure`, which expects the packed layout: `palette` as a list of block state strings and blocks under `data` with `state: "<block state>"`. A `blocks` list with numeric `state` indexes is silently ignored.
**Fix:** wrote the template in the packed layout (`data` + string `palette`).
**Prevention:** write `.snbt` templates in the packed layout; check the template content in the test (`assertBlockPresent`) before relying on it.

## [2026-10-26 13:47] - Test coordinates are one block below the placed structure
**Context:** spawning entities on the template floor at relative y = 1.
**Error:** entities spawned inside the floor layer and moved 0.25 blocks instead of sliding.
**Root cause:** in 1.21.1 the structure is placed one block above the test origin used by `GameTestHelper` relative positions, so the template layer y = 0 is relative y = 1.
**Fix:** floor layer read at relative y = 1, entities spawned at y = 2, precondition asserted with `assertBlockPresent`.
**Prevention:** assert the expected block under any entity a GameTest spawns.

## [2026-10-26 13:45] - FTB Teams login crashes on a FakePlayer
**Context:** creating FTB Teams personal teams and a party for fake players in a GameTest.
**Error:** `Cannot invoke "io.netty.channel.Channel.attr(io.netty.util.AttributeKey)" because the return value of "net.minecraft.network.Connection.channel()" is null` from `TeamManagerImpl.syncAllToPlayer` in `playerLoggedIn`.
**Root cause:** FTB Library checks `connection.hasChannel(...)` before sending; a NeoForge FakePlayer connection has no Netty channel.
**Fix:** the sync is the last step of `playerLoggedIn`, after the personal team is registered; the test tolerates that NPE only when the team was registered and rethrows otherwise. `createParty` and `join` work on fake players.
**Prevention:** expect any mod code that sends packets to a FakePlayer to fail; verify the state it was supposed to build instead.

## [2026-10-26 14:20] - Worktree created on a stale base
**Context:** RS optimization work in an agent worktree; first `./gradlew compileJava`.
**Error:** `package net.darkhax.botanypots.common.impl.block.entity does not exist` and other missing classes.
**Root cause:** the worktree branch started from `main`, while the target jars in `libs/` (RS 2.0.9, BotanyPots 21.1.44...) match `gradle.properties` of `chore/release-0.2.0`; `main` still names the older jar versions, so Gradle found no file for them.
**Fix:** fast-forwarded the worktree branch to `chore/release-0.2.0` (no local commits yet).
**Prevention:** before coding in a worktree, compare its `gradle.properties` versions with the jar names in `libs/` and check which branch the other worktrees start from.

## [2026-10-26 14:30] - Mock server player crashes with Accessories installed
**Context:** `GameTestHelper.makeMockServerPlayerInLevel()` in the cosmetic presence gate GameTests.
**Error:** `UnsupportedOperationException: Payload accessories:main may not be sent to the client!` from `AccessoriesEventHandler.dataSync` during `PlayerList.placeNewPlayer`.
**Root cause:** the mock connection never negotiated Accessories' channel, and Accessories syncs its data on the datapack sync event of the login.
**Fix:** the tests build a `ServerPlayer` directly without placing it (its constructor still loads real `PlayerAdvancements`; Curios and Accessories data work on it).
**Prevention:** with Accessories (owo networking) in the runtime, do not place test players through a mock connection.

## [2026-10-26 14:31] - Mixins.getMixinsForClass is always empty for a target
**Context:** cosmetic startup audit listing the Mixins applied to `Inventory` and `ItemStack`.
**Error:** the listing came back empty although several Mixins were applied to `Inventory`.
**Root cause:** in Mixin 0.8.7 `MixinInfo` calls `addAppliedMixin` on its own `ClassInfo` (the Mixin's), not on the target's, so `Mixins.getMixinsForClass(target)` / `ClassInfo.getAppliedMixins()` of a target is empty.
**Fix:** read the target `ClassInfo`'s private `mixins` set (declared Mixins, a superset of the applied ones) by reflection; the audit checks that our own accessor Mixin is in the list before trusting it, and fails safe otherwise.
**Prevention:** never trust an empty Mixin listing; cross-check it against a Mixin known to be applied.

## [2026-10-26 14:34] - No Curios inventory on players in the dev runtime
**Context:** GameTests putting target items in a Curios slot.
**Error:** `CuriosApi.getCuriosInventory(player)` empty, Curios path never exercised.
**Root cause:** Curios 9 only gives an entity an inventory when some datapack assigns it slots; no mod of the dev runtime assigns player slots (in the modpack, other mods do).
**Fix:** a dev-only `gametestData` source set (`src/gametest/resources`, part of the dev mod in runs, never in a jar) assigns `ring` and `necklace` to players.
**Prevention:** assert that the slot type a test relies on is actually reached (the test counts Curios hits through `Inventory.contains`).

## [2026-10-26 14:35] - Mixin plugin crashed the game on a synthetic switch class
**Context:** first GameTest server start with `AccessoriesMixinPlugin`, whose `postApply` switched on an enum.
**Error:** `IllegalClassLoadError: ...mixin.accessories.AccessoriesMixinPlugin$1 is in a defined mixin package ... owned by arcadiatweaks.accessories.mixins.json and cannot be referenced directly`, game crash while transforming `Inventory`.
**Root cause:** javac compiles a `switch` on an enum from another class into a synthetic `$1` switch-map class next to the switching class. That class sits in the Mixin package, and Mixin refuses to load any class there that is not the plugin itself.
**Fix:** replaced the enum `switch` with `if` comparisons.
**Prevention:** no enum `switch`, anonymous class or inner class in a Mixin plugin; keep plugin logic in a class outside the Mixin package (as `AccessoriesContainsSurgery` does) or use `if` chains and string switches.

## [2026-10-26 14:58] - Bench Refined Storage network moved no items
**Context:** in-server bench, Refined Storage scenario built with `level.setBlock` and disks filled programmatically.
**Error:** exporters exported nothing; the network storage held only the external storage chests; the disk drives stayed inactive.
**Root cause:** two things. A disk drive placed with its default facing had its front (which does not connect) against the cable bus, so each drive formed its own network with no controller and no energy. And a disk put into a drive before the drive has a network is ignored by Refined Storage (`onDiskChanged` skips it) until the next disk change.
**Fix:** drives placed under the bus; disks inserted once every node is on the controller's network; the scenario waits until every node is active and the disks are visible in the network storage; two probes (exporter alone, importer alone) prove items move.
**Prevention:** after building any Refined Storage network in code, check that every node is on the controller's network (`getNetworkForItem() == controller network`) and active, not only that it has a network.

## [2026-10-26 14:55] - Bench saved data grew between runs
**Context:** repeated `runBenchServer` runs.
**Error:** the timed Refined Storage save grew from 31 KB to 217 KB over four runs.
**Root cause:** the GameTest server reuses `run/world`, and the disks created by each run stayed in the `refinedstorage_storages` saved data.
**Fix:** the bench runs in its own `run-bench/` directory whose world is deleted before every run, and the scenario removes its disk storages from the repository on teardown.
**Prevention:** a benchmark that measures saved data must start from a fresh world and clean up what it adds.

## [2026-10-26 15:24] - Bench cosmetic players granted advancements
**Context:** in-server bench `cosmetic` scenario, some players holding one item of a multi-item CosmeticWeapons / CosmeticArmours procedure, meant to complete nothing.
**Error:** 4 cosmetic advancements were done after the first tick; a probe that looked for an item completing nothing found none.
**Root cause:** in both mods a procedure's item list is a set of alternatives: holding any one of its items completes the advancement. There is no "partial chain" state.
**Fix:** holders get their advancement from the procedure itself on a tick fired at build time, as a real player who held the item would have; the scenario records the count before measuring and checks it is unchanged at the end.
**Prevention:** read what a procedure's item list means (any vs all) before building a "near miss" inventory.

## [2026-10-26 16:10] - CurseForge update-file returned HTTP 500
**Context:** renaming already published files with the CurseForge upload API (`POST /api/projects/{id}/update-file`).
**Error:** `{"errorCode":500,"errorMessage":"An unhandled exception occurred while processing the request."}`
**Root cause:** the `metadata` form part was sent with an explicit `;type=application/json` content type; the endpoint only accepts it as a plain form field. A metadata object carrying a `changelog` also returned 500.
**Fix:** send `-F 'metadata={"fileID":<id>,"displayName":"..."}'` with no content type; file ids come from `https://www.curseforge.com/api/v1/mods/<projectId>/files`.
**Prevention:** for update-file, send only the fields to change, as a plain form field, and test on one file first.

