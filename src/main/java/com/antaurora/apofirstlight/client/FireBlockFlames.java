package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Vanilla fire blocks drawn with the fuel flames (2026-10-05, docs/gameplay/fuel_fire_v1.md "火焰效果 V2"): one fire style
 * in the game. Vanilla's model (the fire texture on crossed planes, cut out: hard edges, and seen edge-on a thin bright
 * line) is replaced by an empty one (assets/minecraft/blockstates/fire.json -> apocalypse_firstlight:block/fire_hidden);
 * this client keeps the fire blocks near it (found when a chunk arrives, followed through every block change:
 * LevelRendererFireTrackMixin; also noticed by BaseFireBlockSmokeMixin as vanilla ticks their looks) and draws each as a
 * few flames (FuelFlames#flame: three crossed planes each, fixed in the world; warm white): three over a burning floor
 * with a glow under them, two against each side it clings to, one under a ceiling. Its outline is not drawn. Sparks now and then; the smoke is vanilla's own timing, our puffs (BaseFireBlockSmokeMixin). Soul
 * fire keeps its vanilla look.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FireBlockFlames {
    private static final double RANGE = 64.0, SPARK_RANGE = 24.0;
    private static final BooleanProperty[] SIDES = {FireBlock.NORTH, FireBlock.EAST, FireBlock.SOUTH, FireBlock.WEST};
    private static final Direction[] SIDE_DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private static final Map<Long, Set<Long>> BY_CHUNK = new HashMap<>();
    @Nullable
    private static ClientLevel trackedLevel;

    private FireBlockFlames() {
    }

    public static boolean isEmpty() {
        return BY_CHUNK.isEmpty();
    }

    /** A block changed on this client (LevelRendererFireTrackMixin). */
    public static void changed(BlockPos pos, BlockState old, BlockState now) {
        boolean was = old.is(Blocks.FIRE), is = now.is(Blocks.FIRE);
        if (was == is) return;
        if (is) seen(pos);
        else {
            long chunk = ChunkPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
            Set<Long> set = BY_CHUNK.get(chunk);
            if (set != null && set.remove(pos.asLong()) && set.isEmpty()) BY_CHUNK.remove(chunk);
        }
    }

    /** A fire block is here (a change, or vanilla ticking its looks). */
    public static void seen(BlockPos pos) {
        BY_CHUNK.computeIfAbsent(ChunkPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())), k -> new HashSet<>())
                .add(pos.asLong());
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel) scan(event.getChunk());
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) BY_CHUNK.remove(event.getChunk().getPos().toLong());
    }

    private static void scan(ChunkAccess chunk) {
        Set<Long> found = new HashSet<>();
        LevelChunkSection[] sections = chunk.getSections();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(s -> s.is(Blocks.FIRE))) continue;
            int y0 = SectionPos.sectionToBlockCoord(chunk.getMinSection() + i);
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                if (section.getBlockState(x, y, z).is(Blocks.FIRE)) found.add(BlockPos.asLong(x0 + x, y0 + y, z0 + z));
            }
        }
        if (found.isEmpty()) BY_CHUNK.remove(chunk.getPos().toLong());
        else BY_CHUNK.put(chunk.getPos().toLong(), found);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != trackedLevel) {
            BY_CHUNK.clear();
            trackedLevel = level;
        }
        if (level == null || BY_CHUNK.isEmpty() || minecraft.isPaused()) return;
        boolean check = level.getGameTime() % 40 == 0;
        Vec3 viewer = minecraft.gameRenderer.getMainCamera().getPosition();
        RandomSource r = level.random;
        for (Iterator<Set<Long>> chunks = BY_CHUNK.values().iterator(); chunks.hasNext(); ) {
            Set<Long> set = chunks.next();
            for (Iterator<Long> it = set.iterator(); it.hasNext(); ) {
                BlockPos p = BlockPos.of(it.next());
                if (check && level.isLoaded(p) && !level.getBlockState(p).is(Blocks.FIRE)) {   // missed a change: forget it
                    it.remove();
                    continue;
                }
                if (r.nextFloat() < 0.05F && p.distToCenterSqr(viewer) < SPARK_RANGE * SPARK_RANGE) {
                    FireFx.ember(level, p.getX() + 0.2 + r.nextDouble() * 0.6, p.getY() + 0.3 + r.nextDouble() * 0.8, p.getZ() + 0.2 + r.nextDouble() * 0.6);
                }
            }
            if (set.isEmpty()) chunks.remove();
        }
    }

    /** The glow on the floor under fires burning on it (the GLOW batch). */
    static void renderGlows(PoseStack pose, MultiBufferSource buffers, ClientLevel level, Vec3 camera, double now) {
        if (BY_CHUNK.isEmpty()) return;
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        VertexConsumer out = null;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (Set<Long> set : BY_CHUNK.values()) {
            for (long key : set) {
                p.set(key);
                if (p.distToCenterSqr(camera) > RANGE * RANGE) continue;
                BlockState state = level.getBlockState(p);
                if (!state.is(Blocks.FIRE) || state.getValue(FireBlock.UP)) continue;
                boolean clings = false;
                for (BooleanProperty side : SIDES) clings |= state.getValue(side);
                if (clings) continue;
                if (out == null) out = buffers.getBuffer(LiquidRenderTypes.GLOW);
                double flicker = 0.85 + 0.15 * Math.sin(now * 0.6 + (key * 0x9E3779B97F4A7C15L >>> 50));
                FuelFlames.glow(out, matrix, normals, camera, new Vec3(p.getX() + 0.5, p.getY() + 0.01, p.getZ() + 0.5), 0.5, 255, 105, 30,
                        (int) Math.round(75 * flicker));
            }
        }
    }

    /**
     * No outline round a fire block (user 2026-10-05: the thin box at its foot showed it as a block). It can still be
     * aimed at and beaten out where it was.
     */
    @SubscribeEvent
    public static void onHighlight(net.minecraftforge.client.event.RenderHighlightEvent.Block event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.getBlockState(event.getTarget().getBlockPos()).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock) {
            event.setCanceled(true);
        }
    }

    /** The flames (the FLAME batch, relative to the camera). */
    static void render(PoseStack pose, MultiBufferSource buffers, ClientLevel level, Vec3 camera, double now) {
        if (BY_CHUNK.isEmpty()) return;
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        VertexConsumer out = null;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (Set<Long> set : BY_CHUNK.values()) {
            for (long key : set) {
                p.set(key);
                if (p.distToCenterSqr(camera) > RANGE * RANGE) continue;
                BlockState state = level.getBlockState(p);
                if (!state.is(Blocks.FIRE)) continue;
                if (out == null) out = buffers.getBuffer(LiquidRenderTypes.FLAME);
                long h = key * 0x9E3779B97F4A7C15L;
                double phase = now * 1.2 + (h >>> 40 & 47);
                boolean clings = state.getValue(FireBlock.UP);
                for (BooleanProperty side : SIDES) clings |= state.getValue(side);
                double far = p.distToCenterSqr(camera);
                int floorFlames = far < 20 * 20 ? 3 : far < 40 * 40 ? 2 : 1;   // fewer, larger flames far away
                if (!clings) {   // on the floor
                    for (int k = 0; k < floorFlames; k++) {
                        long hk = h ^ (k * 0xC2B2AE3D27D4EB4FL);
                        double rx = (hk >>> 11 & 1023) / 1023.0, rz = (hk >>> 23 & 1023) / 1023.0, rw = (hk >>> 37 & 1023) / 1023.0;
                        double width = (0.7 + 0.3 * rw) * (floorFlames == 1 ? 1.25 : 1.0);
                        Vec3 base = new Vec3(p.getX() + 0.25 + 0.5 * rx, p.getY() - 0.02, p.getZ() + 0.25 + 0.5 * rz);
                        FuelFlames.flame(out, matrix, normals, camera, camera, base, rw * Math.PI, width, width * 1.55, phase + k * 16, 255, 235, 215, 230);
                    }
                    continue;
                }
                for (int i = 0; i < SIDES.length; i++) {   // against the burning sides
                    if (!state.getValue(SIDES[i])) continue;
                    Direction d = SIDE_DIRS[i];
                    for (int k = 0; k < 2; k++) {
                        double along = (k == 0 ? 0.3 : 0.7) + ((h >>> (8 * k + 3) & 15) / 15.0 - 0.5) * 0.15;
                        Direction across = d.getClockWise();
                        Vec3 base = new Vec3(p.getX() + 0.5 + d.getStepX() * 0.4 + (across.getStepX() * (along - 0.5)), p.getY(),
                                p.getZ() + 0.5 + d.getStepZ() * 0.4 + (across.getStepZ() * (along - 0.5)));
                        FuelFlames.flame(out, matrix, normals, camera, camera, base, (h >>> (5 * k) & 63) / 63.0 * Math.PI, 0.65, 1.05, phase + i * 7 + k * 19, 255, 235, 215, 220);
                    }
                }
                if (state.getValue(FireBlock.UP)) {   // under a ceiling
                    Vec3 base = new Vec3(p.getX() + 0.5, p.getY() + 0.35, p.getZ() + 0.5);
                    FuelFlames.flame(out, matrix, normals, camera, camera, base, (h >>> 20 & 63) / 63.0 * Math.PI, 0.8, 0.65, phase + 31, 255, 235, 215, 210);
                }
            }
        }
    }
}
