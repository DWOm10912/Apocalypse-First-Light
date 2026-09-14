package com.antaurora.apofirstlight.worldgen.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;

/** Dependency-free WG-02 regression, invoked by the existing terrainContractTest task. */
public final class CoreContractTest {
    private static int checks;
    private static final net.minecraft.resources.ResourceKey<Level> OVERWORLD = dimension("overworld");
    private static final net.minecraft.resources.ResourceKey<Level> NETHER = dimension("the_nether");

    /**
     * Test-only access to the interned value factory: createRegistryKey/Level constants
     * trigger BuiltInRegistries in Forge 1.20.1. Avoid bootstrapping registries or a world.
     * The production contract uses only the public ResourceKey value type.
     */
    @SuppressWarnings("unchecked")
    private static net.minecraft.resources.ResourceKey<Level> dimension(String name) {
        try {
            var factory = net.minecraft.resources.ResourceKey.class.getDeclaredMethod(
                    "create", ResourceLocation.class, ResourceLocation.class);
            factory.setAccessible(true);
            return (net.minecraft.resources.ResourceKey<Level>) factory.invoke(null,
                    new ResourceLocation("minecraft", "dimension"), new ResourceLocation("minecraft", name));
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Update the Forge 1.20.1 test-only key fixture", error);
        }
    }
    public static void require(boolean value) {
        checks++;
        if (!value) throw new AssertionError("WG-02 core check " + checks);
    }
    public static void rejects(Class<? extends Throwable> type, Runnable action) {
        checks++;
        try { action.run(); }
        catch (Throwable error) {
            if (type.isInstance(error)) return;
            throw new AssertionError("Unexpected exception", error);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
    private static PlacementResult result(PlacementStatus status, long changed, long skipped,
                                          long failed, List<GenerationFailure> failures) {
        return new PlacementResult(status, "test-plan", new ChunkPos(-1, 2), changed, skipped, failed, failures);
    }
    public static void main(String[] args) {
        var system = new ResourceLocation("apocalypse_firstlight", "test");
        for (long seed : new long[]{Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE}) {
            var a = new WorldgenIdentity(seed, OVERWORLD, system, "v1", "snapshot");
            var b = new WorldgenIdentity(seed, OVERWORLD, system, "v1", "snapshot");
            require(a.equals(b) && a.hashCode() == b.hashCode());
            require(!a.equals(new WorldgenIdentity(seed, NETHER, system, "v1", "snapshot")));
            require(!a.equals(new WorldgenIdentity(seed, OVERWORLD, system, "v2", "snapshot")));
            require(!a.equals(new WorldgenIdentity(seed, OVERWORLD, system, "v1", "other")));
            require(!a.equals(new WorldgenIdentity(seed, OVERWORLD, new ResourceLocation("test", "other"), "v1", "snapshot")));
            require(!a.equals(new WorldgenIdentity(seed ^ 1, OVERWORLD, system, "v1", "snapshot")));
        }
        rejects(NullPointerException.class, () -> new WorldgenIdentity(0, null, system, "v", "s"));
        rejects(NullPointerException.class, () -> new WorldgenIdentity(0, OVERWORLD, null, "v", "s"));
        rejects(NullPointerException.class, () -> new WorldgenIdentity(0, OVERWORLD, system, null, "s"));
        rejects(NullPointerException.class, () -> new WorldgenIdentity(0, OVERWORLD, system, "v", null));
        for (String blank : List.of("", " ", "\t\n")) {
            rejects(IllegalArgumentException.class, () -> new WorldgenIdentity(0, OVERWORLD, system, blank, "s"));
            rejects(IllegalArgumentException.class, () -> new WorldgenIdentity(0, OVERWORLD, system, "v", blank));
            rejects(IllegalArgumentException.class, () -> new PlacementResult(PlacementStatus.COMPLETE, blank, new ChunkPos(0, 0), 0, 0, 0, List.of()));
        }
        var detail = new GenerationFailure(GenerationFailureReason.MISSING_RESOURCE, "");
        require(detail.message().isEmpty());
        rejects(NullPointerException.class, () -> new GenerationFailure(null, ""));
        rejects(NullPointerException.class, () -> new GenerationFailure(GenerationFailureReason.INTERNAL_ERROR, null));
        for (PlacementStatus status : PlacementStatus.values()) {
            rejects(IllegalArgumentException.class, () -> result(status, -1, 0, 0, List.of()));
            rejects(IllegalArgumentException.class, () -> result(status, 0, -1, 0, List.of()));
            rejects(IllegalArgumentException.class, () -> result(status, 0, 0, -1, List.of()));
        }
        rejects(NullPointerException.class, () -> result(null, 0, 0, 0, List.of()));
        rejects(NullPointerException.class, () -> result(PlacementStatus.COMPLETE, 0, 0, 0, null));
        rejects(NullPointerException.class, () -> result(PlacementStatus.COMPLETE, 0, 0, 0, Arrays.asList((GenerationFailure) null)));
        rejects(NullPointerException.class, () -> new PlacementResult(PlacementStatus.COMPLETE, null, new ChunkPos(0, 0), 0, 0, 0, List.of()));
        rejects(NullPointerException.class, () -> new PlacementResult(PlacementStatus.COMPLETE, "p", null, 0, 0, 0, List.of()));
        rejects(IllegalArgumentException.class, () -> result(PlacementStatus.REJECTED_BEFORE_WRITE, 1, 0, 0, List.of()));
        for (PlacementStatus status : List.of(PlacementStatus.COMPLETE, PlacementStatus.APPLIED_SLICE)) {
            rejects(IllegalArgumentException.class, () -> result(status, 1, 0, 1, List.of()));
            require(result(status, 0, Long.MAX_VALUE, 0, List.of(detail)).status() == status);
            require(result(status, Long.MAX_VALUE, Long.MAX_VALUE, 0, List.of()).changed() == Long.MAX_VALUE);
        }
        rejects(IllegalArgumentException.class, () -> result(PlacementStatus.PARTIAL_COMMIT, 0, 0, 1, List.of(detail)));
        rejects(IllegalArgumentException.class, () -> result(PlacementStatus.PARTIAL_COMMIT, 1, 0, 0, List.of()));
        require(result(PlacementStatus.PARTIAL_COMMIT, 1, 0, 0, List.of(detail)).failed() == 0);
        require(result(PlacementStatus.PARTIAL_COMMIT, 1, 0, 1, List.of()).failed() == 1);
        require(result(PlacementStatus.REJECTED_BEFORE_WRITE, 0, 0, 1, List.of(detail)).changed() == 0);
        require(PlacementStatus.APPLIED_SLICE != PlacementStatus.COMPLETE);
        var input = new ArrayList<>(List.of(detail));
        var snapshot = result(PlacementStatus.COMPLETE, 0, 0, 0, input);
        input.clear();
        require(snapshot.failures().equals(List.of(detail)));
        rejects(UnsupportedOperationException.class, () -> snapshot.failures().clear());
        require(input.isEmpty());
        for (int n : new int[]{0, 1, Integer.MAX_VALUE}) require(new QueryBudget(n).maxOperations() == n);
        rejects(IllegalArgumentException.class, () -> new QueryBudget(-1));
        require(Arrays.toString(GenerationFailureReason.values()).equals("[INVALID_TERRAIN, UNKNOWN_TERRAIN, COLLISION, OUT_OF_BOUNDS, PROTECTED_CONTENT, MISSING_RESOURCE, VERSION_MISMATCH, BUDGET_EXCEEDED, INTERNAL_ERROR]"));
        require(Arrays.toString(PlacementStatus.values()).equals("[REJECTED_BEFORE_WRITE, APPLIED_SLICE, COMPLETE, PARTIAL_COMMIT]"));
        require(Arrays.toString(WriteOutcome.values()).equals("[CHANGED, ALREADY_MATCHED, NOT_OWNED, PROTECTED, UNAVAILABLE, FAILED]"));
        require(WriteOutcome.NOT_OWNED != WriteOutcome.FAILED);
        System.out.println("PASS WG-02 core checks=" + checks);
    }
}
