package com.teamarcadia.arcadiatweaks.neoforge.mixin.sparework;

import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.ArcadiaMixinTargetInspector;
import com.teamarcadia.arcadiatweaks.neoforge.sparework.SpareWorkPins;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies a spare work patch only on the code it was proven against. Every patch skips work because of what
 * the original code does, so the mod patches are pinned to the exact bytecode ({@link SpareWorkPins}) and turn
 * themselves off, with one warning, on any other build. A mod that is not installed is skipped silently.
 *
 * @author vyrriox
 */
public final class SpareWorkMixinPlugin implements IMixinConfigPlugin {

    private static final String LEVEL_ACCESSOR = "net.minecraft.world.level.LevelAccessor";
    private static final String STRUCTURE_MANAGER = "net.minecraft.world.level.StructureManager";
    private static final String CHUNK_ACCESS = "net.minecraft.world.level.chunk.ChunkAccess";
    private static final String IMPOSTER = "net.minecraft.world.level.chunk.ImposterProtoChunk";
    private static final String LEVEL_CHUNK = "net.minecraft.world.level.chunk.LevelChunk";
    private static final String GET_CHUNK_DESC = "(IILnet/minecraft/world/level/chunk/status/ChunkStatus;)Lnet/minecraft/world/level/chunk/ChunkAccess;";
    private static final String STRUCTURE_DESC = "Lnet/minecraft/world/level/levelgen/structure/Structure;";
    private static final String LONG_SET_DESC = "Lit/unimi/dsi/fastutil/longs/LongSet;";

    private static final Set<String> LOGGED_SKIPS = new HashSet<>();

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        final String simpleMixinName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (!ArcadiaMixinTargetInspector.hasClass(targetClassName)) {
            return false;
        }
        final List<String> problems = switch (simpleMixinName) {
            case "CachedRecipeListEmptyParallelMixin" -> SpareWorkPins.mismatches(SpareWorkPins.FASTSUITE);
            case "PityLootContextReuseMixin" -> SpareWorkPins.mismatches(SpareWorkPins.SIMPLY_SWORDS);
            case "TackleBoxProximitySkipMixin" -> pendingFieldPresent()
                    ? SpareWorkPins.mismatches(SpareWorkPins.CRABBERS_DELIGHT)
                    : List.of("ChunkAccess.pendingBlockEntities (missing)");
            case "StructureManagerLoadedChunkMixin" -> structureLookupsCompatible()
                    ? List.of()
                    : List.of("StructureManager or ImposterProtoChunk structure methods");
            case "ChunkAccessPendingAccessor" -> pendingFieldPresent()
                    ? List.of()
                    : List.of("ChunkAccess.pendingBlockEntities (missing)");
            default -> List.of("unknown spare work mixin");
        };
        if (problems.isEmpty()) {
            return true;
        }
        if (LOGGED_SKIPS.add(simpleMixinName)) {
            ArcadiaTweaks.LOGGER.warn("[sparework] Skipping {}: target code changed: {}", simpleMixinName, problems);
        }
        return false;
    }

    private static boolean pendingFieldPresent() {
        return ArcadiaMixinTargetInspector.hasField(CHUNK_ACCESS, "pendingBlockEntities", "Ljava/util/Map;");
    }

    /**
     * Vanilla's five lookups go through the chunk call the patch wraps, and {@code ImposterProtoChunk} still
     * answers the structure methods they use from the chunk it wraps.
     */
    public static boolean structureLookupsCompatible() {
        return getsChunk("startsForStructure", "(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Predicate;)Ljava/util/List;")
                && getsChunk("startsForStructure", "(Lnet/minecraft/core/SectionPos;" + STRUCTURE_DESC + ")Ljava/util/List;")
                && getsChunk("fillStartsForStructure", "(" + STRUCTURE_DESC + LONG_SET_DESC + "Ljava/util/function/Consumer;)V")
                && getsChunk("hasAnyStructureAt", "(Lnet/minecraft/core/BlockPos;)Z")
                && getsChunk("getAllStructuresAt", "(Lnet/minecraft/core/BlockPos;)Ljava/util/Map;")
                && imposterDelegates("getStartForStructure", "(" + STRUCTURE_DESC + ")Lnet/minecraft/world/level/levelgen/structure/StructureStart;")
                && imposterDelegates("getReferencesForStructure", "(" + STRUCTURE_DESC + ")" + LONG_SET_DESC)
                && imposterDelegates("getAllReferences", "()Ljava/util/Map;");
    }

    private static boolean getsChunk(String method, String descriptor) {
        return ArcadiaMixinTargetInspector.hasMethodCall(STRUCTURE_MANAGER, method, descriptor, LEVEL_ACCESSOR, "getChunk", GET_CHUNK_DESC);
    }

    private static boolean imposterDelegates(String method, String descriptor) {
        return ArcadiaMixinTargetInspector.hasMethodCall(IMPOSTER, method, descriptor, LEVEL_CHUNK, method, descriptor);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
