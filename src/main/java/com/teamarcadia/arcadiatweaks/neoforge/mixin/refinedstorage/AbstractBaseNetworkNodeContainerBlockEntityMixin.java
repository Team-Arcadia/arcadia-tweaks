package com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage;

import com.refinedmods.refinedstorage.common.support.network.AbstractBaseNetworkNodeContainerBlockEntity;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaActivenessDebounce;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaRefinedStorageNodeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AbstractBaseNetworkNodeContainerBlockEntity.class)
public abstract class AbstractBaseNetworkNodeContainerBlockEntityMixin implements ArcadiaRefinedStorageNodeState {

    /** RS2 debounce counter of updateActiveness ({@code activenessChangeTicks++ % 20}). */
    @Shadow private int activenessChangeTicks;

    @Unique private int arcadia$activenessCheckCooldown;
    @Unique private int arcadia$transmitterStateCheckCooldown;
    @Unique private long arcadia$lastActivenessCheckTick = ArcadiaActivenessDebounce.NEVER_CHECKED;

    @Override
    @Unique
    public int arcadia$getActivenessCheckCooldown() {
        return arcadia$activenessCheckCooldown;
    }

    @Override
    @Unique
    public void arcadia$setActivenessCheckCooldown(int value) {
        arcadia$activenessCheckCooldown = value;
    }

    @Override
    @Unique
    public int arcadia$getTransmitterStateCheckCooldown() {
        return arcadia$transmitterStateCheckCooldown;
    }

    @Override
    @Unique
    public void arcadia$setTransmitterStateCheckCooldown(int value) {
        arcadia$transmitterStateCheckCooldown = value;
    }

    @Override
    @Unique
    public int arcadia$getActivenessChangeTicks() {
        return activenessChangeTicks;
    }

    @Override
    @Unique
    public void arcadia$setActivenessChangeTicks(int value) {
        activenessChangeTicks = value;
    }

    @Override
    @Unique
    public long arcadia$getLastActivenessCheckTick() {
        return arcadia$lastActivenessCheckTick;
    }

    @Override
    @Unique
    public void arcadia$setLastActivenessCheckTick(long gameTime) {
        arcadia$lastActivenessCheckTick = gameTime;
    }
}
