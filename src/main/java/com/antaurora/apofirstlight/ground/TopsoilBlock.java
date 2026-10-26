package com.antaurora.apofirstlight.ground;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Natural ground N1 (docs/worldgen/natural_ground_n1_design.md 4): topsoil, the one block in place of vanilla's grass
 * block, dirt, coarse dirt, podzol, mycelium and moss block. Grass is no block: it is what grows on the soil, so
 * {@link #COVER} only says what its top shows (turf, leaf litter, moss or bare soil); its sides and bottom are always soil,
 * so a wall or a pit's side never shows a strip of grass. The turf is tinted by the biome's grass colour.
 */
public class TopsoilBlock extends NaturalGroundBlock {
    public enum Cover implements StringRepresentable {
        GRASS("grass"), LITTER("litter"), MOSS("moss"), BARE("bare");

        private final String name;

        Cover(String name) { this.name = name; }

        @Override
        public String getSerializedName() { return name; }
    }

    public static final EnumProperty<Cover> COVER = EnumProperty.create("cover", Cover.class);

    public TopsoilBlock(Properties properties) {
        super("topsoil", properties);
        registerDefaultState(defaultBlockState().setValue(COVER, Cover.GRASS));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COVER);
    }

    /** The cover's own footsteps: turf and litter rustle, moss is soft, bare soil sounds as vanilla dirt. */
    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, Entity entity) {
        return switch (state.getValue(COVER)) {
            case GRASS, LITTER -> SoundType.GRASS;
            case MOSS -> SoundType.MOSS;
            case BARE -> SoundType.GRAVEL;
        };
    }

    /** The same soil with its top bare (a dug column, Earthwork#bare). */
    public BlockState bare() {
        return defaultBlockState().setValue(COVER, Cover.BARE);
    }
}
