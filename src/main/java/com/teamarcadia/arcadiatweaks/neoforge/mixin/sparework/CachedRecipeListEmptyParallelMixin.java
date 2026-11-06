package com.teamarcadia.arcadiatweaks.neoforge.mixin.sparework;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teamarcadia.arcadiatweaks.common.modules.sparework.SpareWorkFlags;
import com.teamarcadia.arcadiatweaks.neoforge.sparework.SpareWorkCounters;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * FastSuite only parallelizes recipes of vanilla classes, so {@code parallelRecipes} is empty for every recipe
 * type of a mod (all of Create's, for example). Each lookup of such a type still submitted a task to the
 * thread pool, to stream that empty list, and parked the server thread until it answered. The list never
 * changes after the constructor (pinned bytecode), so the task's answer is known and the lookup goes straight
 * to the serial recipes: empty {@code Optional}, or a new mutable list that the caller fills and sorts.
 *
 * @author vyrriox
 */
@Pseudo
@Mixin(targets = "dev.shadowsoffire.fastsuite.CachedRecipeList")
public abstract class CachedRecipeListEmptyParallelMixin {

    @Shadow
    @Final
    private List<?> parallelRecipes;

    @WrapOperation(
            method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;",
            at = @At(value = "INVOKE", target = "Ldev/shadowsoffire/fastsuite/StreamUtils;executeUntil(Ljava/util/concurrent/Callable;JLjava/util/concurrent/TimeUnit;Ljava/lang/Object;Ljava/util/function/Supplier;)Ljava/lang/Object;"),
            require = 0
    )
    private Object arcadia$skipEmptyParallelLookup(
            Callable<?> task, long maxTime, TimeUnit unit, Object noRecipe, Supplier<?> timeoutMessage, Operation<Object> original
    ) {
        if (parallelRecipes.isEmpty() && SpareWorkFlags.fastSuiteSkipEmptyParallelLookup()) {
            SpareWorkCounters.fastSuiteLookupsSkipped++;
            return noRecipe;
        }
        return original.call(task, maxTime, unit, noRecipe, timeoutMessage);
    }

    @WrapOperation(
            method = "getRecipesFor(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Ldev/shadowsoffire/fastsuite/StreamUtils;executeUntil(Ljava/util/concurrent/Callable;JLjava/util/concurrent/TimeUnit;Ljava/lang/Object;Ljava/util/function/Supplier;)Ljava/lang/Object;"),
            require = 0
    )
    private Object arcadia$skipEmptyParallelList(
            Callable<?> task, long maxTime, TimeUnit unit, Object noRecipes, Supplier<?> timeoutMessage, Operation<Object> original
    ) {
        if (parallelRecipes.isEmpty() && SpareWorkFlags.fastSuiteSkipEmptyParallelLookup()) {
            SpareWorkCounters.fastSuiteLookupsSkipped++;
            return new ArrayList<>();
        }
        return original.call(task, maxTime, unit, noRecipes, timeoutMessage);
    }
}
