package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.vegetation.MeshPlantBlock;
import com.antaurora.apofirstlight.vegetation.client.VegetationClient;
import com.antaurora.apofirstlight.vegetation.client.VegetationDynamics;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Vegetation frame time probe (dev only; src/dev/veg-perf-client.init.gradle, docs/gameplay/vegetation_v1_design.md 13.10;
 * user 2026-10-24: "原版300fps，进去逛一圈只剩80多，光影还是和之前一样卡，20帧左右"): with {@code -Dafl.vegperf=WORLD}
 * a development client in its own run directory opens a copy of the player's save where it left the player (third
 * person from behind, as the user plays), counts the mesh plants around, then runs phases of {@value #MEASURE} ticks
 * after {@value #SETTLE} ticks of settling: the distance levels as they are standing and walking (the forward key held,
 * turning round every 3 s), walking while the view turns ({@value #TURN} degrees a second, every frame: a circle of
 * about 4 m; user 2026-10-24 "在植物多的地方走路转动视角会最低到60fps"), the same with the near layer off, then every
 * section at LOD0, LOD1, LOD2 and no plants, standing at the start, and no plants walking and turning. Each phase's frame times (mean, p95, worst), its wall clock (for a JFR profile of the run) and
 * the vegetation status lines go to {@code afl_veg_perf.txt}; then it quits. Vsync off, no frame cap.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class VegetationPerfProbe {
    private static final String WORLD = System.getProperty("afl.vegperf", "");
    private static final boolean SHADERS = Boolean.getBoolean("afl.vegperf.shaders");
    /** -Dafl.vegperf.mobs=true: the mobs stay (as the player meets them; each one in the plants brings near layer of its own). */
    private static final boolean MOBS = Boolean.getBoolean("afl.vegperf.mobs");
    /** -Dafl.vegperf.dense=true: start in the middle of the 32 m square with the most plants the client has loaded. */
    private static final boolean DENSE = Boolean.getBoolean("afl.vegperf.dense");
    /** -Dafl.vegperf.first=true: first person instead of third person from behind. */
    private static final boolean FIRST = Boolean.getBoolean("afl.vegperf.first");
    private static final int WARM = 20 * 20, SETTLE = 20 * 6, MEASURE = 20 * 10, PHASE = SETTLE + MEASURE;
    private static final String[] PHASES = {"auto, standing", "auto, walking", "auto, walking and turning",
            "auto, walking and turning, near layer off", "lod 0 everywhere, standing", "lod 1 everywhere, standing",
            "lod 2 everywhere, standing", "no plants, standing", "no plants, walking and turning"};
    /** The view's turn in the turning phases (degrees a second). */
    private static final float TURN = 60;
    private static boolean started;
    private static int ticks = -1, phase = -1;
    private static Vec3 start;
    private static float startYaw;
    private static boolean measuring;
    private static final List<Long> FRAMES = new ArrayList<>();
    private static long lastFrame, phaseStart;
    private static final StringBuilder OUT = new StringBuilder();

    private VegetationPerfProbe() {}

    static {
        if (!WORLD.isEmpty()) {
            Thread watchdog = new Thread(() -> {
                try { Thread.sleep(10 * 60_000L); } catch (InterruptedException e) { return; }
                log("TIMEOUT: halting");
                Runtime.getRuntime().halt(3);
            }, "AFL veg perf watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
        }
    }

    private static void log(String s) { ApocalypseFirstLight.LOGGER.info("[AFL VEG PERF] {}", s); }

    @SubscribeEvent
    public static void onTitle(ScreenEvent.Init.Post event) {
        if (WORLD.isEmpty() || started || !(event.getScreen() instanceof TitleScreen screen)) return;
        started = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.enableVsync().set(false);
        mc.getWindow().updateVsync(false);
        mc.options.framerateLimit().set(260);   // 260: unlimited
        mc.tell(() -> {
            log("opening " + WORLD);
            mc.createWorldOpenFlows().loadLevel(screen, WORLD);
        });
    }

    @SubscribeEvent
    public static void onFrame(TickEvent.RenderTickEvent event) {
        if (WORLD.isEmpty() || event.phase != TickEvent.Phase.START) return;
        long now = System.nanoTime();
        if (measuring && lastFrame != 0) FRAMES.add(now - lastFrame);
        var player = Minecraft.getInstance().player;
        if (turning() && player != null && lastFrame != 0) {
            float yaw = player.getYRot() + TURN * Math.min(0.1F, (now - lastFrame) / 1e9F);
            player.setYRot(yaw);
            player.yRotO = yaw;
            player.setYHeadRot(yaw);
        }
        lastFrame = now;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (WORLD.isEmpty() || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ticks++;
        if (ticks == 40) {
            start = mc.player.position();
            startYaw = mc.player.getYRot();
            OUT.append(densest(mc)).append('\n');
            if (DENSE && dense != null) {
                start = dense;
                back(mc);
            }
            mc.options.setCameraType(FIRST ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK);
            mc.player.setXRot(10);
            OUT.append(String.format("start %s yaw %.0f, %s%n", BlockPos.containing(start).toShortString(), startYaw, plants(mc)));
            // the same run every time: no mobs wandering through the plants (they bring the near layer of their own)
            var server = mc.getSingleplayerServer();
            if (server != null && !MOBS) server.execute(() -> {
                var player = server.getPlayerList().getPlayers().get(0);
                int n = 0;
                for (var e : server.overworld().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(96))) {
                    e.discard();
                    n++;
                }
                log("mobs removed within 96 m: " + n);
            });
            log(OUT.toString());
        }
        if (ticks < WARM) return;
        int t = ticks - WARM;
        boolean walking = phase == 1 || turning();
        mc.options.keyUp.setDown(walking);
        if (phase == 1 && t % 60 == 0) {
            mc.player.setYRot(mc.player.getYRot() + 180);
            mc.player.setYHeadRot(mc.player.getYRot());
        }
        if (t % PHASE == SETTLE) {
            FRAMES.clear();
            measuring = true;
            phaseStart = System.currentTimeMillis();
        }
        if (t % PHASE != 0) return;
        if (phase >= 0) report(PHASES[phase]);
        measuring = false;
        phase++;
        if (phase >= PHASES.length) {
            finish(mc);
            return;
        }
        VegetationDynamics.enabled = phase != 3;
        VegetationClient.lod(phase <= 3 ? -1 : VegetationClient.levelStage(Math.min(3, phase - 4)));
        if (phase == 0 || phase == 2 || phase >= 4) back(mc);
        log("phase " + PHASES[phase]);
    }

    private static boolean turning() {
        return phase == 2 || phase == 3 || phase == 8;
    }

    /** Back to the start, looking as at the start (the server's teleport). */
    private static void back(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null || start == null) return;
        Vec3 at = start;
        float yaw = startYaw;
        server.execute(() -> server.getPlayerList().getPlayers().get(0).teleportTo(server.overworld(), at.x, at.y, at.z, yaw, 10));
    }

    private static Vec3 dense;

    /**
     * The loaded columns' plants (the top block and the one under it, as the plants stand) counted in 32 m squares on a
     * 16 m grid; the five fullest and the fullest's middle (on the ground) as {@link #dense}.
     */
    private static String densest(Minecraft mc) {
        var level = mc.level;
        var counts = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        BlockPos c = mc.player.blockPosition();
        int r = 16 * Math.min(12, mc.options.getEffectiveRenderDistance());
        var pos = new BlockPos.MutableBlockPos();
        for (int x = c.getX() - r; x < c.getX() + r; x++)
            for (int z = c.getZ() - r; z < c.getZ() + r; z++) {
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
                boolean plant = false;
                for (int y = top - 3; y < top && !plant; y++)
                    plant = level.getBlockState(pos.set(x, y, z)).getBlock() instanceof MeshPlantBlock;
                if (!plant) continue;
                // each 32 m square on the 16 m grid that holds the column
                for (int ox = 0; ox < 2; ox++)
                    for (int oz = 0; oz < 2; oz++)
                        counts.addTo(net.minecraft.world.level.ChunkPos.asLong((x >> 4) - ox, (z >> 4) - oz), 1);
            }
        long[] keys = counts.keySet().toLongArray();
        Long[] boxed = Arrays.stream(keys).boxed().toArray(Long[]::new);
        Arrays.sort(boxed, (a, b) -> Integer.compare(counts.get((long) b), counts.get((long) a)));
        StringBuilder out = new StringBuilder("fullest 32 m squares (plant columns):");
        for (int i = 0; i < Math.min(5, boxed.length); i++) {
            var cp = new net.minecraft.world.level.ChunkPos(boxed[i]);
            out.append(String.format(" [%d, %d] %d", cp.getMinBlockX() + 16, cp.getMinBlockZ() + 16, counts.get((long) boxed[i])));
        }
        if (boxed.length > 0) {
            var cp = new net.minecraft.world.level.ChunkPos(boxed[0]);
            int x = cp.getMinBlockX() + 16, z = cp.getMinBlockZ() + 16;
            dense = new Vec3(x + 0.5, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z), z + 0.5);
            out.append(String.format(" -> fullest middle %.0f %.0f %.0f", dense.x, dense.y, dense.z));
        }
        return out.toString();
    }

    /** The mesh plants within 48 m (8 up and down) of the player, by species. */
    private static String plants(Minecraft mc) {
        int[] n = new int[MeshPlantBlock.Species.values().length];
        BlockPos c = mc.player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-48, -8, -48), c.offset(48, 8, 48)))
            if (mc.level.getBlockState(p).getBlock() instanceof MeshPlantBlock m) n[m.species.ordinal()]++;
        StringBuilder s = new StringBuilder("plants within 48 m:");
        for (var sp : MeshPlantBlock.Species.values()) s.append(' ').append(sp).append(' ').append(n[sp.ordinal()]);
        return s.toString();
    }

    private static void report(String name) {
        long[] f = FRAMES.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(f);
        double mean = Arrays.stream(f).average().orElse(0) / 1e6;
        double p95 = f.length == 0 ? 0 : f[Math.min(f.length - 1, (int) (f.length * 0.95))] / 1e6;
        double worst = f.length == 0 ? 0 : f[f.length - 1] / 1e6;
        OUT.append(String.format("== %s: %d frames, mean %.2f ms (%.0f fps), p95 %.2f ms, worst %.2f ms%n", name, f.length, mean, mean > 0 ? 1000 / mean : 0, p95, worst));
        OUT.append(String.format("   wall ms %d-%d%n", phaseStart, System.currentTimeMillis()));
        for (String line : VegetationClient.status().split("\n")) OUT.append("   ").append(line).append('\n');
        log(OUT.substring(OUT.lastIndexOf("== ")));
    }

    private static void finish(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        OUT.insert(0, "GL " + com.mojang.blaze3d.platform.GlUtil.getRenderer() + (SHADERS ? "; shader pack on" : "; no shader pack") + (MOBS ? "; mobs kept" : "; mobs removed") + (DENSE ? "; started in the fullest square" : "") + (FIRST ? "; first person" : "; third person") + "; window "
                + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + "\n");
        try {
            Files.writeString(FMLPaths.GAMEDIR.get().resolve("afl_veg_perf" + (SHADERS ? "_pack" : "") + (MOBS ? "_mobs" : "") + (DENSE ? "_dense" : "") + (FIRST ? "_first" : "") + ".txt"), OUT.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log("write failed " + e);
        }
        log("report written");
        if (mc.level != null) mc.level.disconnect();
        mc.clearLevel(new GenericDirtMessageScreen(Component.literal("AFL vegetation perf")));
        mc.stop();
    }
}
