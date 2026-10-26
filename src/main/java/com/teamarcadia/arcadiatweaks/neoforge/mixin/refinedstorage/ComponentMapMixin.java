package com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage;

import com.refinedmods.refinedstorage.api.core.component.ComponentMap;
import com.teamarcadia.arcadiatweaks.common.modules.refinedstorage.RefinedStorageRuntimeFlags;
import com.teamarcadia.arcadiatweaks.neoforge.refinedstorage.ArcadiaComponentLookup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

/**
 * S2 - Single hash lookup in RS2 network component access.
 *
 * {@code ComponentMap.getComponent} is called for the energy component on every node work
 * cycle and for storage, graph and autocrafting components all over RS. Plain RS does
 * {@code containsKey} then {@code get} on the same map. An Overwrite is used because the method
 * is six instructions and an injector would allocate a callback object per call on this path;
 * the plugin checks the original body is still exactly those two lookups.
 *
 * @author vyrriox
 */
@Mixin(ComponentMap.class)
public abstract class ComponentMapMixin<C> {

    @Shadow @Final private Map<Class<? extends C>, C> map;

    /**
     * @author vyrriox
     * @reason One hash lookup instead of two; same value, same exception for a missing component.
     */
    @Overwrite
    @SuppressWarnings("unchecked")
    public <I extends C> I getComponent(Class<I> componentType) {
        if (RefinedStorageRuntimeFlags.singleComponentLookup()) {
            return ArcadiaComponentLookup.get(map, componentType);
        }
        if (!map.containsKey(componentType)) {
            throw new IllegalArgumentException("Component not present: " + componentType);
        }
        return (I) map.get(componentType);
    }
}
