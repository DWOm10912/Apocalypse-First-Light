package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.highway.HighwayRouteGraph;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample.LandmassRole;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample.NationId;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample.SurfaceClass;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample.WaterClass;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Development-only analytic map export. No chunk, biome, or heightmap requests. */
public final class MacroGeographyExportCommand {
    private static final int DEFAULT_RADIUS = 12000;
    private static final int DEFAULT_STEP = 32;
    private static final int MAX_PIXELS = 1_000_000;
    private static final int MIN_BRIDGE_GAP = 300;
    private static final int MAX_BRIDGE_GAP = 600;

    private MacroGeographyExportCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("macro").requires(source -> source.hasPermission(2))
                .then(Commands.literal("export")
                        .executes(ctx -> export(ctx.getSource(), DEFAULT_RADIUS, DEFAULT_STEP))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(9000, 16000))
                                .executes(ctx -> export(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radius"), DEFAULT_STEP))
                                .then(Commands.argument("step", IntegerArgumentType.integer(16, 128))
                                        .executes(ctx -> export(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "radius"),
                                                IntegerArgumentType.getInteger(ctx, "step"))))));
    }

    private static int export(CommandSourceStack source, int radius, int step) {
        var level = source.getLevel();
        if (!Level.OVERWORLD.equals(level.dimension())
                || !((RandomStateSeedAccess) (Object) level.getChunkSource().randomState()).apocalypse$hasMacroGeography()) {
            source.sendFailure(Component.literal("Macro export requires the AFL normal Overworld noise settings."));
            return 0;
        }
        int side = 2 * radius / step + 1;
        if ((long) side * side > MAX_PIXELS) {
            source.sendFailure(Component.literal("Macro export exceeds the 1,000,000-sample limit."));
            return 0;
        }
        try {
            MacroGeography geography = MacroGeography.forSeed(level.getSeed());
            HighwayRouteGraph highway = HighwayRouteGraph.forSeed(level.getSeed());
            if (radius < geography.outerOceanRadius() + step * 2) {
                source.sendFailure(Component.literal("Radius must be at least "
                        + (geography.outerOceanRadius() + step * 2) + " to include this island group and ocean margin."));
                return 0;
            }
            Path directory = FMLPaths.GAMEDIR.get().resolve("afl_debug").resolve("macro");
            Files.createDirectories(directory);
            Path png = directory.resolve("macro_geography_" + level.getSeed() + ".png");
            Path txt = directory.resolve("macro_geography_" + level.getSeed() + ".txt");
            Survey survey = sample(geography, radius, step, side);
            HighwayNetworkExport.overlay(survey.image, highway, geography, radius, step);
            if (!ImageIO.write(survey.image, "png", png.toFile())) throw new IOException("PNG writer unavailable");
            Files.writeString(txt, report(geography, survey, radius, step)
                    + HighwayNetworkExport.report(highway, geography), StandardCharsets.UTF_8);
            source.sendSuccess(() -> Component.literal("Macro map exported: " + png.toAbsolutePath()
                    + " and " + txt.toAbsolutePath()), false);
            return 1;
        } catch (IOException exception) {
            source.sendFailure(Component.literal("Macro export failed: " + exception.getMessage()));
            return 0;
        }
    }

    private static Survey sample(MacroGeography geography, int radius, int step, int side) {
        BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
        Map<Integer, Extent> land = new LinkedHashMap<>();
        Map<Integer, Extent> water = new LinkedHashMap<>();
        EnumMap<WaterClass, Long> waterCounts = new EnumMap<>(WaterClass.class);
        boolean foreign = false, nonMainNation = false;
        long outerSamples = 0, outerOpenOcean = 0;
        int outerRadius = geography.outerOceanRadius();
        double nearestOcean = Double.POSITIVE_INFINITY, nearestInlandSea = Double.POSITIVE_INFINITY;
        for (int row = 0; row < side; row++) {
            int z = -radius + row * step;
            for (int col = 0; col < side; col++) {
                int x = -radius + col * step;
                MacroGeographySample value = geography.sample(x, z);
                image.setRGB(col, row, color(value).getRGB());
                if (value.isLand()) {
                    land.computeIfAbsent(value.landmassId(), ignored -> new Extent()).add(x, z, step);
                    if (value.nationId() != NationId.MAIN_NATION) nonMainNation = true;
                    if (value.nationId() != NationId.MAIN_NATION && value.nationId() != NationId.NONE) foreign = true;
                } else {
                    waterCounts.merge(value.waterClass(), 1L, Long::sum);
                    if (value.waterbodyId() > 0)
                        water.computeIfAbsent(value.waterbodyId(), ignored -> new Extent()).add(x, z, step);
                    double distance = Math.hypot(x, z);
                    if (value.waterClass() == WaterClass.OPEN_OCEAN) nearestOcean = Math.min(nearestOcean, distance);
                    if (value.waterClass() == WaterClass.INLAND_SEA) nearestInlandSea = Math.min(nearestInlandSea, distance);
                }
                if (Math.hypot(x, z) >= outerRadius) {
                    outerSamples++;
                    if (value.waterClass() == WaterClass.OPEN_OCEAN) outerOpenOcean++;
                }
            }
        }
        annotate(image, geography, radius, step);
        return new Survey(image, land, water, waterCounts, foreign, nonMainNation,
                nearestOcean, nearestInlandSea, outerSamples, outerOpenOcean);
    }

    private static Color color(MacroGeographySample sample) {
        if (sample.isLand()) {
            if (sample.surfaceClass() == SurfaceClass.COAST) return new Color(226, 206, 145);
            return switch (sample.landmassRole()) {
                case MAINLAND -> new Color(91, 139, 81);
                case SATELLITE_ISLAND -> new Color(210, 177, 88);
                case MINOR_ISLAND -> new Color(186, 169, 119);
                case NONE -> Color.MAGENTA;
            };
        }
        return switch (sample.waterClass()) {
            case INLAND_SEA -> new Color(72, 180, 199);
            case BAY -> new Color(66, 144, 200);
            case STRAIT -> new Color(128, 183, 229);
            case COASTAL_WATER -> new Color(55, 111, 180);
            case OPEN_OCEAN -> new Color(28, 57, 112);
            case NONE -> Color.MAGENTA;
        };
    }

    private static void annotate(BufferedImage image, MacroGeography geography, int radius, int step) {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            int center = radius / step;
            graphics.setStroke(new BasicStroke(1.3f));
            graphics.setColor(new Color(255, 255, 255, 190));
            ring(graphics, center, center, MacroGeography.MAINLAND_CORE_RADIUS / step);
            graphics.setColor(new Color(255, 210, 110));
            ring(graphics, center, center, Math.max(2, MacroGeography.STARTUP_MAINLAND_RESERVE / step));
            graphics.setColor(Color.WHITE);
            graphics.drawLine(center - 8, center, center + 8, center);
            graphics.drawLine(center, center - 8, center, center + 8);
            graphics.drawString("SPAWN (0,0)", center + 10, center - 9);
            graphics.drawString("MAINLAND CORE", center + 8, center - MacroGeography.MAINLAND_CORE_RADIUS / step - 4);
            graphics.drawString("STARTUP RESERVE", center + 12, center + 19);
            for (var island : geography.islands()) {
                int x = pixel(island.x(), radius, step), z = pixel(island.z(), radius, step);
                graphics.setColor(Color.WHITE);
                graphics.fillOval(x - 3, z - 3, 6, 6);
                graphics.drawString("ID " + island.id() + " " + island.role(), x + 6, z - 5);
            }
            graphics.setColor(new Color(255, 242, 124));
            graphics.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10, new float[]{5, 4}, 0));
            for (var crossing : geography.crossingCandidates()) {
                if (bridgeEligible(geography, crossing)) graphics.drawLine(
                        pixel(crossing.fromX(), radius, step), pixel(crossing.fromZ(), radius, step),
                        pixel(crossing.toX(), radius, step), pixel(crossing.toZ(), radius, step));
            }
            graphics.setStroke(new BasicStroke());
            int y = 18;
            String[] legend = {"MAINLAND", "SATELLITE_ISLAND",
                    "MINOR_ISLAND", "COAST", "INLAND_SEA", "BAY", "STRAIT", "COASTAL_WATER", "OPEN_OCEAN",
                    "dashed yellow: 300-600 bridge candidate"};
            Color[] colors = {new Color(91, 139, 81), new Color(210, 177, 88),
                    new Color(186, 169, 119), new Color(226, 206, 145),
                    new Color(72, 180, 199), new Color(66, 144, 200), new Color(128, 183, 229),
                    new Color(55, 111, 180), new Color(28, 57, 112), new Color(255, 242, 124)};
            graphics.setColor(new Color(0, 0, 0, 220));
            graphics.fillRect(5, 4, 300, legend.length * 16 + 10);
            for (int i = 0; i < legend.length; i++, y += 16) {
                graphics.setColor(colors[i]);
                graphics.fillRect(12, y - 9, 12, 10);
                graphics.setColor(Color.WHITE);
                graphics.drawString(legend[i], 30, y);
            }
        } finally {
            graphics.dispose();
        }
    }

    private static void ring(Graphics2D graphics, int x, int z, int radius) {
        graphics.drawOval(x - radius, z - radius, radius * 2, radius * 2);
    }
    private static int pixel(double coordinate, int radius, int step) {
        return (int) Math.round((coordinate + radius) / step);
    }

    private static boolean bridgeEligible(MacroGeography geography, MacroGeography.CrossingCandidate crossing) {
        double gap = sampledCrossingGap(geography, crossing);
        return gap >= MIN_BRIDGE_GAP && gap <= MAX_BRIDGE_GAP;
    }

    /** Verify a continuous MAINLAND -> water -> SATELLITE crossing with <=2-block probes. */
    private static double sampledCrossingGap(MacroGeography geography, MacroGeography.CrossingCandidate crossing) {
        MacroGeographySample a = geography.sample((int) crossing.fromX(), (int) crossing.fromZ());
        MacroGeographySample b = geography.sample((int) crossing.toX(), (int) crossing.toZ());
        if (a.nationId() != NationId.MAIN_NATION || b.nationId() != NationId.MAIN_NATION
                || a.landmassRole() != LandmassRole.MAINLAND || b.landmassRole() != LandmassRole.SATELLITE_ISLAND
                || a.landmassId() != crossing.fromLandmassId()
                || b.landmassId() != crossing.toLandmassId()) return Double.NaN;
        double length = Math.hypot(crossing.toX() - crossing.fromX(), crossing.toZ() - crossing.fromZ());
        int probes = (int) Math.ceil(length / 2), waterProbes = 0, phase = 0;
        for (int i = 0; i <= probes; i++) {
            double t = i / (double) probes;
            MacroGeographySample middle = geography.sample(
                    (int) Math.round(crossing.fromX() * (1 - t) + crossing.toX() * t),
                    (int) Math.round(crossing.fromZ() * (1 - t) + crossing.toZ() * t));
            if (middle.isWater()) {
                if (phase == 2 || middle.waterClass() == WaterClass.OPEN_OCEAN) return Double.NaN;
                phase = 1;
                waterProbes++;
            } else if (middle.nationId() != NationId.MAIN_NATION) {
                return Double.NaN;
            } else if (middle.landmassId() == a.landmassId()) {
                if (phase != 0) return Double.NaN;
            } else if (middle.landmassId() == b.landmassId()) {
                if (phase == 0) return Double.NaN;
                phase = 2;
            } else return Double.NaN;
        }
        return phase == 2 && waterProbes > 0 ? waterProbes * length / probes : Double.NaN;
    }

    private static String report(MacroGeography geography, Survey survey, int radius, int step) {
        StringBuilder out = new StringBuilder("AFL MACRO GEOGRAPHY DEV AUDIT\n");
        out.append("seed = ").append(geography.seed()).append("\n")
                .append("macroVersion = ").append(MacroGeography.VERSION).append("\n")
                .append("sampleRadius = ").append(radius).append(" blocks\n")
                .append("sampleStep = ").append(step).append(" blocks\n")
                .append("sampleResolution = ").append(survey.image.getWidth()).append(" x ")
                .append(survey.image.getHeight()).append("\n")
                .append("sampledAreaNote = sampled cell count * step^2, not exact land area\n\n")
                .append("MAIN NATION\nnationId = MAIN_NATION\nmainland landmassId = 0\n")
                .append("mainland role = MAINLAND\n");
        appendExtent(out, survey.land.get(0));
        MacroGeographySample spawn = geography.sample(0, 0);
        out.append("\nSPAWN SAFETY\nspawn surfaceClass = ").append(spawn.surfaceClass())
                .append("\nspawn nationId = ").append(spawn.nationId())
                .append("\nspawn landmassId = ").append(spawn.landmassId())
                .append("\nspawn landmassRole = ").append(spawn.landmassRole())
                .append("\nspawn to nearest OPEN_OCEAN = ").append(estimate(survey.nearestOcean, step))
                .append("\nspawn to nearest INLAND_SEA = ").append(estimate(survey.nearestInlandSea, step))
                .append("\n");
        int bridgeCount = 0;
        long satelliteCount = geography.islands().stream().filter(island -> island.role() == LandmassRole.SATELLITE_ISLAND).count();
        long minorCount = geography.islands().stream().filter(island -> island.role() == LandmassRole.MINOR_ISLAND).count();
        out.append("\nSATELLITE ISLANDS\nplannedCount = ").append(geography.islands().size()).append("\n");
        for (var island : geography.islands()) {
            out.append("\nIsland #").append(island.id()).append("\nlandmassId = ").append(island.id())
                    .append("\nlandmassRole = ").append(island.role()).append("\nnationId = ")
                    .append(geography.sample((int) Math.round(island.x()), (int) Math.round(island.z())).nationId())
                    .append("\ncenter = ").append(Math.round(island.x())).append(", ").append(Math.round(island.z()))
                    .append("\nmajor = ").append(island.majorRadius()).append("\nminor = ").append(island.minorRadius())
                    .append("\ngap = ").append(island.gap()).append("\nsectorAngle = ").append(island.angle())
                    .append("\n");
            var policy = geography.satellitePolicy(island.id());
            out.append("satelliteId = ").append(policy.satelliteId()).append("\nslot = ").append(policy.id())
                    .append("\nfacilityRole = ").append(policy.facilityRole())
                    .append("\nbridgePolicy = ").append(policy.bridgePolicy())
                    .append("\nfacilityEligibilityStatus = NOT_EVALUATED")
                    .append("\n");
            appendExtent(out, survey.land.get(island.id()));
            var crossing = geography.crossingCandidates().stream()
                    .filter(candidate -> candidate.toLandmassId() == island.id()).findFirst().orElse(null);
            if (crossing != null) {
                boolean eligible = bridgeEligible(geography, crossing);
                if (eligible) bridgeCount++;
                out.append("nearestMainlandWaterGap = ~").append(Math.round(crossing.waterSpan()))
                        .append(" blocks (supporting shores, numeric planning estimate)\n")
                        .append("sampledCrossingWaterGap = ").append(estimate(sampledCrossingGap(geography, crossing), 2))
                        .append("\nbridgeCandidate300to600 = ").append(eligible ? "YES" : "NO").append("\n");
            } else {
                out.append("nearestMainlandWaterGap = not available\nbridgeCandidate300to600 = NO\n");
            }
        }
        out.append("\nWATERBODIES (sampled estimates; OPEN_OCEAN id 0 omitted)\n");
        for (var entry : survey.water.entrySet()) {
            int id = entry.getKey();
            WaterClass type = id == 1 ? WaterClass.INLAND_SEA : id >= 100 ? WaterClass.STRAIT : WaterClass.BAY;
            out.append("\nwaterbodyId = ").append(id).append("\nwaterClass = ").append(type).append("\n");
            appendExtent(out, entry.getValue());
            out.append("touchesMainland = ").append(touches(geography, entry.getValue(), step, 0))
                    .append(" (sampled)\ntouchesSatelliteIsland = ")
                    .append(touches(geography, entry.getValue(), step, 1)).append(" (sampled)\n");
        }
        out.append("\nWATER CLASS SAMPLE COUNTS\n");
        for (WaterClass type : WaterClass.values())
            if (type != WaterClass.NONE) out.append(type).append(" = ")
                    .append(survey.waterCounts.getOrDefault(type, 0L)).append("\n");
        out.append("INLAND_SEA waterbody count = ").append(survey.water.containsKey(1) ? 1 : 0)
                .append("\nBAY waterbody count = ")
                .append(survey.water.keySet().stream().filter(id -> id >= 10 && id < 100).count())
                .append("\nSTRAIT waterbody count = ")
                .append(survey.water.keySet().stream().filter(id -> id >= 100).count()).append("\n")
                .append("OPEN_OCEAN sample coverage = ").append(survey.waterCounts.getOrDefault(WaterClass.OPEN_OCEAN, 0L))
                .append(" / ").append((long) survey.image.getWidth() * survey.image.getHeight()).append("\n");
        out.append("\nBRIDGE CANDIDATES 300-600 blocks (audit only)\ncount = ").append(bridgeCount).append("\n");
        for (var crossing : geography.crossingCandidates()) if (bridgeEligible(geography, crossing))
            out.append("MAIN_NATION MAINLAND ").append(crossing.fromLandmassId())
                    .append(" -> MAIN_NATION ").append(crossing.toLandmassId())
                    .append(" via waterbody ").append(crossing.waterbodyId())
                    .append(", planned gap ~").append(Math.round(crossing.waterSpan())).append("\n");
        out.append("\nAUDIT SUMMARY\nforeignLandDetected = ").append(survey.foreign)
                .append(" (sampled check)\nnonMainNationLandDetected = ").append(survey.nonMainNation)
                .append(" (sampled check)\nouterRingRadius = ").append(geography.outerOceanRadius())
                .append(" blocks\nouterRingOpenOceanSamples = ")
                .append(survey.outerOpenOcean).append(" / ").append(survey.outerSamples)
                .append(" (sampled check; radius ").append(geography.outerOceanRadius()).append("..").append(radius).append(")\n")
                .append("spawnIsSafeMainland = ").append(spawn.isLand()
                        && spawn.nationId() == NationId.MAIN_NATION
                        && spawn.landmassRole() == LandmassRole.MAINLAND
                        && spawn.surfaceClass() == SurfaceClass.LAND).append("\n")
                .append("satelliteIslandCount = ").append(satelliteCount)
                .append("\nminorIslandCount = ").append(minorCount)
                .append("\nbridgeCandidateCount300to600 = ").append(bridgeCount)
                .append("\nrequiredSatelliteIslandRuleSatisfied = ").append(satelliteCount == 3)
                .append("\nrequiredBridgeCandidateRuleSatisfied = ").append(bridgeCount == 3)
                .append("\nmajorCoastalFeature = STRAIT_COMPLEX (same-nation satellite group)\n");
        return out.toString();
    }

    private static String estimate(double value, int step) {
        return Double.isFinite(value) ? "~" + Math.round(value) + " blocks (sampled estimate, step "
                + step + ")" : "not observed within sampled map";
    }
    private static void appendExtent(StringBuilder out, Extent extent) {
        if (extent == null) {
            out.append("estimated bounds = not observed at sample step\n");
            return;
        }
        out.append("estimated bounds = x ").append(extent.minX).append("..").append(extent.maxX)
                .append(", z ").append(extent.minZ).append("..").append(extent.maxZ)
                .append("\nestimated width = ").append(extent.maxX - extent.minX + extent.step)
                .append(" blocks\nestimated height = ").append(extent.maxZ - extent.minZ + extent.step)
                .append(" blocks\nestimated sampled area = ").append(extent.count * (long) extent.step * extent.step)
                .append(" blocks^2\n");
    }
    private static boolean touches(MacroGeography geography, Extent extent, int step, int role) {
        // Classify any sampled shore immediately around the observed waterbody bounds.
        for (int z = extent.minZ - step; z <= extent.maxZ + step; z += step)
            for (int x = extent.minX - step; x <= extent.maxX + step; x += step) {
                MacroGeographySample value = geography.sample(x, z);
                if (value.isLand() && (role == 0 ? value.landmassId() == 0 : value.landmassId() > 0)
                        && Math.abs(value.coastDistance()) <= step * 2) return true;
            }
        return false;
    }

    private static final class Extent {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int step;
        long count;
        void add(int x, int z, int sampleStep) {
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            step = sampleStep;
            count++;
        }
    }
    private record Survey(BufferedImage image, Map<Integer, Extent> land, Map<Integer, Extent> water,
                          EnumMap<WaterClass, Long> waterCounts, boolean foreign, boolean nonMainNation,
                          double nearestOcean, double nearestInlandSea, long outerSamples, long outerOpenOcean) {}
}
