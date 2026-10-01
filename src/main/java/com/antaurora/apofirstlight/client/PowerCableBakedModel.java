package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.block.PowerCableBlock;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Power Cable V2 block model: assembles one cable block from the baked OBJ pieces of tools/build-power-cable-v2.mjs.
 * The key holds, per direction, NONE / CABLE / PORT (2 bits each), then the pipe clamp side (3 bits, 7 = none); ports
 * (a machine's power port on that side) and clamp faces are read from the neighbours while the chunk is meshed.
 * Selection: none -> box; one -> end; two opposite -> two halves + band, or, every third block along the run, + a clamp
 * toward a solid face next to it (floor first, then walls, then ceiling); two perpendicular cables -> bend; else box + arms.
 */
public final class PowerCableBakedModel implements IDynamicBakedModel {
    static final ModelProperty<Integer> LINKS = new ModelProperty<>();
    private static final Direction[] ORDER = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final Direction[] CLAMP_PREFERENCE = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP};
    private static final int CLAMP_SHIFT = 12;
    private static final int NO_CLAMP = 7;
    /** One clamp every this many blocks along a run (by world coordinate along the run, so spacing stays even). */
    private static final int CLAMP_SPACING = 3;
    private static final ChunkRenderTypeSet SOLID = ChunkRenderTypeSet.of(RenderType.solid());

    private final Map<String, BakedModel> pieces;
    private final BakedModel particle;
    private final Map<Integer, List<BakedQuad>> cache = new ConcurrentHashMap<>();

    PowerCableBakedModel(Map<String, BakedModel> pieces) {
        this.pieces = pieces;
        this.particle = pieces.get("box");
    }

    static int key(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        int key = 0;
        int connected = 0;
        Direction first = null;
        for (Direction direction : ORDER) {
            PowerCableBlock.Link link = PowerCableBlock.link(level, pos, state, direction);
            key |= link.ordinal() << (2 * direction.ordinal());
            if (link != PowerCableBlock.Link.NONE) {
                connected++;
                if (first == null) first = direction;
            }
        }
        int clamp = NO_CLAMP;
        if (connected == 2 && state.getValue(PowerCableBlock.PROPERTY_BY_DIRECTION.get(first.getOpposite()))
                && Math.floorMod(first.getAxis().choose(pos.getX(), pos.getY(), pos.getZ()), CLAMP_SPACING) == 0) {
            for (Direction side : CLAMP_PREFERENCE) {
                if (side.getAxis() == first.getAxis()) continue;
                BlockPos neighbor = pos.relative(side);
                if (level.getBlockState(neighbor).isFaceSturdy(level, neighbor, side.getOpposite())) {
                    clamp = side.ordinal();
                    break;
                }
            }
        }
        return key | clamp << CLAMP_SHIFT;
    }

    /** Without level data (e.g. a lone state lookup) every connected side counts as a cable. */
    private static int keyFromState(BlockState state) {
        int key = 0;
        for (Direction direction : ORDER) {
            if (state.getValue(PowerCableBlock.PROPERTY_BY_DIRECTION.get(direction))) key |= 1 << (2 * direction.ordinal());
        }
        return key | NO_CLAMP << CLAMP_SHIFT;
    }

    private static PowerCableBlock.Link linkOf(int key, Direction direction) {
        return PowerCableBlock.Link.values()[(key >> (2 * direction.ordinal())) & 3];
    }

    private static String suffix(PowerCableBlock.Link link) {
        return link == PowerCableBlock.Link.PORT ? "plug" : "cable";
    }

    private List<BakedQuad> assemble(int key) {
        List<Direction> connected = new ArrayList<>();
        for (Direction direction : ORDER) if (linkOf(key, direction) != PowerCableBlock.Link.NONE) connected.add(direction);
        List<String> names = new ArrayList<>();
        if (connected.isEmpty()) {
            names.add("box");
        } else if (connected.size() == 1) {
            Direction d = connected.get(0);
            names.add("end_" + suffix(linkOf(key, d)) + "_" + d.getName());
        } else if (connected.size() == 2 && connected.get(0).getOpposite() == connected.get(1)) {
            for (Direction d : connected) names.add("half_" + suffix(linkOf(key, d)) + "_" + d.getName());
            int clamp = (key >> CLAMP_SHIFT) & 7;
            names.add(clamp == NO_CLAMP ? "band_" + connected.get(0).getAxis().getName()
                    : "clamp_" + connected.get(0).getAxis().getName() + "_" + Direction.values()[clamp].getName());
        } else if (connected.size() == 2 && linkOf(key, connected.get(0)) == PowerCableBlock.Link.CABLE
                && linkOf(key, connected.get(1)) == PowerCableBlock.Link.CABLE) {
            names.add("bend_" + connected.get(0).getName() + "_" + connected.get(1).getName());
        } else {
            names.add("box");
            for (Direction d : connected) names.add("arm_" + suffix(linkOf(key, d)) + "_" + d.getName());
        }
        List<BakedQuad> quads = new ArrayList<>();
        RandomSource random = RandomSource.create(42L);
        for (String name : names) {
            BakedModel piece = pieces.get(name);
            if (piece != null) quads.addAll(piece.getQuads(null, null, random, ModelData.EMPTY, null));
        }
        return List.copyOf(quads);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource random,
                                             @NotNull ModelData data, @Nullable RenderType renderType) {
        if (side != null || state == null) return List.of();
        Integer key = data.get(LINKS);
        int resolved = key != null ? key : keyFromState(state);
        return cache.computeIfAbsent(resolved, this::assemble);
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state,
                                           @NotNull ModelData modelData) {
        return ModelData.builder().with(LINKS, key(level, pos, state)).build();
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource random, @NotNull ModelData data) {
        return SOLID;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon() {
        return particle.getParticleIcon();
    }

    @Override
    public @NotNull ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
