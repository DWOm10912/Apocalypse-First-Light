package com.antaurora.apofirstlight.ground;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Natural ground N1 (docs/worldgen/natural_ground_n1_design.md 3): gravel, one material in two looks by where it lies:
 * rounded river gravel in stream beds, angular scree in the mountains. Unlike vanilla's it does not fall: loose ground
 * moves by the earthwork's angles of repose.
 */
public class GravelGroundBlock extends NaturalGroundBlock {
    public enum Kind implements StringRepresentable {
        ROUND("round"), ANGULAR("angular");

        private final String name;

        Kind(String name) { this.name = name; }

        @Override
        public String getSerializedName() { return name; }
    }

    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);

    public GravelGroundBlock(Properties properties) {
        super("gravel", properties);
        registerDefaultState(stateDefinition.any().setValue(KIND, Kind.ROUND));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND);
    }
}
