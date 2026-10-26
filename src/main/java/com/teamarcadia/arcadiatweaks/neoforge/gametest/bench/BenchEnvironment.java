package com.teamarcadia.arcadiatweaks.neoforge.gametest.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.language.IModInfo;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Comparator;

/**
 * Describes the machine and the mod set a bench ran on.
 *
 * @author vyrriox
 */
final class BenchEnvironment {

    private BenchEnvironment() {}

    static JsonObject describe() {
        final JsonObject env = new JsonObject();
        env.addProperty("javaVersion", System.getProperty("java.version"));
        env.addProperty("javaVm", System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"));
        env.addProperty("javaVendor", System.getProperty("java.vendor"));
        env.addProperty("jvmArgs", String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(a -> a.startsWith("-Xm") || a.startsWith("-XX:")).toList()));
        env.addProperty("maxHeapMb", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        final JsonArray gcs = new JsonArray();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcs.add(gc.getName());
        }
        env.add("garbageCollectors", gcs);
        env.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
        env.addProperty("cpu", cpuName());
        env.addProperty("logicalCores", Runtime.getRuntime().availableProcessors());
        env.addProperty("minecraft", SharedConstants.getCurrentVersion().getName());
        env.addProperty("neoforge", FMLLoader.versionInfo().neoForgeVersion());
        final JsonObject mods = new JsonObject();
        ModList.get().getMods().stream()
                .sorted(Comparator.comparing(IModInfo::getModId))
                .forEach(m -> mods.addProperty(m.getModId(), m.getVersion().toString()));
        env.add("mods", mods);
        return env;
    }

    private static String cpuName() {
        try {
            return new oshi.SystemInfo().getHardware().getProcessor().getProcessorIdentifier().getName().trim();
        } catch (Throwable t) {
            final String id = System.getenv("PROCESSOR_IDENTIFIER");
            return id != null ? id : "unknown";
        }
    }
}
