package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.world.biome.StartupPlainsEnclave;
import com.antaurora.apofirstlight.world.biome.StartupSettlementProtection;
import com.antaurora.apofirstlight.world.bunker.BunkerSavedData;
import com.antaurora.apofirstlight.worldgen.highway.HighwaySpatialClaimProvider;
import com.antaurora.apofirstlight.worldgen.roads.RoadPlan;
import com.antaurora.apofirstlight.worldgen.roads.RoadPlanJson;
import com.antaurora.apofirstlight.worldgen.roads.RoadPlanner;
import com.antaurora.apofirstlight.worldgen.roads.RoadTerrainQuery;
import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import com.antaurora.apofirstlight.worldgen.spatial.ClaimPriorityPolicy;
import com.antaurora.apofirstlight.worldgen.spatial.DeterministicClaimId;
import com.antaurora.apofirstlight.worldgen.spatial.SpatialClaim;
import com.antaurora.apofirstlight.worldgen.spatial.SpatialClaimStrength;
import com.antaurora.apofirstlight.worldgen.spatial.SpatialClaimType;
import com.antaurora.apofirstlight.worldgen.terrain.FluidCategory;
import com.antaurora.apofirstlight.worldgen.terrain.ProtectionKnowledge;
import com.antaurora.apofirstlight.worldgen.terrain.SurfaceType;
import com.antaurora.apofirstlight.worldgen.terrain.TerrainQuery;
import com.antaurora.apofirstlight.worldgen.terrain.TerrainSample;
import com.antaurora.apofirstlight.worldgen.terrain.TerrainSource;
import com.antaurora.apofirstlight.worldgen.terrain.TerrainValidity;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.WeakHashMap;

/** Development-only plan diagnostics. No block writes, chunk loads or worldgen registrations. */
public final class RoadPlanningCommand {
    private static final long COOLDOWN_NANOS = 5_000_000_000L;
    private static final int MAX_CENTER_COORDINATE = 29_999_000;
    private static final Map<MinecraftServer, Long> LAST_RUN = new WeakHashMap<>();
    private static final String PROTECTION_VERSION = "roads_v1a_known_protection_1";
    private static final ResourceLocation BUNKER = new ResourceLocation("apocalypse_firstlight", "bunker");

    private RoadPlanningCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        var root = Commands.literal("roads").requires(source -> source.hasPermission(2));
        root.then(mode("plan", false));
        root.then(mode("synthetic", true));
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mode(String name, boolean synthetic) {
        var mode = Commands.literal(name);
        for (RoadPlan.Layout layout : RoadPlan.Layout.values()) {
            // T is a diagnostic topology fixture, not a real-terrain settlement layout.
            if (!synthetic && layout == RoadPlan.Layout.T) continue;
            mode.then(Commands.literal(layout.name().toLowerCase(Locale.ROOT))
                    .executes(context -> execute(context.getSource(), layout,
                            (int) Math.floor(context.getSource().getPosition().x),
                            (int) Math.floor(context.getSource().getPosition().z), synthetic))
                    .then(Commands.argument("x", IntegerArgumentType.integer(-MAX_CENTER_COORDINATE, MAX_CENTER_COORDINATE))
                            .then(Commands.argument("z", IntegerArgumentType.integer(-MAX_CENTER_COORDINATE, MAX_CENTER_COORDINATE))
                                    .executes(context -> execute(context.getSource(), layout,
                                            IntegerArgumentType.getInteger(context, "x"),
                                            IntegerArgumentType.getInteger(context, "z"), synthetic)))));
        }
        return mode;
    }

