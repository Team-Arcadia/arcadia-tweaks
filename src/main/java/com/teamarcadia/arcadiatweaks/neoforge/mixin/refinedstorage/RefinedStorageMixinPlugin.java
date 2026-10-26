package com.teamarcadia.arcadiatweaks.neoforge.mixin.refinedstorage;

import com.teamarcadia.arcadiatweaks.ArcadiaTweaks;
import com.teamarcadia.arcadiatweaks.neoforge.mixin.ArcadiaMixinTargetInspector;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RefinedStorageMixinPlugin implements IMixinConfigPlugin {

    private static final String BASE_NODE = "com.refinedmods.refinedstorage.common.support.network.AbstractBaseNetworkNodeContainerBlockEntity";
    private static final String TRANSMITTER = "com.refinedmods.refinedstorage.common.networking.NetworkTransmitterBlockEntity";
    private static final String EXTERNAL_STORAGE = "com.refinedmods.refinedstorage.common.storage.externalstorage.AbstractExternalStorageBlockEntity";
    private static final String COMPONENT_MAP = "com.refinedmods.refinedstorage.api.core.component.ComponentMap";
    private static final String PLATFORM_STORAGE = "com.refinedmods.refinedstorage.common.storage.PlatformStorage";
    private static final String LIMITED_PLATFORM_STORAGE = "com.refinedmods.refinedstorage.common.storage.LimitedPlatformStorage";
    private static final String PROXY_STORAGE = "com.refinedmods.refinedstorage.api.storage.AbstractProxyStorage";
    private static final String STORAGE_REPOSITORY = "com.refinedmods.refinedstorage.common.storage.StorageRepositoryImpl";
    private static final String SAME_TYPE_STORAGE_TYPE = "com.refinedmods.refinedstorage.common.storage.SameTypeStorageType";

    private static final String UPDATE_ACTIVENESS_DESC =
            "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/properties/BooleanProperty;)V";
    private static final String STORAGE_OP_DESC =
            "(Lcom/refinedmods/refinedstorage/api/resource/ResourceKey;JLcom/refinedmods/refinedstorage/api/core/Action;Lcom/refinedmods/refinedstorage/api/storage/Actor;)J";
    private static final String LOAD_DESC = "(Lcom/refinedmods/refinedstorage/common/storage/StorageCodecs$StorageResource;)V";
    private static final String DEBUG_ONE_ARG = "(Ljava/lang/String;Ljava/lang/Object;)V";
    private static final String DEBUG_TWO_ARGS = "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V";

    /**
     * Methods of the disk classes in RS 2.0.9. A disk's saved content must only change through
     * insert, extract and load; any method added or removed by a future RS version turns S3 off.
     */
    static final Set<String> PLATFORM_STORAGE_METHODS = Set.of(
            "<init> (Lcom/refinedmods/refinedstorage/api/storage/Storage;Lcom/refinedmods/refinedstorage/common/api/storage/StorageType;Lcom/refinedmods/refinedstorage/api/storage/tracked/TrackedStorageRepository;Ljava/lang/Runnable;)V",
            "load " + LOAD_DESC,
            "extract " + STORAGE_OP_DESC,
            "insert " + STORAGE_OP_DESC,
            "getType ()Lcom/refinedmods/refinedstorage/common/api/storage/StorageType;",
            "findTrackedResourceByActorType (Lcom/refinedmods/refinedstorage/api/resource/ResourceKey;Ljava/lang/Class;)Ljava/util/Optional;",
            "lambda$load$0 (Lcom/refinedmods/refinedstorage/api/resource/ResourceKey;Lcom/refinedmods/refinedstorage/common/storage/StorageCodecs$StorageChangedByAt;)V"
    );
    static final Set<String> LIMITED_PLATFORM_STORAGE_METHODS = Set.of(
            "<init> (Lcom/refinedmods/refinedstorage/api/storage/limited/LimitedStorageImpl;Lcom/refinedmods/refinedstorage/common/api/storage/StorageType;Lcom/refinedmods/refinedstorage/api/storage/tracked/TrackedStorageRepository;Ljava/lang/Runnable;)V",
            "getCapacity ()J"
    );
    static final Set<String> PROXY_STORAGE_METHODS = Set.of(
            "<init> (Lcom/refinedmods/refinedstorage/api/storage/Storage;)V",
            "extract " + STORAGE_OP_DESC,
            "insert " + STORAGE_OP_DESC,
            "getAll ()Ljava/util/Collection;",
            "getStored ()J"
    );
    static final List<String> COMPONENT_LOOKUP_CALLS = List.of(
            "java/util/Map.containsKey (Ljava/lang/Object;)Z",
            "java/lang/String.valueOf (Ljava/lang/Object;)Ljava/lang/String;",
            "java/lang/IllegalArgumentException.<init> (Ljava/lang/String;)V",
            "java/util/Map.get (Ljava/lang/Object;)Ljava/lang/Object;"
    );

    private static final Set<String> LOGGED_SKIPS = new HashSet<>();
    private static Boolean storageSaveCacheCompatible;

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
        final boolean compatible = switch (simpleMixinName) {
            case "AbstractBaseNetworkNodeContainerBlockEntityMixin" -> baseNodeCompatible();
            case "NetworkNodeBlockEntityTickerMixin" -> baseNodeCompatible()
                    && ArcadiaMixinTargetInspector.hasMethod(targetClassName, "tick",
                    "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lcom/refinedmods/refinedstorage/common/support/network/AbstractBaseNetworkNodeContainerBlockEntity;)V");
            case "NetworkTransmitterBlockEntityTickerMixin" -> baseNodeCompatible()
                    && ArcadiaMixinTargetInspector.hasMethod(TRANSMITTER, "updateStateInLevel",
                    "(Lnet/minecraft/world/level/block/state/BlockState;)V")
                    && ArcadiaMixinTargetInspector.hasMethod(targetClassName, "tick",
                    "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lcom/refinedmods/refinedstorage/common/networking/NetworkTransmitterBlockEntity;)V");
            case "ComponentMapMixin" -> componentMapCompatible(targetClassName);
            case "PlatformStorageMixin", "StorageRepositoryImplMixin" -> storageSaveCacheCompatible();
            case "ExternalStorageDebugLogMixin" -> ArcadiaMixinTargetInspector.hasMethodCall(
                    targetClassName, "doWork", "()V", "org.slf4j.Logger", "debug", DEBUG_ONE_ARG);
            case "NetworkTransmitterDebugLogMixin" -> ArcadiaMixinTargetInspector.hasMethodCall(
                    targetClassName, "tryReconnectingWithReceiver", "()V", "org.slf4j.Logger", "debug", DEBUG_TWO_ARGS);
            default -> true;
        };
        if (!compatible && LOGGED_SKIPS.add(simpleMixinName)) {
            ArcadiaTweaks.LOGGER.warn("[refinedstorage] Skipping {}: target structure changed in {}.", simpleMixinName, targetClassName);
        }
        return compatible;
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

    /**
     * S1 needs updateActiveness, its debounce counter field and the 20-tick debounce itself
     * ({@code bipush 20}, {@code irem}). S1 without a known debounce is not safe, so all three
     * S1 Mixins are skipped together when any of it changed.
     */
    static boolean baseNodeCompatible() {
        return ArcadiaMixinTargetInspector.hasMethod(BASE_NODE, "updateActiveness", UPDATE_ACTIVENESS_DESC)
                && ArcadiaMixinTargetInspector.hasField(BASE_NODE, "activenessChangeTicks", "I")
                && ArcadiaMixinTargetInspector.hasIntConstantInMethod(BASE_NODE, "updateActiveness", UPDATE_ACTIVENESS_DESC, 20)
                && ArcadiaMixinTargetInspector.hasOpcodeInMethod(BASE_NODE, "updateActiveness", UPDATE_ACTIVENESS_DESC, Opcodes.IREM);
    }

    /** S2 replaces the body, so it must still be exactly containsKey, the exception path, then get. */
    static boolean componentMapCompatible(String targetClassName) {
        return COMPONENT_MAP.equals(targetClassName)
                && ArcadiaMixinTargetInspector.hasField(COMPONENT_MAP, "map", "Ljava/util/Map;")
                && COMPONENT_LOOKUP_CALLS.equals(ArcadiaMixinTargetInspector.methodCallsInMethod(
                        COMPONENT_MAP, "getComponent", "(Ljava/lang/Class;)Ljava/lang/Object;"));
    }

    /**
     * S3 is all or nothing: both Mixins are gated on this one result, computed once. A cache
     * without version tracking would save stale disks.
     */
    static synchronized boolean storageSaveCacheCompatible() {
        if (storageSaveCacheCompatible == null) {
            storageSaveCacheCompatible = computeStorageSaveCacheCompatible();
        }
        return storageSaveCacheCompatible;
    }

    private static boolean computeStorageSaveCacheCompatible() {
        return PLATFORM_STORAGE_METHODS.equals(ArcadiaMixinTargetInspector.declaredMethods(PLATFORM_STORAGE))
                && LIMITED_PLATFORM_STORAGE_METHODS.equals(ArcadiaMixinTargetInspector.declaredMethods(LIMITED_PLATFORM_STORAGE))
                && PROXY_STORAGE_METHODS.equals(ArcadiaMixinTargetInspector.declaredMethods(PROXY_STORAGE))
                && ArcadiaMixinTargetInspector.hasField(PROXY_STORAGE, "delegate", "Lcom/refinedmods/refinedstorage/api/storage/Storage;")
                && ArcadiaMixinTargetInspector.hasMethodCall(STORAGE_REPOSITORY, "createCodec",
                        "(Ljava/lang/Runnable;)Lcom/mojang/serialization/Codec;",
                        "com.refinedmods.refinedstorage.common.storage.ErrorHandlingMapCodec", "<init>",
                        "(Lcom/mojang/serialization/Codec;Lcom/mojang/serialization/Codec;)V")
                && ArcadiaMixinTargetInspector.hasMethod(STORAGE_REPOSITORY, "save",
                        "(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;")
                && ArcadiaMixinTargetInspector.hasMethodCall(SAME_TYPE_STORAGE_TYPE, "lambda$createStorage$3",
                        "(Lcom/refinedmods/refinedstorage/api/storage/tracked/TrackedStorageImpl;Lcom/refinedmods/refinedstorage/api/storage/tracked/TrackedStorageRepository;Ljava/lang/Runnable;)Lcom/refinedmods/refinedstorage/common/storage/PlatformStorage;",
                        PLATFORM_STORAGE, "<init>",
                        "(Lcom/refinedmods/refinedstorage/api/storage/Storage;Lcom/refinedmods/refinedstorage/common/api/storage/StorageType;Lcom/refinedmods/refinedstorage/api/storage/tracked/TrackedStorageRepository;Ljava/lang/Runnable;)V")
                && ArcadiaMixinTargetInspector.hasMethodCall(SAME_TYPE_STORAGE_TYPE, "lambda$createStorage$2",
                        "(Lcom/refinedmods/refinedstorage/api/storage/tracked/TrackedStorageImpl;Lcom/refinedmods/refinedstorage/api/storage/tracked/TrackedStorageRepository;Ljava/lang/Runnable;Ljava/lang/Long;)Lcom/refinedmods/refinedstorage/common/storage/PlatformStorage;",
                        LIMITED_PLATFORM_STORAGE, "<init>",
                        "(Lcom/refinedmods/refinedstorage/api/storage/limited/LimitedStorageImpl;Lcom/refinedmods/refinedstorage/common/api/storage/StorageType;Lcom/refinedmods/refinedstorage/api/storage/tracked/TrackedStorageRepository;Ljava/lang/Runnable;)V");
    }
}
