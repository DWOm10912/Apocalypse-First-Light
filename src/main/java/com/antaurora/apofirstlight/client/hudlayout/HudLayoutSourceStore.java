package com.antaurora.apofirstlight.client.hudlayout;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/** Source-only, fail-closed IO. Never writes a config copy, JAR or another layout. */
public final class HudLayoutSourceStore {
    private static final Path SOURCE = Path.of("src/main/resources/assets/apocalypse_firstlight/gui/layout");
    private final Path root;
    public record Loaded(JsonObject json, byte[] bytes) {}

    public HudLayoutSourceStore(boolean development, Path workingDirectory, Path gameDirectory) throws IOException {
        if (!development) throw new IOException("Development environment required");
        Path found = findRoot(workingDirectory);
        if (found == null) found = findRoot(gameDirectory);
        if (found == null) throw new IOException("Writable HUD source root not found");
        root = found;
    }

    private static Path findRoot(Path start) throws IOException {
        if (start == null) return null;
        Path candidate = start.toAbsolutePath().normalize();
        // Normal IDE/Gradle cwd is the project or its run directory. No drive-wide search.
        for (int i = 0; i < 5 && candidate != null; i++, candidate = candidate.getParent()) {
            if (!Files.isRegularFile(candidate.resolve("build.gradle"))
                    || !Files.isRegularFile(candidate.resolve("settings.gradle"))
                    || !Files.isRegularFile(candidate.resolve("gradlew"))) continue;
            Path real = candidate.toRealPath();
            Path source = real.resolve(SOURCE);
            if (Files.isDirectory(source) && source.toRealPath().equals(source) && Files.isWritable(source)) return real;
        }
        return null;
    }

    public Path path(HudLayoutDescriptor layout) throws IOException {
        return path(layout.fileName());
    }
    public Path path(String fileName) throws IOException {
        if (!fileName.matches("[a-z0-9_]+\\.json")) throw new IOException("Invalid layout filename");
        Path source = root.resolve(SOURCE);
        Path target = source.resolve(fileName);
        if (!Files.isDirectory(source) || !source.toRealPath().equals(source)
                || !Files.isRegularFile(target) || !target.toRealPath().equals(target)
                || !Files.isWritable(source) || !Files.isWritable(target))
            throw new IOException("HUD source is missing, linked, or not writable: " + target);
        return target;
    }

    public Loaded read(HudLayoutDescriptor layout) throws IOException {
        byte[] bytes = Files.readAllBytes(path(layout));
        try {
            return new Loaded(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject(), bytes);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid HUD JSON: " + layout.fileName(), exception);
        }
    }

    public byte[] save(HudLayoutDescriptor layout, JsonObject json, byte[] expected) throws IOException {
        Path target = path(layout);
        if (!Arrays.equals(Files.readAllBytes(target), expected))
            throw new IOException("Source changed outside editor; reload before saving");
        byte[] bytes = (new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json) + "\n")
                .getBytes(StandardCharsets.UTF_8);
        Path temporary = Files.createTempFile(target.getParent(), "." + layout.id() + "-", ".tmp");
        try {
            Files.write(temporary, bytes);
            // Recheck the source path and external changes just before replacement.
            if (!path(layout).equals(target) || !Arrays.equals(Files.readAllBytes(target), expected))
                throw new IOException("Source changed outside editor; reload before saving");
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return bytes;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
