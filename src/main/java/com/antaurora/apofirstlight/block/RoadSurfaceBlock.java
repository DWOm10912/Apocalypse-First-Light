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
 * by a block model. A dynamic shape (RoadDamage#shape: a broken column's base and the asphalt left; the light methods are
 * constants, memory project-dynamic-shape-light-deadlock). Broken by pick mattock strikes only (boulder/PavementMining, road Mesh-ification P0, 2026-10-25,
 * docs/worldgen/road_mesh_damage_v1_design.md 6); its asphalt (the wearing course, RoadSurfaceQuery#asphalt, within this
 * block) comes off as asphalt chunks, one per ASPHALT_CHUNK_LITRES, and the same when anything else breaks it; never
 * itself, and nothing for the driveway's gravel or a town's concrete.
 */
public final class RoadSurfaceBlock extends Block {
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, 32);
    private static final VoxelShape[] SHAPES = new VoxelShape[32];

    static {
        for (int i = 0; i < 32; i++) SHAPES[i] = Block.box(0, 0, 0, 16, (i + 1) * 0.5, 16);
    }

    /** Litres of asphalt in one asphalt chunk (about 4.6 kg at 2.3 kg/L). */
    public static final double ASPHALT_CHUNK_LITRES = 2;

    public RoadSurfaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 32));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(LAYERS); }

    /** Its layers' box; in a column a strike has broken into, the base and the asphalt still there (RoadDamage#shape, P1). */
    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        VoxelShape broken = com.antaurora.apofirstlight.road.RoadDamage.shape(l, p);
        return broken != null ? broken : SHAPES[s.getValue(LAYERS) - 1];
    }

    @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return getShape(s, l, p, c); }

    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }

    @Override
    public java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder b) {
        net.minecraft.world.phys.Vec3 o = b.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);
        return o == null ? java.util.List.of() : chunks(asphaltLitres(b.getLevel(), BlockPos.containing(o)));
    }

    /** Litres of the wearing course within the block at pos (server side; 0 off a paved road or before the network is built). */
    public static double asphaltLitres(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        var n = com.antaurora.apofirstlight.worldgen.network.RoadNetworkRuntime.bound(level);
        if (n == null) return 0;
        int y = pos.getY();
        double[] a = com.antaurora.apofirstlight.worldgen.network.road.RoadSurfaceQuery.asphalt(n.works(), pos.getX() + 0.5, pos.getZ() + 0.5, y + 1);
        double whole = a == null ? 0 : 1000 * Math.max(0, Math.min(a[0], y + 1) - Math.max(a[0] - a[1], y));
        // what strikes already took (P1)
        return whole * com.antaurora.apofirstlight.road.RoadDamage.left(com.antaurora.apofirstlight.road.RoadDamage.col(level, pos.getX(), pos.getZ()));
    }

    @Override
    public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!next.is(this)) com.antaurora.apofirstlight.road.RoadDamage.removed(level, pos);
        super.onRemove(state, level, pos, next, moved);
    }

    /** Asphalt chunks for so many litres of asphalt, in full stacks. */
    public static java.util.List<net.minecraft.world.item.ItemStack> chunks(double litres) {
        int count = (int) Math.round(litres / ASPHALT_CHUNK_LITRES);
        java.util.List<net.minecraft.world.item.ItemStack> out = new java.util.ArrayList<>();
        net.minecraft.world.item.Item item = com.antaurora.apofirstlight.registry.AflItems.ASPHALT_CHUNK.get();
        while (count > 0) {
            int k = Math.min(count, item.getMaxStackSize());
            out.add(new net.minecraft.world.item.ItemStack(item, k));
            count -= k;
        }
        return out;
    }

    @Override public int getLightBlock(BlockState s, BlockGetter l, BlockPos p) { return 0; }

    @Override public float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) { return 1; }

    @Override public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) { return true; }

    @Override public boolean useShapeForLightOcclusion(BlockState s) { return false; }
}
