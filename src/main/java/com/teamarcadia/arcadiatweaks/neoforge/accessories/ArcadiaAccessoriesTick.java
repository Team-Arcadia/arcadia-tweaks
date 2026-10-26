/*
 * Derived from io.wispforest.accessories.impl.AccessoriesEventHandler#onLivingEntityTick,
 * Accessories 1.1.0-beta.54+1.21.1 (https://github.com/wisp-forest/accessories), under this license:
 *
 * MIT License
 *
 * Copyright (c) 2024 Wisp Forest
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.teamarcadia.arcadiatweaks.neoforge.accessories;

import com.google.common.collect.ImmutableMap;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.accessories.AccessoriesEventHandlerInvoker;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.accessories.AccessoriesHolderImplAccessor;
import io.wispforest.accessories.Accessories;
import io.wispforest.accessories.AccessoriesInternals;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.attributes.AccessoryAttributeBuilder;
import io.wispforest.accessories.api.events.AccessoryChangeCallback;
import io.wispforest.accessories.api.events.ContainersChangeCallback;
import io.wispforest.accessories.api.events.SlotStateChange;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.impl.AccessoriesCapabilityImpl;
import io.wispforest.accessories.impl.AccessoriesContainerImpl;
import io.wispforest.accessories.impl.AccessoriesHolderImpl;
import io.wispforest.accessories.impl.AccessoryNestUtils;
import io.wispforest.accessories.impl.ExpandedSimpleContainer;
import io.wispforest.accessories.networking.AccessoriesNetworking;
import io.wispforest.accessories.networking.client.SyncContainerData;
import io.wispforest.accessories.utils.AttributeUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * T-A: {@code AccessoriesEventHandler.onLivingEntityTick} of Accessories 1.1.0-beta.54 with its
 * per-tick allocations made lazy. Pinned to that build by a bytecode fingerprint.
 *
 * <p>Every call that is not an allocation runs with the same arguments in the same order as upstream.
 * The only differences:
 * <ol>
 *   <li>Per slot, the entity and slot name that {@code createReference(i)} would read are read at the
 *   same moment, but the {@code SlotReference} record is only built when first needed (a non-empty
 *   stack tick or a change branch) and then shared for the rest of that slot, so callbacks still see
 *   one instance per slot per tick. A container that is not {@code AccessoriesContainerImpl} keeps the
 *   eager {@code createReference(i)} call.</li>
 *   <li>The {@code "slot/index"} id is only built inside the change branches.</li>
 *   <li>The two dirty maps and the two attribute builders are created on first use. With no builder,
 *   {@code AttributeUtils} is not called (it returns at once for an empty builder); a sync packet with
 *   one map unused gets a fresh empty {@code HashMap}, as upstream would pass.</li>
 * </ol>
 *
 * @author vyrriox
 */
public final class ArcadiaAccessoriesTick {

    /** Test-only fault injection, 0 in normal play. 1 skips cosmetic change detection. */
    public static volatile int faultForTest = 0;

    private ArcadiaAccessoriesTick() {}