    private static int execute(CommandSourceStack source, RoadPlan.Layout layout, int x, int z,
                               boolean synthetic) {
        ServerLevel level = source.getLevel();
        if (!Level.OVERWORLD.equals(level.dimension())) {
            source.sendFailure(Component.literal("Road V1-A diagnostics currently require the Overworld."));
            return 0;
        }
        long now = System.nanoTime();
        Long previous = LAST_RUN.get(source.getServer());
        if (previous != null && now - previous < COOLDOWN_NANOS) {
            source.sendFailure(Component.literal("Road planning diagnostics: wait 5 seconds between invocations."));
            return 0;
        }
        LAST_RUN.put(source.getServer(), now);
        try {
            BoundsXZ area = RoadPlanner.candidateBounds(x, z);
            List<SpatialClaim> highway = HighwaySpatialClaimProvider.query(level.getSeed(), level.dimension(), area);
            List<SpatialClaim> blockers = new ArrayList<>(highway);
            List<String> protectionNotes = addKnownProtection(level, area, blockers);
            blockers.sort(Comparator.comparing(SpatialClaim::id));
            TerrainSource terrainSource = synthetic ? TerrainSource.STRUCTURE_PLANNING
                    : TerrainSource.NOISE_PRE_DECORATION;
            RoadTerrainQuery realTerrain = synthetic ? null : new RoadTerrainQuery(level, RoadTerrainQuery.MAX_COLUMNS);
            TerrainQuery terrain = synthetic ? RoadPlanningCommand::syntheticFlatSample : realTerrain;
            String candidateId = (synthetic ? "diagnostic_synthetic:" : "diagnostic_noise:")
                    + layout.name().toLowerCase(Locale.ROOT) + ":" + x + ":" + z;
            RoadPlan plan = RoadPlanner.plan(level.getSeed(), candidateId, x, z, layout,
                    terrain, terrainSource, List.copyOf(blockers));
            JsonObject json = JsonParser.parseString(RoadPlanJson.write(plan)).getAsJsonObject();
            JsonObject context = new JsonObject();
            context.addProperty("diagnostic_only", true);
            context.addProperty("terrain_source", terrainSource.name());
            context.addProperty("synthetic_flat_terrain", synthetic);
            context.addProperty("synthetic_G", synthetic ? 64 : null);
            context.addProperty("seed", level.getSeed());
            context.addProperty("highway_claim_count", highway.size());
            context.addProperty("known_blocker_count", blockers.size());
            context.addProperty("current_world_protection", "UNKNOWN: player/mod structures are not scanned");
            context.addProperty("construction_authorized", false);
            if (realTerrain != null) {
                context.addProperty("noise_columns", realTerrain.sampledColumns());
                context.addProperty("invalid_columns", realTerrain.failedColumns());
                context.addProperty("budget_refusals", realTerrain.budgetRefusals());
                context.addProperty("last_adapter_failure", realTerrain.lastFailure());
            }
            JsonArray notes = new JsonArray();
            protectionNotes.forEach(notes::add);
            context.add("protection_notes", notes);
            json.add("diagnostic_context", context);
            // Four real layouts, four synthetic layouts and one synthetic T fixture: nine fixed files.
            Path directory = FMLPaths.GAMEDIR.get().resolve("afl_debug").resolve("roads");
            Files.createDirectories(directory);
            Path output = directory.resolve((synthetic ? "synthetic_" : "plan_")
                    + layout.name().toLowerCase(Locale.ROOT) + ".json");
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json),
                    StandardCharsets.UTF_8);
            source.sendSuccess(() -> Component.literal("Road V1-A " + plan.status() + " | id=" + plan.planId()
                    + " | spec=" + plan.specVersion() + " | nodes=" + plan.nodes().size()
                    + " edges=" + plan.edges().size() + " lots=" + plan.lots().size()
                    + " connected=" + plan.connected() + " | highway claims=" + highway.size()), false);
            source.sendSuccess(() -> Component.literal("Source=" + terrainSource
                    + (synthetic ? " (SYNTHETIC FLAT DATA; no world terrain validation)" : " (pre-decoration only)")
                    + "; current-world protection UNKNOWN; no blocks changed. JSON: " + output.toAbsolutePath()), false);
            if (!plan.diagnostics().isEmpty()) {
                source.sendSuccess(() -> Component.literal("Diagnostics: "
                        + String.join("; ", plan.diagnostics().stream().limit(8).toList())), false);
            }
            return plan.successful() ? 1 : 0;
        } catch (IOException | RuntimeException exception) {
            String detail = exception.getCause() == null ? exception.getMessage()
                    : exception.getMessage() + " (" + exception.getCause().getMessage() + ")";
            source.sendFailure(Component.literal("Road planning failed: " + detail));
            return 0;
        }
    }

    private static TerrainSample syntheticFlatSample(int x, int z, TerrainSource source) {
        if (source != TerrainSource.STRUCTURE_PLANNING) return TerrainSample.unknown(source);
        return new TerrainSample(TerrainValidity.VALID, source, OptionalInt.of(64), OptionalInt.of(64),
                OptionalInt.of(64), OptionalInt.empty(), FluidCategory.NONE, Optional.empty(),
                SurfaceType.SOLID, ProtectionKnowledge.UNKNOWN);
    }

    private static List<String> addKnownProtection(ServerLevel level, BoundsXZ area, List<SpatialClaim> claims) {
        List<String> notes = new ArrayList<>();
        // Exact protection is irregular. This bound contains all points the existing policy protects;
        // deliberately reserve the extra corners rather than misreport a partially sampled area clear.
        int radius = StartupPlainsEnclave.PLAINS_BASE_RADIUS + StartupPlainsEnclave.PLAINS_NOISE_AMPLITUDE
                + StartupSettlementProtection.STARTUP_SETTLEMENT_FALLOUT_PROTECTION_DEPTH;
        BoundsXZ startup = new BoundsXZ(StartupPlainsEnclave.CENTER_X - radius,
                StartupPlainsEnclave.CENTER_Z - radius, StartupPlainsEnclave.CENTER_X + radius + 1,
                StartupPlainsEnclave.CENTER_Z + radius + 1);
        addProtection(level, area, claims, new ResourceLocation("apocalypse_firstlight", "startup_protection"),
                "startup_envelope", startup);
        notes.add("Startup policy reserved conservatively by a +/-" + radius + "-block square; not an ecology change.");
        // get (not computeIfAbsent) may read existing SavedData but cannot create/mark it dirty.
        BunkerSavedData bunker = level.getDataStorage().get(BunkerSavedData::load, BunkerSavedData.ID);
        if (bunker == null || !bunker.isGenerated()) {
            notes.add("No generated bunker SavedData observed; startup envelope remains reserved.");
        } else {
            var template = level.getServer().getStructureManager().get(BUNKER);
            if (template.isEmpty()) throw new IllegalArgumentException("Generated bunker template unavailable; protected bounds UNKNOWN.");
            Rotation rotation;
            try { rotation = Rotation.valueOf(bunker.getRotation()); }
            catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Generated bunker rotation unavailable; protected bounds UNKNOWN.");
            }
            var box = template.get().getBoundingBox(new StructurePlaceSettings().setMirror(Mirror.NONE)
                    .setRotation(rotation), bunker.getOrigin());
            BoundsXZ bunkerBounds = new BoundsXZ(box.minX(), box.minZ(), box.maxX() + 1, box.maxZ() + 1).expand(8);
            addProtection(level, area, claims, BUNKER, "placed_bunker:" + bunker.getOrigin().toShortString(), bunkerBounds);
            notes.add("Generated bunker rotated template bounds reserved with 8-block construction margin.");
        }
        notes.add("No generic loaded-world, player-build, mod-protection or other settlement claim scan performed.");
        return List.copyOf(notes);
    }

    private static void addProtection(ServerLevel level, BoundsXZ area, List<SpatialClaim> claims,
                                      ResourceLocation owner, String identity, BoundsXZ bounds) {
        if (!bounds.intersects(area)) return;
        claims.add(new SpatialClaim(DeterministicClaimId.create(owner, level.dimension(), PROTECTION_VERSION, identity),
                owner, level.dimension(), PROTECTION_VERSION, bounds, Optional.empty(), SpatialClaimType.PROTECTED_SITE,
                SpatialClaimStrength.HARD, ClaimPriorityPolicy.defaultPriorityFor(SpatialClaimType.PROTECTED_SITE,
                SpatialClaimStrength.HARD), 0, List.of()));
    }
}
