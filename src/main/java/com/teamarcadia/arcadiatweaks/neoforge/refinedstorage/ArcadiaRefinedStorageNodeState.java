package com.teamarcadia.arcadiatweaks.neoforge.refinedstorage;

public interface ArcadiaRefinedStorageNodeState {

    int arcadia$getActivenessCheckCooldown();

    void arcadia$setActivenessCheckCooldown(int value);

    int arcadia$getTransmitterStateCheckCooldown();

    void arcadia$setTransmitterStateCheckCooldown(int value);

    /** RS2's own activeness debounce counter. */
    int arcadia$getActivenessChangeTicks();

    void arcadia$setActivenessChangeTicks(int value);

    /** Game time of the last coalesced activeness check, {@link ArcadiaActivenessDebounce#NEVER_CHECKED} before the first. */
    long arcadia$getLastActivenessCheckTick();

    void arcadia$setLastActivenessCheckTick(long gameTime);
}
