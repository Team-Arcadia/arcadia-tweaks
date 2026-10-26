# Server 1 Spark investigation - 2026-05-15

Profile: `2ipVmJVLAr.sparkprofile`

Duration: ~60.5 seconds
Ticks recorded: 653
Effective TPS: ~11.13 over the last minute
MSPT last minute: mean 84.32, median 78.47, p95 105.69, max 2741.93

## Summary

Server 1 is overloaded. This is not mainly Refined Storage anymore, and not the Mekanism network emit path we already patched.

The dominant hot path is Mekanism item transport:

```text
mekanism.common.tile.transmitter.TileEntityTransmitter.tickServer              26.49%
mekanism.common.tile.transmitter.TileEntityLogisticalTransporterBase.onUpdateServer 25.83%
mekanism.common.content.network.transmitter.LogisticalTransporterBase.onUpdateServer 25.36%
mekanism.common.content.network.transmitter.LogisticalTransporterBase.recalculate    23.46%
mekanism.common.content.transporter.TransporterStack.calculateIdle                   23.44%
mekanism.common.content.transporter.TransporterPathfinder.getIdlePath                23.43%
mekanism.common.content.transporter.TransporterPathfinder.getPaths                   23.37%
mekanism.common.content.network.InventoryNetwork.calculateAcceptors                  23.37%
mekanism.common.content.transporter.TransporterManager.getPredictedInsert            23.32%
```

This points to **Logistical Transporters** item pathfinding / idle path recalculation, not Universal Cable / Mechanical Pipe / Pressurized Tube pull scans.

## Current patch status

The existing ArcadiaTweaks patch still appears to be doing its job on the previous targets:

```text
Refined Storage NetworkNodeBlockEntityTicker.tick       2.50%
Refined Storage NetworkTransmitterBlockEntityTicker.tick 0.08%
Mekanism TransmitterNetworkRegistry.onTick              0.66%
Mekanism tile machines                                  0.47%
```

The bad Mekanism cost is a different subsystem:

```text
LogisticalTransporterBase / TransporterPathfinder / InventoryNetwork.calculateAcceptors
```

## Other major costs

```text
Level.tickBlockEntities                                 60.63%
Create SmartBlockEntityTicker.tick                      15.01%
Create VersionedInventoryWrapper.insertItem              9.41%
NeoForge EventBus.post / PlayerTickPost                  2.70%
NaturalSpawner.spawnForChunk                             5.56%
Server networking / player tick                          ~4-5%
Almanac ItemNBTUtil.checkAndFix via ItemStack.getCount    4.55%
```

NeoForge is visible, but it is mostly dispatching mod listeners and inventory/capability calls. It is not the best first patch target.

## Source research

Mekanism source confirms `LogisticalTransporterBase.onUpdateServer` updates all in-transit stacks every server tick and calls `recalculate(...)` when a stack has no path, reaches a decision point, or must retry a destination. That recalculate path can fall into `TransporterStack.calculateIdle`, `TransporterPathfinder.getIdlePath`, `getPaths`, `InventoryNetwork.calculateAcceptors`, and insert simulation.

Relevant upstream source:

- `LogisticalTransporterBase`: https://raw.githubusercontent.com/mekanism/Mekanism/1.21.x/src/main/java/mekanism/common/content/network/transmitter/LogisticalTransporterBase.java
- `TransporterPathfinder`: https://raw.githubusercontent.com/mekanism/Mekanism/1.21.x/src/main/java/mekanism/common/content/transporter/TransporterPathfinder.java

There are also existing upstream reports around Logistical Transporter lag/pathfinding:

- https://github.com/mekanism/Mekanism/issues/6136
- https://github.com/mekanism/Mekanism/issues/7983

## Recommended next patch

Add a new Mekanism strategy specifically for item transporters:

```toml
[mekanism]
logistical_transporter_idle_backoff_enabled = true
logistical_transporter_idle_backoff_max_ticks = 20
logistical_transporter_recalculate_backoff_enabled = true
logistical_transporter_recalculate_backoff_max_ticks = 20
```

Conservative design:

1. Only back off repeated **failed idle recalculations** or repeated no-target recalculations.
2. Do not skip moving stacks that already have a valid path.
3. Reset the backoff immediately when:
   - a stack gets a valid path,
   - an item successfully inserts,
   - the transporter network changes,
   - transmitter connections refresh.
4. Keep the max default at 20 ticks to cap worst-case recovery delay at ~1 second.
5. Add mixin structural safety so if Mekanism changes internals, only this item-transporter patch is skipped.

Expected effect if the profile is representative:

```text
Target reducible area: ~23-26% of Server thread
Realistic safe win: 8-15 percentage points, depending on how many stacks are stuck/idling.
```

## Immediate server-side mitigation

Before a new patch is built:

1. Find the base/player using large Mekanism Logistical Transporter item networks.
2. Replace long item transporter buses with fewer high-level routers, Ender Chests, drawers, or batch transfer blocks where possible.
3. Remove loops and dense grids in logistical transporter networks.
4. Reduce always-active item output into full inventories.
5. Investigate the `Almanac` `ItemNBTUtil.checkAndFix` cost if it remains high after transporters are fixed.

## Priority

P0 for Server 1: Mekanism Logistical Transporter item pathfinding/backoff.

P1 after that: Create inventory insertion hot paths.

P2 after that: Almanac ItemStack NBT check and event-listener cleanup.
