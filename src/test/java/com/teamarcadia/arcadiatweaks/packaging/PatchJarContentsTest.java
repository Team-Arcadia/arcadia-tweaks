package com.teamarcadia.arcadiatweaks.packaging;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PatchJarContentsTest {

    private static final String MOD_ID = System.getProperty("arcadia.modId", "arcadiatweaks");
    private static final String MOD_VERSION = System.getProperty("arcadia.modVersion", "0.2.0");
    private static final Path PATCH_JAR_DIR = Path.of(System.getProperty("arcadia.patchJarDir", "build/libs"));

    @Test
    void allPatchJarContainsEveryPatchMixinConfig() throws IOException {
        try (JarFile jar = openVariant("all")) {
            assertEntryPresent(jar, "arcadiatweaks.mixins.json");
            assertEntryPresent(jar, "arcadiatweaks.botany.mixins.json");
            assertEntryPresent(jar, "arcadiatweaks.refinedstorage.mixins.json");
            assertEntryPresent(jar, "arcadiatweaks.mekanism.mixins.json");
            assertEntryPresent(jar, "arcadiatweaks.waystones.mixins.json");
            assertEntryPresent(jar, "arcadiatweaks.simplyswords.mixins.json");
            assertEntryPresent(jar, "arcadiatweaks.accessories.mixins.json");
            assertEntryPresent(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/accessories/AccessoriesMixinPlugin.class");
            assertEntryPresent(jar, "com/teamarcadia/arcadiatweaks/neoforge/accessories/ArcadiaAccessoriesContains.class");
            assertEntryPresent(jar, "com/teamarcadia/arcadiatweaks/neoforge/accessories/ArcadiaAccessoriesTick.class");
            assertEntryPresent(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/simplyswords/RunicForgeScreenHandlerMixin.class");
            assertEntryPresent(jar, "com/teamarcadia/arcadiatweaks/common/modules/simplyswords/RunicForgeLock.class");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/gametest/");

            final String modsToml = readEntry(jar, "META-INF/neoforge.mods.toml");
            assertTrue(modsToml.contains("config = \"arcadiatweaks.botany.mixins.json\""));
            assertTrue(modsToml.contains("config = \"arcadiatweaks.refinedstorage.mixins.json\""));
            assertTrue(modsToml.contains("config = \"arcadiatweaks.mekanism.mixins.json\""));
            assertTrue(modsToml.contains("config = \"arcadiatweaks.waystones.mixins.json\""));
            assertTrue(modsToml.contains("config = \"arcadiatweaks.simplyswords.mixins.json\""));
            assertTrue(modsToml.contains("modId = \"simplyswords\""));
            assertTrue(modsToml.contains("config = \"arcadiatweaks.accessories.mixins.json\""));
            assertTrue(modsToml.contains("modId = \"accessories\""));
        }
    }

    @Test
    void singlePatchJarsOnlyExposeTheirPatchMixinConfig() throws IOException {
        assertVariantOnlyContainsPatchConfigs("botany", Set.of("arcadiatweaks.botany.mixins.json"));
        assertVariantOnlyContainsPatchConfigs("refinedstorage", Set.of("arcadiatweaks.refinedstorage.mixins.json"));
        assertVariantOnlyContainsPatchConfigs("mekanism", Set.of("arcadiatweaks.mekanism.mixins.json"));
        assertVariantOnlyContainsPatchConfigs("waystones", Set.of("arcadiatweaks.waystones.mixins.json"));
    }

    @Test
    void singlePatchJarsExcludeOtherPatchImplementationPackages() throws IOException {
        try (JarFile jar = openVariant("botany")) {
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/gametest/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/simplyswords/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/common/modules/simplyswords/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/accessories/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/accessories/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/mekanism/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/refinedstorage/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/waystones/");
            assertEntryPresent(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/ArcadiaMixinTargetInspector.class");
        }
        try (JarFile jar = openVariant("refinedstorage")) {
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/gametest/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/simplyswords/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/common/modules/simplyswords/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/accessories/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/accessories/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/botany/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/mekanism/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/waystones/");
            assertEntryPresent(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/ArcadiaMixinTargetInspector.class");
        }
        try (JarFile jar = openVariant("mekanism")) {
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/gametest/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/simplyswords/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/common/modules/simplyswords/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/accessories/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/accessories/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/botany/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/refinedstorage/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/waystones/");
            assertEntryPresent(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/mekanism/TransmitterBakedModelMixin.class");
            assertEntryPresent(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/ArcadiaMixinTargetInspector.class");
        }
        try (JarFile jar = openVariant("waystones")) {
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/gametest/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/simplyswords/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/common/modules/simplyswords/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/accessories/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/accessories/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/botany/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/refinedstorage/");
            assertNoEntryStartsWith(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/mekanism/");
            assertEntryPresent(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/waystones/WaystoneIndexManagerMixin.class");
            assertEntryPresent(jar, "com/teamarcadia/arcadiatweaks/neoforge/mixin/ArcadiaMixinTargetInspector.class");
        }
    }

    private static void assertVariantOnlyContainsPatchConfigs(String classifier, Set<String> expectedPatchConfigs) throws IOException {
        try (JarFile jar = openVariant(classifier)) {
            assertEntryPresent(jar, "arcadiatweaks.mixins.json");
            for (String config : Set.of(
                    "arcadiatweaks.botany.mixins.json",
                    "arcadiatweaks.refinedstorage.mixins.json",
                    "arcadiatweaks.mekanism.mixins.json",
                    "arcadiatweaks.waystones.mixins.json",
                    "arcadiatweaks.simplyswords.mixins.json",
                    "arcadiatweaks.accessories.mixins.json"
            )) {
                if (expectedPatchConfigs.contains(config)) {
                    assertEntryPresent(jar, config);
                } else {
                    assertEntryMissing(jar, config);
                }
            }
        }
    }

    private static JarFile openVariant(String classifier) throws IOException {
        final Path jarPath = PATCH_JAR_DIR.resolve(MOD_ID + "-" + MOD_VERSION + "-" + classifier + ".jar");
        return new JarFile(jarPath.toFile());
    }

    private static void assertEntryPresent(JarFile jar, String entryName) {
        assertNotNull(jar.getEntry(entryName), () -> jar.getName() + " should contain " + entryName);
    }

    private static void assertEntryMissing(JarFile jar, String entryName) {
        assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals(entryName)),
                () -> jar.getName() + " should not contain " + entryName);
    }

    private static void assertNoEntryStartsWith(JarFile jar, String prefix) {
        assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith(prefix)),
                () -> jar.getName() + " should not contain entries under " + prefix);
    }

    private static String readEntry(JarFile jar, String entryName) throws IOException {
        final var entry = jar.getEntry(entryName);
        assertNotNull(entry, () -> jar.getName() + " should contain " + entryName);
        try (var input = jar.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
