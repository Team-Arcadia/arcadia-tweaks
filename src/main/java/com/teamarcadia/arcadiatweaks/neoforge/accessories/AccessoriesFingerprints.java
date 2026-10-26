package com.teamarcadia.arcadiatweaks.neoforge.accessories;

import com.teamarcadia.arcadiatweaks.neoforge.mixin.ArcadiaMixinTargetInspector;

import java.util.ArrayList;
import java.util.List;

/**
 * Bytecode the Accessories optimizations are proven against: Accessories 1.1.0-beta.54+1.21.1.
 * Each entry is a {@link ArcadiaMixinTargetInspector#fingerprint} of one upstream method. Any
 * difference (an update, another build, a predicate fix) turns the matching optimization off.
 *
 * <p>Regenerate with {@code AccessoriesFingerprintTest} after re-verifying the proof on a new build.
 *
 * @author vyrriox
 */
public final class AccessoriesFingerprints {

    public record Pin(String className, String method, String descriptor, String sha256) {
        public String describe() {
            return className + "." + method + descriptor;
        }
    }

    private static final String API = "io.wispforest.accessories.api.";
    private static final String IMPL = "io.wispforest.accessories.impl.";
    private static final String ITEM_STACK = "Lnet/minecraft/world/item/ItemStack;";
    private static final String PREDICATE = "Ljava/util/function/Predicate;";
    private static final String STACK_PREDICATE = "Lio/wispforest/accessories/api/caching/ItemStackBasedPredicate;";
    private static final String CHECKING = "Lio/wispforest/accessories/api/EquipmentChecking;";
    private static final String SLOT_REF = "Lio/wispforest/accessories/api/slot/SlotReference;";

    /** Methods whose combined behaviour makes {@code contains(ItemStack)}'s accessory scan dead (C1). */
    public static final List<Pin> CONTAINS = List.of(
            new Pin("io.wispforest.accessories.mixin.InventoryMixin", "extendContainsCheck",
                    "(" + ITEM_STACK + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V",
                    "0768b37029a711952290a1d3db1545f34863942c60536c369e4d8e53ef64659b"),
            new Pin("io.wispforest.accessories.mixin.InventoryMixin", "lambda$extendContainsCheck$1",
                    "(" + ITEM_STACK + ITEM_STACK + ")Z", "deb7cdfd2c31f0c8f083d40c1f9e5f472afedf378a6576bd80d7a04d28dc5915"),
            new Pin(API + "AccessoriesCapability", "isEquipped", "(" + PREDICATE + ")Z", "7ebcf962e02c6f3cce2378851ea3d99b9cad107046718ee69ee4c0607736a84d"),
            new Pin(API + "AccessoriesCapability", "isEquipped", "(" + PREDICATE + CHECKING + ")Z", "5a68bdce76f24e49b6be82a51b969298929f655523732ed11d02b1bca5a2fddd"),
            new Pin(API + "AccessoriesCapability", "isEquipped", "(" + STACK_PREDICATE + CHECKING + ")Z", "cd9956e64fc65424607844997cab85d2f4de23f9bf1ac8c5e0ef5f86b3ce475d"),
            new Pin(API + "caching.ItemStackBasedPredicate", "ofPredicate", "(" + PREDICATE + ")" + STACK_PREDICATE, "3d3870e3db926d19b1826ed9ee365a253c29737f585a4b7880493dda06075277"),
            new Pin(API + "caching.ItemStackBasedPredicate", "ofPredicate",
                    "(Ljava/lang/String;" + PREDICATE + ")" + STACK_PREDICATE, "f3168ea28314b147ae5deee39017978da375e6247ab27a9d919b5474316c9649"),
            new Pin(API + "caching.ItemStackBasedPredicate", "<init>", "(Ljava/lang/String;)V", "c1a97a6c6ca8af23a8c7faa7427a5ae6e6a0f7028e89e7a3038408ca8d90f329"),
            new Pin(API + "caching.ItemStackPredicate", "<init>", "(Ljava/lang/String;" + PREDICATE + ")V", "ee5ede6f58b7ff487ca525639c94d7a81a683afc8614d4866f1497f7d2777799"),
            new Pin(API + "caching.ItemStackPredicate", "test", "(" + ITEM_STACK + ")Z", "83470edfccd9bae86aa4d9a05a901884ac1bb6abfb2b8ae95d5d89e9243f62bb"),
            new Pin(IMPL + "AccessoriesCapabilityImpl", "getFirstEquipped",
                    "(" + STACK_PREDICATE + CHECKING + ")Lio/wispforest/accessories/api/slot/SlotEntryReference;", "4b97f506a9f0f12dc3e5421337171eaec34eb39b1113fb7621333b9ea4647022"),
            new Pin(IMPL + "AccessoriesCapabilityImpl", "lambda$getFirstEquipped$9",
                    "(" + STACK_PREDICATE + SLOT_REF + ITEM_STACK + SLOT_REF + ")Lio/wispforest/accessories/api/slot/SlotEntryReference;",
                    "f17a12d40e39a5749392dcc15fd5a80bc62649a98f5fb94abba3d1ae5387cace"),
            new Pin(IMPL + "AccessoriesCapabilityImpl", "getContainers", "()Ljava/util/Map;", "7f8d3fa3ec2d069ebcff52b27fd7fab3200bc769b4860a1ff970aeacd6ec55b7"),
            new Pin(IMPL + "AccessoriesHolderImpl", "getLookupCache",
                    "()Lio/wispforest/accessories/impl/caching/AccessoriesHolderLookupCache;", "fe201cf62cc60a7ae4200d6072b7e9c88752c4164348397e3cff4d4058effd8a"),
            new Pin(IMPL + "AccessoryNestUtils", "recursiveStackHandling",
                    "(" + ITEM_STACK + SLOT_REF + "Ljava/util/function/BiFunction;)Ljava/lang/Object;", "6124e06060b626a253de526091d93961383f578ffa623de75dbb960df9c8c7a4"),
            new Pin(IMPL + "ExpandedSimpleContainer", "iterator", "()Ljava/util/Iterator;", "a1e265e69d1f6d6f647927fb041beaea7f57bddad58e0daaf823e7faa2f98d79"),
            new Pin(IMPL + "ExpandedSimpleContainer", "getItem", "(I)" + ITEM_STACK, "f29c2ff94a6dc40ab46cfdd5d81170aaeae1b538fe02c6c73f2bf1f9e187c331"),
            new Pin(IMPL + "ExpandedSimpleContainer$1", "hasNext", "()Z", "f1b124b4b7636532577a7d3787e339e0de9341b00409c073920ccee05902b6c3"),
            new Pin(IMPL + "ExpandedSimpleContainer$1", "next", "()Lcom/mojang/datafixers/util/Pair;", "99ef6c192ba06b0f37a5920bffa1df2e1f81ef1a3edb39197fb166bef3e54b50"),
            new Pin(API + "AccessoriesContainer", "createReference", "(I)" + SLOT_REF, "3d8273db70025ad1593cd92b71a2dc17646feb5470e95e5d69df99fca00028f7"),
            new Pin(API + "AccessoriesAPI", "getOrDefaultAccessory", "(" + ITEM_STACK + ")Lio/wispforest/accessories/api/Accessory;", "a30a0103354d55be35c7016b9f42eb3572e2829cd3dbe37eba5027148fc652bc")
    );

