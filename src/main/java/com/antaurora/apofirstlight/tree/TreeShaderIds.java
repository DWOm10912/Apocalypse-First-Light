package com.antaurora.apofirstlight.tree;

import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Mesh Trees P0-A (docs/rendering/mesh_trees_p0a.md, D): the shader pack sees the sample's crown as oak leaves and its
 * wood as an oak log.
 * <p>
 * Oculus 1.8 gives every chunk vertex the material ID of the block state being meshed: per block, from the map it builds
 * from the pack's block.properties ({@code WorldRenderingSettings#getBlockStateIds}; the Embeddium path sets it in
 * MixinChunkRenderRebuildTask right before the block's model is fetched). A mod block the pack does not list gets -1,
 * so Sundial neither waves it nor treats it as leaves. This copies the pack's own ID of {@code oak_leaves} (Sundial Lite:
 * 16896, wave type "leaves"; Complementary: 10009) to the white oak crown states, of {@code spruce_leaves} to the white
 * pine crown states, and of {@code oak_log} to the root and wood states, whatever the pack is; a pack that lists the AFL blocks itself keeps its entries. Voxy reads the same
 * map for its distant terrain.
 * <p>
 * By reflection (AFL does not compile against Oculus; without Oculus nothing happens, and a dedicated server never loads
 * this client class): when the pack's map is new (a pack was loaded,
 * a world joined), a copy with the aliases replaces it and the chunks are rebuilt once, so no chunk builder ever reads a
 * map being changed. The formal version would add the aliases inside {@code setBlockStateIds} (a mixin), before any chunk
 * is meshed: no second rebuild. No third-party file is changed.
 */
final class TreeShaderIds {
    private static final String SETTINGS = "net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings";
    private static boolean initialized;
    private static Object settings;
    private static Method get, set, clearReload;
    private static Object lastMap;
    private static String status = "not checked yet";
    static int leafId = -1, needleId = -1, logId = -1;

    private TreeShaderIds() {}

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> type = Class.forName(SETTINGS);
            settings = type.getField("INSTANCE").get(null);
            get = type.getMethod("getBlockStateIds");
            set = type.getMethod("setBlockStateIds", Object2IntMap.class);
            clearReload = type.getMethod("clearReloadRequired");
        } catch (ReflectiveOperationException | LinkageError e) {
            get = null;
        }
    }

    @SuppressWarnings("unchecked")
    static void tick() {
        if (!ModList.get().isLoaded("oculus")) { status = "Oculus not loaded"; return; }
        init();
        if (get == null) { status = "Oculus block ID map not found (another Oculus version?): crowns do not wave"; return; }
        try {
            Object map = get.invoke(settings);
            if (map == null) { status = "no shader pack (no block ID map)"; lastMap = null; return; }
            if (map == lastMap) return;
            var ids = (Object2IntMap<BlockState>) map;
            leafId = ids.getOrDefault(Blocks.OAK_LEAVES.defaultBlockState(), -1);
            needleId = ids.getOrDefault(Blocks.SPRUCE_LEAVES.defaultBlockState(), leafId);
            logId = ids.getOrDefault(Blocks.OAK_LOG.defaultBlockState(), -1);
            var copy = new Object2IntOpenHashMap<>(ids);
            copy.defaultReturnValue(ids.defaultReturnValue());
            int added = alias(copy, TreeBlocks.root(), logId) + alias(copy, TreeBlocks.wood(), logId);
            for (var crown : TreeBlocks.crowns())
                added += alias(copy, crown, crown.species.conifer ? needleId : leafId);
            if (added == 0) {
                lastMap = map;
                status = "pack " + AflShaderCompat.currentPackLabel() + " defines no oak leaves / log ID (or lists the sample itself): nothing to alias";
                return;
            }
            set.invoke(settings, copy);
            clearReload.invoke(settings);
            lastMap = copy;
            var renderer = Minecraft.getInstance().levelRenderer;
            if (renderer != null) renderer.allChanged();
            status = "pack " + AflShaderCompat.currentPackLabel() + ": broadleaf crowns use the oak leaves ID " + leafId + ", conifer crowns the spruce leaves ID "
                    + needleId + ", wood the oak log ID " + logId + " (" + added + " states aliased, chunks rebuilt once)";
        } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
            get = null;
            status = "aliasing failed (" + e + "): crowns do not wave";
        }
    }

    private static int alias(Object2IntOpenHashMap<BlockState> ids, Block block, int id) {
        if (id < 0) return 0;
        int n = 0;
        for (BlockState s : block.getStateDefinition().getPossibleStates()) if (!ids.containsKey(s)) { ids.put(s, id); n++; }
        return n;
    }

    static String describe() {
        return status;
    }
}
