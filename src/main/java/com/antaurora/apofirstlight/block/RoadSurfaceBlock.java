package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The collision of a generated road's pavement (`apocalypse_firstlight:road_surface`, C3.2;
 * docs/worldgen/federal_highways_c3.md, 10): an invisible block whose top is 1..32 thirty-seconds of a block high, placed
 * by the chunk pass so that every column's top matches the pavement mesh to about 3 cm (the R4 sample's collision,
 * formalised from the dev-only dev_highway_surface). Vanilla collision: walking, mobs and vehicles stand on ordinary
 * axis-aligned boxes, the same on the server and every client; no physics patch. Drawn by the client's road mesh, not
 * by a block model. Mined like the asphalt under it (pickaxe, stone tier); drops nothing (the asphalt below drops).
 */
public final class RoadSurfaceBlock extends Block {
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, 32);
    private static final VoxelShape[] SHAPES = new VoxelShape[32];

    static {
        for (int i = 0; i < 32; i++) SHAPES[i] = Block.box(0, 0, 0, 16, (i + 1) * 0.5, 16);
    }

    public RoadSurfaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 32));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(LAYERS); }

    @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPES[s.getValue(LAYERS) - 1]; }

    @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return getShape(s, l, p, c); }

    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }

    @Override public int getLightBlock(BlockState s, BlockGetter l, BlockPos p) { return 0; }

    @Override public float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) { return 1; }

    @Override public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) { return true; }

    @Override public boolean useShapeForLightOcclusion(BlockState s) { return true; }
}