    /** The upstream tick T-A copies, and the methods its deferred allocations rely on. */
    public static final List<Pin> TICK = List.of(
            new Pin(IMPL + "AccessoriesEventHandler", "onLivingEntityTick", "(Lnet/minecraft/world/entity/LivingEntity;)V", "4eed925397ce530edc7412a6d8557a4afb5dfbdbee815b105a05f17e5b3067ca"),
            new Pin(API + "AccessoriesContainer", "createReference", "(I)" + SLOT_REF, "3d8273db70025ad1593cd92b71a2dc17646feb5470e95e5d69df99fca00028f7"),
            new Pin(API + "slot.SlotReference", "of",
                    "(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/String;I)" + SLOT_REF, "15502b181b59d87421de90811b95981192a8c04784ba68371f73dc6702edb4cb"),
            new Pin(IMPL + "AccessoriesContainerImpl", "capability", "()Lio/wispforest/accessories/api/AccessoriesCapability;", "afd79d1ae5c8c900b3b3c9d9570e6b3a9de7254b5098fa7c1d83567206a6c85a"),
            new Pin(IMPL + "AccessoriesContainerImpl", "getSlotName", "()Ljava/lang/String;", "a8bb622fc58ffb503a2c91a5cf523c4edfb9d03631788bc3b20a0c246c543252"),
            new Pin(API + "attributes.AccessoryAttributeBuilder", "<init>", "()V", "3213561926109396b9e9f8b9cac4aac1d9c438c7841afa6295830b18a5aeed4b"),
            new Pin("io.wispforest.accessories.utils.AttributeUtils", "removeTransientAttributeModifiers",
                    "(Lnet/minecraft/world/entity/LivingEntity;Lio/wispforest/accessories/api/attributes/AccessoryAttributeBuilder;)V", "d3f8b265d1ada740f62e3ee69e8ca02601ef5868012aba36ab5ea8c0e313084d"),
            new Pin("io.wispforest.accessories.utils.AttributeUtils", "addTransientAttributeModifiers",
                    "(Lnet/minecraft/world/entity/LivingEntity;Lio/wispforest/accessories/api/attributes/AccessoryAttributeBuilder;)V", "f6e4348ef7f9bc98ff471b56b4a65321e29bf989cc4009f9ba4484d02efba8a5")
    );

    private AccessoriesFingerprints() {}

    /** Pins whose method is missing or whose fingerprint differs from the expected value. */
    public static List<String> mismatches(List<Pin> pins) {
        final List<String> out = new ArrayList<>();
        for (Pin pin : pins) {
            final String actual = ArcadiaMixinTargetInspector.methodFingerprint(pin.className(), pin.method(), pin.descriptor());
            if (actual == null) {
                out.add(pin.describe() + " (missing)");
            } else if (!actual.equals(pin.sha256())) {
                out.add(pin.describe() + " (changed)");
            }
        }
        return out;
    }
}