    public static void tick(LivingEntity entity) {
        if (entity.isRemoved()) {
            return;
        }
        final AccessoriesCapability capability = AccessoriesCapability.get(entity);
        if (capability != null) {
            HashMap<String, ItemStack> dirtyStacks = null;
            HashMap<String, ItemStack> dirtyCosmeticStacks = null;
            AccessoryAttributeBuilder removedAttributesBuilder = null;
            AccessoryAttributeBuilder addedAttributesBuilder = null;
            final boolean checkCosmetics = faultForTest != 1;

            final AccessoriesHolderImpl holderImpl = (AccessoriesHolderImpl) capability.getHolder();
            for (Map.Entry<String, AccessoriesContainer> containerEntry
                    : ((AccessoriesHolderImplAccessor) (Object) holderImpl).arcadia$getAllSlotContainers().entrySet()) {
                final AccessoriesContainer container = containerEntry.getValue();
                final ExpandedSimpleContainer accessories = container.getAccessories();
                final ExpandedSimpleContainer cosmetics = container.getCosmeticAccessories();
                final boolean defaultReference = container.getClass() == AccessoriesContainerImpl.class;

                for (int i = 0; i < accessories.getContainerSize(); i++) {
                    SlotReference slotReference;
                    final LivingEntity referenceEntity;
                    final String slotName;
                    if (defaultReference) {
                        // What AccessoriesContainer.createReference(i) reads, read at the same point.
                        referenceEntity = container.capability().entity();
                        slotName = container.getSlotName();
                        slotReference = null;
                    } else {
                        slotReference = container.createReference(i);
                        referenceEntity = null;
                        slotName = container.getSlotName();
                    }
                    final ItemStack currentStack = accessories.getItem(i);
                    if (!currentStack.isEmpty()) {
                        currentStack.inventoryTick(entity.level(), entity, -1, false);
                        final Accessory accessory = AccessoriesAPI.getOrDefaultAccessory(currentStack);
                        if (accessory != null) {
                            if (slotReference == null) {
                                slotReference = SlotReference.of(referenceEntity, slotName, i);
                            }
                            accessory.tick(currentStack, slotReference);
                        }
                    }

                    final ItemStack lastStack = accessories.getPreviousItem(i);
                    if (!entity.level().isClientSide() && !entity.isDeadOrDying()) {
                        if (!ItemStack.matches(currentStack, lastStack)) {
                            if (slotReference == null) {
                                slotReference = SlotReference.of(referenceEntity, slotName, i);
                            }
                            container.getAccessories().setPreviousItem(i, currentStack.copy());
                            if (dirtyStacks == null) {
                                dirtyStacks = new HashMap<>();
                            }
                            dirtyStacks.put(slotName + "/" + i, currentStack.copy());
                            if (!lastStack.isEmpty()) {
                                if (removedAttributesBuilder == null) {
                                    removedAttributesBuilder = new AccessoryAttributeBuilder();
                                }
                                removedAttributesBuilder.addFrom(AccessoriesAPI.getAttributeModifiers(lastStack, slotReference));
                            }

                            if (!currentStack.isEmpty()) {
                                if (addedAttributesBuilder == null) {
                                    addedAttributesBuilder = new AccessoryAttributeBuilder();
                                }
                                addedAttributesBuilder.addFrom(AccessoriesAPI.getAttributeModifiers(currentStack, slotReference));
                            }

                            boolean equipmentChange = false;
                            if (!ItemStack.isSameItem(currentStack, lastStack) || accessories.isSlotFlagged(i)) {
                                AccessoriesAPI.getOrDefaultAccessory(lastStack).onUnequip(lastStack, slotReference);
                                AccessoriesAPI.getOrDefaultAccessory(currentStack).onEquip(currentStack, slotReference);
                                if (entity instanceof ServerPlayer serverPlayer) {
                                    if (!currentStack.isEmpty()) {
                                        Accessories.ACCESSORY_EQUIPPED.trigger(serverPlayer, currentStack, slotReference, false);
                                    }

                                    if (!lastStack.isEmpty()) {
                                        Accessories.ACCESSORY_UNEQUIPPED.trigger(serverPlayer, lastStack, slotReference, false);
                                    }
                                }

                                equipmentChange = true;
                            }

                            AccessoryChangeCallback.EVENT.invoker()
                                    .onChange(lastStack, currentStack, slotReference,
                                            equipmentChange ? SlotStateChange.REPLACEMENT : SlotStateChange.MUTATION);
                            AccessoriesEventHandlerInvoker.arcadia$recursiveStackChange(
                                    slotReference, AccessoryNestUtils.getData(lastStack), AccessoryNestUtils.getData(currentStack));
                        }

                        final ItemStack currentCosmeticStack = cosmetics.getItem(i);
                        final ItemStack lastCosmeticStack = container.getCosmeticAccessories().getPreviousItem(i);
                        if (checkCosmetics && !ItemStack.matches(currentCosmeticStack, lastCosmeticStack)) {
                            cosmetics.setPreviousItem(i, currentCosmeticStack.copy());
                            if (dirtyCosmeticStacks == null) {
                                dirtyCosmeticStacks = new HashMap<>();
                            }
                            dirtyCosmeticStacks.put(slotName + "/" + i, currentCosmeticStack.copy());
                            if (entity instanceof ServerPlayer serverPlayer) {
                                if (slotReference == null) {
                                    slotReference = SlotReference.of(referenceEntity, slotName, i);
                                }
                                if (!currentStack.isEmpty()) {
                                    Accessories.ACCESSORY_EQUIPPED.trigger(serverPlayer, currentStack, slotReference, true);
                                }

                                if (!lastStack.isEmpty()) {
                                    Accessories.ACCESSORY_UNEQUIPPED.trigger(serverPlayer, lastStack, slotReference, true);
                                }
                            }
                        }
                    }
                }
            }

            if (entity.level().isClientSide()) {
                return;
            }

            if (removedAttributesBuilder != null) {
                AttributeUtils.removeTransientAttributeModifiers(entity, removedAttributesBuilder);
            }
            if (addedAttributesBuilder != null) {
                AttributeUtils.addTransientAttributeModifiers(entity, addedAttributesBuilder);
            }
            final Map<AccessoriesContainer, Boolean> updatedContainers = ((AccessoriesCapabilityImpl) capability).getUpdatingInventories();
            capability.updateContainers();
            ContainersChangeCallback.EVENT.invoker().onChange(entity, capability, ImmutableMap.copyOf(updatedContainers));
            if (dirtyStacks != null || dirtyCosmeticStacks != null || !updatedContainers.isEmpty()) {
                final SyncContainerData packet = SyncContainerData.of(
                        entity,
                        updatedContainers.keySet(),
                        dirtyStacks != null ? dirtyStacks : new HashMap<>(),
                        dirtyCosmeticStacks != null ? dirtyCosmeticStacks : new HashMap<>());
                AccessoriesNetworking.sendToTrackingAndSelf(entity, packet);
            }

            updatedContainers.clear();
        }

        final AccessoriesHolderImpl holder = (AccessoriesHolderImpl) AccessoriesInternals.getHolder(entity);
        if (((AccessoriesHolderImplAccessor) (Object) holder).arcadia$isLoadedFromTag() && capability == null) {
            new AccessoriesCapabilityImpl(entity);
        }

        final List<ItemStack> invalidStacks = holder.invalidStacks;
        if (!invalidStacks.isEmpty()) {
            for (ItemStack invalidStack : invalidStacks) {
                if (entity instanceof ServerPlayer serverPlayer) {
                    AccessoriesInternals.giveItemToPlayer(serverPlayer, invalidStack);
                } else {
                    entity.spawnAtLocation(invalidStack);
                }
            }

            invalidStacks.clear();
        }
    }
}
