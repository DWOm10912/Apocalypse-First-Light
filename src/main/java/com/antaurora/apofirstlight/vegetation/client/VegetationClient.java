package com.antaurora.apofirstlight.vegetation.client;

import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.tree.TreeShaderIds;
import com.antaurora.apofirstlight.vegetation.MeshPlantBlock;
import com.mojang.brigadier.arguments.StringArgumentType;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Client side of the mesh plants (AFL vegetation P1, docs/gameplay/vegetation_v1_design.md 10-13):
 * <ul>
 * <li>Distance levels per block (doc 17.3; were per 16 m section, 13.8): a plant block's stage comes from the horizontal
 * camera distance to its middle when its section is meshed: a level's own stage below its blend band, then
 * {@value #STEPS} - 1 blend stages across the band (24-40 m LOD0 to LOD1, 56-80 m to LOD2, 96-120 m to nothing), each
 * swapping a further share of the clumps by their rank, so a band is a soft gradient on the ground, not a square.
 * Every section a plant was meshed in is known here with a key: its one stage when all of it is at one level, else its
 * band and the camera's distance to its middle in steps of that band's bucket (2, 4, 6 m), the step longer for a heavy
 * section (doc 18.8: up to {@value #HEAVY_MAX} times for {@value #HEAVY_QUADS} LOD0 quads and more, so a reed bed is not
 * rebuilt every two metres); a changed key waits up to {@value #FLUSH_TICKS} ticks and marks the section for a rebuild
 * (the mesh trees' scheme, TreeClient). Only sections in a band are rebuilt as the camera moves.</li>
 * <li>Footprints and wheel tracks are the server's block state (MeshPlantBlock, VegetationFootfalls); the blocks around
 * anyone walking through are simulated and drawn by the near layer (VegetationDynamics).</li>
 * <li>The model, its colour (tint 0: the biome's grass colour, as the ground) and the shader pack's ID for the standing
 * states only (the pack's own short grass, so it waves them; what lies keeps no ID and stays still: TreeShaderIds); the
 * wind without a pack is VegetationWind.</li>
 * </ul>
 * {@code /afl_vegetation status|lod <auto|0|1|2|off>} (client command, development runs).
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", value = Dist.CLIENT)
public final class VegetationClient {
    static final int STEPS = 8, STAGES = PlantShape.LEVELS * STEPS + 1, NOTHING_STAGE = STAGES - 1;
    /**
     * The blend bands (m from the camera to a plant block): LOD0 to LOD1, LOD1 to LOD2, LOD2 to nothing (doc 17.3; user
     * 2026-10-24 "LOD0 的范围太小了": were 12-20, 36-48, 64-80 m to a section's nearest point).
     */
    private static final float[][] BANDS = {{24, 40}, {56, 80}, {96, 120}};
    /** How far the camera's distance to a section in each band moves before the section is rebuilt (m). */
    private static final float[] BUCKET = {2, 4, 6};
    static final int FLUSH_TICKS = 5;
    /** A section's LOD0 quads at which its rebuild step starts to grow (in half steps), and the most it grows. */
    static final int HEAVY_QUADS = 40000, HEAVY_MAX = 3;
    /** Per section: which of its blocks have been weighed, and their LOD0 quads summed. */
    private static final ConcurrentHashMap<Long, Weight> WEIGHTS = new ConcurrentHashMap<>();

    private static final class Weight {
        final java.util.BitSet seen = new java.util.BitSet(4096);
        volatile int quads;
    }

    /** -1: by distance; else one stage for every section (the lod command). */
    static volatile int override = -1;
    private static volatile double camX, camZ;
    /** Section -> stage (read by the chunk builders). */
    private static final ConcurrentHashMap<Long, Integer> SECTIONS = new ConcurrentHashMap<>();
    /** Client thread only: stage changes waiting; sections to mark. */
    private static final ConcurrentHashMap<Long, Integer> PENDING = new ConcurrentHashMap<>();
    private static final LongOpenHashSet DIRTY = new LongOpenHashSet();
    private static final LongAdder builds = new LongAdder(), buildNanos = new LongAdder(), quadsOut = new LongAdder();
    /** Rebuild marks by the new key: all at one stage, in band 0, 1, 2. */
    private static final int[] keyChanges = new int[1 + 3];
    private static long ticks, marks;

    private VegetationClient() {}

    // ------------------------------------------------------------------------- read by the chunk builders

    /** A plant block's stage, its LOD0 weight (quads) counted once into its section's. */
    static int stage(BlockPos pos, int nearQuads) {
        Weight w = WEIGHTS.computeIfAbsent(SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4), k -> new Weight());
        int i = (pos.getY() & 15) << 8 | (pos.getZ() & 15) << 4 | pos.getX() & 15;
        synchronized (w) {
            if (!w.seen.get(i)) {
                w.seen.set(i);
                w.quads += nearQuads;
            }
        }
        return stage(pos);
    }

    /** A plant block's stage, from the camera now (its section, seen for the first time, is known from now on). */
    static int stage(BlockPos pos) {
        int o = override;
        if (o >= 0) return o;
        SECTIONS.computeIfAbsent(SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4), VegetationClient::keyOf);
        return stageAt(Math.hypot(pos.getX() + 0.5 - camX, pos.getZ() + 0.5 - camZ));
    }

    static void count(long start, int quads) {
        builds.increment();
        buildNanos.add(System.nanoTime() - start);
        quadsOut.add(quads);
    }

    /** The stage at a horizontal distance: a level's own stage below its band, then 1 .. STEPS - 1 across the band. */
    static int stageAt(double d) {
        for (int b = 0; b < BANDS.length; b++) {
            if (d < BANDS[b][0]) return b * STEPS;
            if (d < BANDS[b][1]) return b * STEPS + 1 + (int) ((d - BANDS[b][0]) / (BANDS[b][1] - BANDS[b][0]) * (STEPS - 1));
        }
        return NOTHING_STAGE;
    }

    /**
     * A section's rebuild key: -1 - its stage when every point of it is at one level's own stage (or nothing); else its
     * band (the one nearest its middle) * 100000 + the camera's distance to its middle in that band's buckets, each bucket
     * longer for a heavy section ({@link #heavy}).
     */
    private static int keyOf(long section) {
        int x0 = SectionPos.sectionToBlockCoord(SectionPos.x(section)), z0 = SectionPos.sectionToBlockCoord(SectionPos.z(section));
        double nx = Math.max(0, Math.max(x0 - camX, camX - (x0 + 16))), nz = Math.max(0, Math.max(z0 - camZ, camZ - (z0 + 16)));
        double fx = Math.max(Math.abs(x0 - camX), Math.abs(x0 + 16 - camX)), fz = Math.max(Math.abs(z0 - camZ), Math.abs(z0 + 16 - camZ));
        int near = stageAt(Math.hypot(nx, nz)), far = stageAt(Math.hypot(fx, fz));
        if (near == far && near % STEPS == 0) return -1 - near;
        double mid = Math.hypot(x0 + 8 - camX, z0 + 8 - camZ), best = Double.MAX_VALUE;
        int band = 0;
        for (int b = 0; b < BANDS.length; b++) {
            double gap = Math.max(0, Math.max(BANDS[b][0] - mid, mid - BANDS[b][1]));
            if (gap < best) { best = gap; band = b; }
        }
        return band * 100000 + (int) Math.floor(mid / (BUCKET[band] * heavy(section)));
    }

    /** How much longer a section's rebuild step is: 1 up to HEAVY_QUADS of LOD0 quads, then in half steps up to HEAVY_MAX. */
    private static float heavy(long section) {
        Weight w = WEIGHTS.get(section);
        if (w == null) return 1;
        return Math.max(1, Math.min(HEAVY_MAX, Math.round(w.quads / (float) HEAVY_QUADS * 2) / 2F));
    }

    // ------------------------------------------------------------------------------------------- ticking

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.gameRenderer == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        camX = cam.x;
        camZ = cam.z;
        ticks++;
        if (override < 0) {
            for (var e : SECTIONS.entrySet()) {
                int old = e.getValue(), now = keyOf(e.getKey());
                if (now != old) PENDING.put(e.getKey(), now);
                else PENDING.remove(e.getKey());
            }
            if (ticks % FLUSH_TICKS == 0 && !PENDING.isEmpty()) {
                for (var e : PENDING.entrySet()) {
                    SECTIONS.put(e.getKey(), e.getValue());
                    keyChanges[e.getValue() < 0 ? 0 : 1 + e.getValue() / 100000]++;
                    DIRTY.add(e.getKey());
                }
                PENDING.clear();
            }
        }
        if (!DIRTY.isEmpty() && mc.levelRenderer != null) {
            var it = DIRTY.iterator();
            while (it.hasNext()) {
                long k = it.nextLong();
                mc.levelRenderer.setSectionDirty(SectionPos.x(k), SectionPos.y(k), SectionPos.z(k));
                marks++;
            }
        }
        DIRTY.clear();
    }

    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        int cx = event.getChunk().getPos().x, cz = event.getChunk().getPos().z;
        SECTIONS.keySet().removeIf(k -> SectionPos.x(k) == cx && SectionPos.z(k) == cz);
        WEIGHTS.keySet().removeIf(k -> SectionPos.x(k) == cx && SectionPos.z(k) == cz);
        PENDING.keySet().removeIf(k -> SectionPos.x(k) == cx && SectionPos.z(k) == cz);
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SECTIONS.clear();
        WEIGHTS.clear();
        PENDING.clear();
        DIRTY.clear();
        override = -1;
    }

    // ------------------------------------------------------------------------------------------ command

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        var lod = Commands.literal("lod").then(Commands.argument("value", StringArgumentType.word()).executes(c -> {
            String v = StringArgumentType.getString(c, "value").toLowerCase(Locale.ROOT);
            int o;
            switch (v) {
                case "auto" -> o = -1;
                case "0", "1", "2" -> o = Integer.parseInt(v) * STEPS;
                case "off" -> o = NOTHING_STAGE;
                default -> { c.getSource().sendFailure(Component.literal("auto / 0 / 1 / 2 / off")); return 0; }
            }
            lod(o);
            c.getSource().sendSuccess(() -> Component.literal("vegetation lod " + v), false);
            return 1;
        }));
        event.getDispatcher().register(Commands.literal("afl_vegetation")
                .then(Commands.literal("status").executes(c -> {
                    for (String line : status().split("\n")) c.getSource().sendSuccess(() -> Component.literal(line), false);
                    return 1;
                }))
                .then(lod));
    }

    /** The lod command's switch (also dev/VegetationPerfProbe): -1 by distance, else one stage for every section. */
    public static void lod(int stage) {
        override = stage;
        DIRTY.addAll(SECTIONS.keySet());
    }

    /** A level's stage for lod (0, 1, 2; 3 nothing). */
    public static int levelStage(int level) {
        return Math.min(NOTHING_STAGE, level * STEPS);
    }

    public static String status() {
        int[] at = new int[1 + 3];
        for (int k : SECTIONS.values()) at[k < 0 ? 0 : 1 + k / 100000]++;
        int heavy = 0;
        for (long k : SECTIONS.keySet()) if (heavy(k) > 1) heavy++;
        long n = builds.sum();
        return String.format(Locale.ROOT, "Vegetation (client): %d sections with plants (%d heavy), %s (at one level, in band 0, 1, 2; %d steps a band: %s m), view %s%n"
                        + "  rebuild marks %d; by new key %s%n"
                        + "  chunk builds: %d plant blocks, %.2f us each, %d quads (%.1f a block); baked layouts %d%n"
                        + "  wind: %s; %s; %s; shader pack IDs: %s",
                SECTIONS.size(), heavy, Arrays.toString(at), STEPS, Arrays.deepToString(BANDS), override < 0 ? "auto" : "stage " + override,
                marks, Arrays.toString(keyChanges),
                n, n == 0 ? 0 : buildNanos.sum() / 1000.0 / n, quadsOut.sum(), n == 0 ? 0 : quadsOut.sum() / (double) n, PlantModel.cacheSize(),
                VegetationWind.status(), VegetationShaderData.status(), VegetationDynamics.status(), TreeShaderIds.describe());
    }

    // ------------------------------------------------------------------------------------- registration

    @Mod.EventBusSubscriber(modid = "apocalypse_firstlight", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {}

        static MeshPlantBlock[] plants() {
            return new MeshPlantBlock[]{(MeshPlantBlock) AflBlocks.KENTUCKY_BLUEGRASS.get(), (MeshPlantBlock) AflBlocks.BROADLEAF_CATTAIL.get(),
                    (MeshPlantBlock) AflBlocks.COMMON_YARROW.get(), (MeshPlantBlock) AflBlocks.BIG_BLUESTEM.get(),
                    (MeshPlantBlock) AflBlocks.LITTLE_BLUESTEM.get(), (MeshPlantBlock) AflBlocks.PENNSYLVANIA_SEDGE.get(),
                    (MeshPlantBlock) AflBlocks.BRACKEN_FERN.get(), (MeshPlantBlock) AflBlocks.BROADLEAF_PLANTAIN.get(),
                    (MeshPlantBlock) AflBlocks.DANDELION.get(), (MeshPlantBlock) AflBlocks.PURPLE_CONEFLOWER.get(),
                    (MeshPlantBlock) AflBlocks.BLACK_EYED_SUSAN.get(), (MeshPlantBlock) AflBlocks.CANADA_GOLDENROD.get(),
                    (MeshPlantBlock) AflBlocks.COMMON_REED.get(), (MeshPlantBlock) AflBlocks.ALLEGHENY_BLACKBERRY.get()};
        }

        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent event) {
            // the pack's short grass ID for what stands, so a pack waves it as its own grass; what is trodden keeps none
            event.enqueueWork(() -> {
                VegetationShaderData.register();
                for (MeshPlantBlock plant : plants())
                    TreeShaderIds.aliasLike(plant, Blocks.GRASS, s -> s.getValue(MeshPlantBlock.TRODDEN) == MeshPlantBlock.STANDING);
            });
        }

        @SubscribeEvent
        public static void models(ModelEvent.ModifyBakingResult event) {
            for (MeshPlantBlock plant : plants()) {
                var model = new PlantModel(plant.species);
                for (BlockState s : plant.getStateDefinition().getPossibleStates())
                    event.getModels().put(BlockModelShaper.stateToModelLocation(s), model);
            }
        }

        @SubscribeEvent
        public static void baked(ModelEvent.BakingCompleted event) {
            PlantModel.clearCache();
            VegetationWind.spriteChanged();
            VegetationDynamics.spritesChanged = true;
        }

        @SubscribeEvent
        public static void colors(RegisterColorHandlersEvent.Block event) {
            event.register((state, level, pos, tint) -> tint != 0 ? -1 : level != null && pos != null
                    ? BiomeColors.getAverageGrassColor(level, pos) : GrassColor.getDefaultColor(), plants());
        }
    }
}
