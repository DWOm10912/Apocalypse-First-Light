package com.antaurora.apofirstlight.dev.worldgen;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * C2 world generation test harness (dev only, never in the release jar; docs/worldgen/federal_six_state_c2.md, tests).
 * Driven by one JVM property, {@code -Dafl.c2="mode=create;world=NAME;preset=ID;seed=N;tag=T;minutes=M"}
 * (src/dev/federal-c2-client.init.gradle): a development client in its own run directory under build/ creates or
 * opens a test world by itself (C2HarnessClient), the integrated server runs C2Probe once the player is in, writes a
 * report under {@code <game dir>/afl_c2_probe/}, saves and quits. A watchdog ends the process if anything hangs.
 * No user save, no user run directory, no camera: the client only shows the spawn while the probe works.
 */
public final class C2Harness {
    private C2Harness() {}

    static final Map<String, String> ARGS = parse(System.getProperty("afl.c2", ""));

    public static boolean active() { return !ARGS.isEmpty(); }

    static String arg(String key, String def) { return ARGS.getOrDefault(key, def); }

    private static Map<String, String> parse(String s) {
        Map<String, String> out = new LinkedHashMap<>();
        if (s == null || s.isBlank()) return out;
        for (String part : s.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) out.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
        }
        return out;
    }

    static {
        if (active()) {
            int minutes = Integer.parseInt(arg("minutes", "20"));
            Thread watchdog = new Thread(() -> {
                try {
                    Thread.sleep(minutes * 60_000L);
                } catch (InterruptedException e) {
                    return;
                }
                log("TIMEOUT after " + minutes + " min: halting the test client");
                Runtime.getRuntime().halt(3);
            }, "AFL C2 harness watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
            log("harness active: " + ARGS);
        }
    }

    static void log(String msg) {
        ApocalypseFirstLight.LOGGER.info("[AFL C2 HARNESS] {}", msg);
    }

    static Path reportDir() throws IOException {
        Path dir = FMLPaths.GAMEDIR.get().resolve("afl_c2_probe");
        Files.createDirectories(dir);
        return dir;
    }

    static void writeReport(String name, String text) {
        try {
            Path f = reportDir().resolve(name);
            Files.writeString(f, text, StandardCharsets.UTF_8);
            log("report written: " + f);
        } catch (IOException e) {
            log("could not write report " + name + ": " + e);
        }
    }
}
