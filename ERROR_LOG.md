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
