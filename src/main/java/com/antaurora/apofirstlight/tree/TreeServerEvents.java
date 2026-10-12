package com.antaurora.apofirstlight.tree;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mesh Trees P4 (docs/rendering/mesh_trees_p4_formal.md, D/F/K): the server-side lifecycle of the trees in every world.
 * <ul>
 * <li>The server's tree index ({@link TreeIndex#SERVER}): the overworld's roots, per loaded chunk; collision and felling
 * read it. Roots that appear or go update it through {@link TreeNetwork#rootChanged}.</li>
 * <li>The forest's world record ({@value #DATA}): the forest version the world grows (written when the server first
 * starts it with the mesh forest on), so a later algorithm change can tell an existing world from a new one.</li>
 * <li>Old ids: the development ids of P0-P3 test worlds (dev_tree_root, dev_falling_tree, ...) are remapped to the formal
 * ones when such a world loads (Forge's missing-mappings remap: the old name resolves to the new block, the block
 * states keep their properties). No data fixer; chunks are not rewritten until they save normally.</li>
 * <li>Hooks for diagnostics: a felled tree's cells may be given back what a development command replaced there.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
public final class TreeServerEvents {
    static final String DATA = "afl_mesh_forest";

    private TreeServerEvents() {}

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (!TreeBlocks.ready() || !(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)
                || !(event.getChunk() instanceof LevelChunk chunk)) return;
        TreeIndex.SERVER.load(chunk);
    }

    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(Level.OVERWORLD)) TreeIndex.SERVER.unload(event.getChunk().getPos());
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        if (overworld == null || !ForestGate.enabled()) return;
        var data = overworld.getDataStorage().computeIfAbsent(ForestRecord::load, ForestRecord::new, DATA);
        if (data.version == 0) {
            data.version = ForestPlan.VERSION;
            data.setDirty();
            ForestGate.LOG.info("[AFL mesh forest] this world grows mesh forest version {} in the chunks it generates from now on", data.version);
        } else if (data.version != ForestPlan.VERSION) {
            ForestGate.LOG.warn("[AFL mesh forest] this world recorded mesh forest version {}, the running one is {}", data.version, ForestPlan.VERSION);
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        TreeIndex.SERVER.clear();
        ForestGate.stopped();
    }

    /** The forest version a world grows. */
    static final class ForestRecord extends SavedData {
        int version;

        static ForestRecord load(CompoundTag tag) {
            var r = new ForestRecord();
            r.version = tag.getInt("version");
            return r;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putInt("version", version);
            return tag;
        }
    }

    // ---------------------------------------------------------------------------------------------- old ids

    /** P0-P3 development ids and their formal names (P4). */
    static final Map<String, String> BLOCKS = Map.ofEntries(
            Map.entry("dev_tree_root", "tree_root"), Map.entry("dev_tree_wood", "tree_wood"), Map.entry("dev_tree_stump", "tree_stump"),
            Map.entry("dev_tree_foliage", "white_oak_foliage"), Map.entry("dev_tree_canopy", "white_oak_canopy"),
            Map.entry("dev_white_pine_foliage", "white_pine_foliage"), Map.entry("dev_white_pine_canopy", "white_pine_canopy"),
            Map.entry("dev_red_oak_foliage", "red_oak_foliage"), Map.entry("dev_red_oak_canopy", "red_oak_canopy"),
            Map.entry("dev_shagbark_hickory_foliage", "shagbark_hickory_foliage"), Map.entry("dev_shagbark_hickory_canopy", "shagbark_hickory_canopy"),
            Map.entry("dev_red_maple_foliage", "red_maple_foliage"), Map.entry("dev_red_maple_canopy", "red_maple_canopy"),
            Map.entry("dev_eastern_hemlock_foliage", "eastern_hemlock_foliage"), Map.entry("dev_eastern_hemlock_canopy", "eastern_hemlock_canopy"));
    static final Map<String, String> OTHER = Map.of("dev_falling_tree", "falling_tree", "dev_mesh_forest", "mesh_forest");

    @SubscribeEvent
    public static void missing(MissingMappingsEvent event) {
        for (var m : event.getMappings(Registries.BLOCK, "apocalypse_firstlight")) {
            String to = BLOCKS.get(m.getKey().getPath());
            var target = to == null ? null : ForgeRegistries.BLOCKS.getValue(new ResourceLocation("apocalypse_firstlight", to));
            if (target != null) m.remap(target);
        }
        for (var m : event.getMappings(Registries.ENTITY_TYPE, "apocalypse_firstlight")) {
            String to = OTHER.get(m.getKey().getPath());
            var target = to == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("apocalypse_firstlight", to));
            if (target != null) m.remap(target);
        }
        for (var m : event.getMappings(Registries.FEATURE, "apocalypse_firstlight")) {
            String to = OTHER.get(m.getKey().getPath());
            var target = to == null ? null : ForgeRegistries.FEATURES.getValue(new ResourceLocation("apocalypse_firstlight", to));
            if (target != null) m.remap(target);
        }
        for (var m : event.getMappings(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, "apocalypse_firstlight")) {
            String to = OTHER.get(m.getKey().getPath());
            var target = to == null ? null : ForgeRegistries.BIOME_MODIFIER_SERIALIZERS.get().getValue(new ResourceLocation("apocalypse_firstlight", to));
            if (target != null) m.remap(target);
        }
    }

    // ------------------------------------------------------------------------------------------------- hooks

    /** What a development command replaced at a felled tree's cells (position -> the state to give back), or empty. */
    public interface FelledHook {
        Map<Long, BlockState> felled(ServerLevel level, BlockPos root);
    }

    static final List<FelledHook> FELLED = new CopyOnWriteArrayList<>();

    /** Registers a hook (development diagnostics only; the formal felling needs none). */
    public static void onFelled(FelledHook hook) { FELLED.add(hook); }
}
