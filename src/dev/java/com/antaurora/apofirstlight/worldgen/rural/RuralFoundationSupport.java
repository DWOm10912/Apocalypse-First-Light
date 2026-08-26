package com.antaurora.apofirstlight.worldgen.rural;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared template-derived foundation contract for Natural Rural lots.
 *
 * <p>The expensive palette/block scan is deliberately a template-metadata operation. It is
 * performed once per Rural template resource lifecycle and never from the candidate or chunk
 * validation hot path. Candidate evaluation only rotates and translates immutable local columns.
 * The cache is bounded by the fixed Rural structure pool and is cleared by server resource reload.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RuralFoundationSupport {
    private static final Set<ResourceLocation> MANAGED_TEMPLATE_IDS = RuralStructurePool.definitions().stream()
            .map(RuralStructurePool.Definition::id)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final ConcurrentMap<ResourceLocation, TemplateSupportMetadata> TEMPLATE_METADATA =
            new ConcurrentHashMap<>();

    private static final AtomicLong SUPPORT_MASK_BUILD_COUNT = new AtomicLong();
    private static final AtomicLong SUPPORT_MASK_BUILD_TOTAL_NANOS = new AtomicLong();
    private static final AtomicLong SUPPORT_VALIDATION_CALLS = new AtomicLong();
    private static final AtomicLong SUPPORT_VALIDATION_TOTAL_NANOS = new AtomicLong();
    private static final AtomicLong TEMPLATE_SAVE_COUNT = new AtomicLong();
    private static final AtomicLong TEMPLATE_SAVE_TOTAL_NANOS = new AtomicLong();
    private static final AtomicLong SUPPORT_MASK_CACHE_HIT = new AtomicLong();
    private static final AtomicLong SUPPORT_MASK_CACHE_MISS = new AtomicLong();

    private RuralFoundationSupport() {
    }

    /** Returns the cached immutable template-local support metadata for one Rural definition. */
    public static TemplateSupportMetadata metadata(RuralStructurePool.Definition definition,
                                                   StructureTemplate template) {
        return metadata(definition.id(), template, definition.groundAnchorOffsetY());
    }

    private static TemplateSupportMetadata metadata(ResourceLocation id, StructureTemplate template,
                                                    int groundAnchorOffsetY) {
        if (!MANAGED_TEMPLATE_IDS.contains(id)) {
            throw new IllegalArgumentException("Unsupported Rural foundation template: " + id);
        }
        TemplateSupportMetadata cached = TEMPLATE_METADATA.get(id);
        if (cached != null) {
            if (cached.groundAnchorOffsetY() != groundAnchorOffsetY) {
                throw new IllegalStateException("Rural foundation anchor changed for " + id);
            }
            SUPPORT_MASK_CACHE_HIT.incrementAndGet();
            return cached;
        }

        SUPPORT_MASK_CACHE_MISS.incrementAndGet();
        return TEMPLATE_METADATA.computeIfAbsent(id, ignored -> buildMetadata(template, groundAnchorOffsetY));
    }

    /**
     * Evaluates terrain against already-parsed template-local columns. Rotation is only an
     * integer coordinate transform; no NBT, palette, or template scan occurs here.
     */
    public static Evaluation evaluate(RuralTerrainSource terrain, TemplateSupportMetadata metadata,
                                       StructurePlaceSettings settings, BlockPos origin) {
        long start = System.nanoTime();
        SUPPORT_VALIDATION_CALLS.incrementAndGet();
        SupportMask mask = toWorldMask(metadata, settings, origin);
        Evaluation result;
        if (mask.columns().isEmpty()) {
            result = Evaluation.invalid(mask, "FOUNDATION_SUPPORT_MASK_EMPTY");
        } else {
            int minSurface = Integer.MAX_VALUE;
            int maxSurface = Integer.MIN_VALUE;
            int maxFill = 0;
            int maxCut = 0;
            int fillBlocks = 0;
            int cutBlocks = 0;
            int invalidColumns = 0;
            for (BlockPos support : mask.columns()) {
                RuralTerrainSampler.Sample sample = terrain.sample(support.getX(), support.getZ());
                if (!sample.valid() || sample.water()) {
                    invalidColumns++;
                    continue;
                }
                int surfaceY = sample.surfaceY();
                minSurface = Math.min(minSurface, surfaceY);
                maxSurface = Math.max(maxSurface, surfaceY);
                int fill = Math.max(0, support.getY() - surfaceY);
                int cut = Math.max(0, surfaceY - support.getY());
                maxFill = Math.max(maxFill, fill);
                maxCut = Math.max(maxCut, cut);
                fillBlocks += fill;
                cutBlocks += cut;
            }
            if (invalidColumns > 0) {
                result = new Evaluation(mask, false, "FOUNDATION_SUPPORT_INVALID_GROUND", minSurface, maxSurface,
                        maxFill, maxCut, fillBlocks, cutBlocks, invalidColumns);
            } else {
                boolean withinBudget = maxFill <= RuralGenerator.MAX_LOT_CORRECTION
                        && maxCut <= RuralGenerator.MAX_LOT_CORRECTION;
                result = new Evaluation(mask, withinBudget,
                        withinBudget ? "OK" : "FOUNDATION_SUPPORT_EXCEEDS_BUDGET",
                        minSurface, maxSurface, maxFill, maxCut, fillBlocks, cutBlocks, 0);
            }
        }
        SUPPORT_VALIDATION_TOTAL_NANOS.addAndGet(System.nanoTime() - start);
        return result;
    }

    /** Converts the cached local support columns without probing terrain. */
    public static SupportMask worldMask(TemplateSupportMetadata metadata, StructurePlaceSettings settings,
                                        BlockPos origin) {
        return toWorldMask(metadata, settings, origin);
    }

    private static TemplateSupportMetadata buildMetadata(StructureTemplate template, int groundAnchorOffsetY) {
        long start = System.nanoTime();
        SUPPORT_MASK_BUILD_COUNT.incrementAndGet();
        long saveStart = System.nanoTime();
        CompoundTag saved = template.save(new CompoundTag());
        TEMPLATE_SAVE_COUNT.incrementAndGet();
        TEMPLATE_SAVE_TOTAL_NANOS.addAndGet(System.nanoTime() - saveStart);

        ListTag palette = saved.getList(StructureTemplate.PALETTE_TAG, Tag.TAG_COMPOUND);
        if (palette.isEmpty() && saved.contains("palettes", Tag.TAG_LIST)) {
            ListTag palettes = saved.getList("palettes", Tag.TAG_LIST);
            if (!palettes.isEmpty()) palette = palettes.getList(0);
        }
        ListTag blocks = saved.getList(StructureTemplate.BLOCKS_TAG, Tag.TAG_COMPOUND);
        Map<Long, BlockPos> columns = new LinkedHashMap<>();
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag blockTag = blocks.getCompound(index);
            int[] coordinates = blockTag.getIntArray(StructureTemplate.BLOCK_TAG_POS);
            if (coordinates.length < 3 || coordinates[1] != groundAnchorOffsetY) continue;
            int stateIndex = blockTag.getInt(StructureTemplate.BLOCK_TAG_STATE);
            if (stateIndex < 0 || stateIndex >= palette.size()) continue;
            BlockState state;
            try {
                state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(stateIndex));
            } catch (RuntimeException ignored) {
                continue;
            }
            if (!isSupportState(state)) continue;
            BlockPos local = new BlockPos(coordinates[0], coordinates[1], coordinates[2]);
            columns.putIfAbsent(BlockPos.asLong(local.getX(), 0, local.getZ()), local);
        }
        TemplateSupportMetadata metadata = new TemplateSupportMetadata(groundAnchorOffsetY,
                List.copyOf(columns.values()));
        SUPPORT_MASK_BUILD_TOTAL_NANOS.addAndGet(System.nanoTime() - start);
        return metadata;
    }

    private static SupportMask toWorldMask(TemplateSupportMetadata metadata, StructurePlaceSettings settings,
                                           BlockPos origin) {
        List<BlockPos> columns = new ArrayList<>(metadata.localColumns().size());
        for (BlockPos local : metadata.localColumns()) {
            BlockPos relative = StructureTemplate.calculateRelativePosition(settings, local);
            BlockPos world = origin.offset(relative);
            columns.add(world);
        }
        return new SupportMask(List.copyOf(columns));
    }

    private static boolean isSupportState(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) return false;
        if (state.is(net.minecraft.tags.BlockTags.LEAVES) || state.is(net.minecraft.tags.BlockTags.LOGS)
                || state.is(net.minecraft.tags.BlockTags.FLOWERS)) return false;
        if (state.getBlock() instanceof BushBlock || state.getBlock() instanceof FlowerBlock
                || state.getBlock() instanceof VineBlock || state.getBlock() instanceof FenceBlock
                || state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof WallBlock
                || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof CarpetBlock || state.getBlock() instanceof TorchBlock
                || state.getBlock() instanceof LanternBlock || state.getBlock() instanceof SignBlock) return false;
        return !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO,
                net.minecraft.world.phys.shapes.CollisionContext.empty()).isEmpty();
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void ignored, ResourceManager resourceManager, ProfilerFiller profiler) {
                TEMPLATE_METADATA.clear();
                ApocalypseFirstLight.LOGGER.debug("[AFL RURAL NATURAL][FOUNDATION_SUPPORT_CACHE_CLEAR] templates={}",
                        MANAGED_TEMPLATE_IDS.size());
            }
        });
    }

    public static DiagnosticsSnapshot diagnostics() {
        return new DiagnosticsSnapshot(SUPPORT_MASK_BUILD_COUNT.get(), SUPPORT_MASK_BUILD_TOTAL_NANOS.get(),
                SUPPORT_VALIDATION_CALLS.get(), SUPPORT_VALIDATION_TOTAL_NANOS.get(), TEMPLATE_SAVE_COUNT.get(),
                TEMPLATE_SAVE_TOTAL_NANOS.get(), SUPPORT_MASK_CACHE_HIT.get(), SUPPORT_MASK_CACHE_MISS.get());
    }

    public static DiagnosticsSnapshot delta(DiagnosticsSnapshot before) {
        return diagnostics().minus(before);
    }

    public record TemplateSupportMetadata(int groundAnchorOffsetY, List<BlockPos> localColumns) {
        public TemplateSupportMetadata {
            localColumns = List.copyOf(localColumns);
        }
    }

    public record SupportMask(List<BlockPos> columns) {
        public SupportMask {
            columns = List.copyOf(columns);
        }
    }

    public record Evaluation(SupportMask mask, boolean withinBudget, String reason,
                             int minNaturalSurface, int maxNaturalSurface, int maxFillDepth,
                             int maxCutDepth, int fillBlocks, int cutBlocks, int invalidColumns) {
        private static Evaluation invalid(SupportMask mask, String reason) {
            return new Evaluation(mask, false, reason, Integer.MAX_VALUE, Integer.MIN_VALUE,
                    Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0, 0);
        }
    }

    public record DiagnosticsSnapshot(long supportMaskBuildCount, long supportMaskBuildTotalNanos,
                                      long supportValidationCalls, long supportValidationTotalNanos,
                                      long templateSaveCount, long templateSaveTotalNanos,
                                      long supportMaskCacheHit, long supportMaskCacheMiss) {
        private DiagnosticsSnapshot minus(DiagnosticsSnapshot before) {
            return new DiagnosticsSnapshot(supportMaskBuildCount - before.supportMaskBuildCount,
                    supportMaskBuildTotalNanos - before.supportMaskBuildTotalNanos,
                    supportValidationCalls - before.supportValidationCalls,
                    supportValidationTotalNanos - before.supportValidationTotalNanos,
                    templateSaveCount - before.templateSaveCount,
                    templateSaveTotalNanos - before.templateSaveTotalNanos,
                    supportMaskCacheHit - before.supportMaskCacheHit,
                    supportMaskCacheMiss - before.supportMaskCacheMiss);
        }

        public double supportMaskBuildMs() { return supportMaskBuildTotalNanos / 1_000_000.0D; }
        public double supportValidationMs() { return supportValidationTotalNanos / 1_000_000.0D; }
        public double templateSaveMs() { return templateSaveTotalNanos / 1_000_000.0D; }
    }
}
