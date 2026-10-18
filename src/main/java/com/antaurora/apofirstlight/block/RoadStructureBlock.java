package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.worldgen.network.road.BridgeMesh;
import com.antaurora.apofirstlight.worldgen.network.road.RoadCells;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The collision of a generated road's structures (`apocalypse_firstlight:road_structure`, C3.2;
 * docs/worldgen/federal_highways_c3.md, 10.11): bridge decks, parapets, abutments and wing walls. Invisible like
 * road_surface (the client draws the structure as a mesh, BridgeMesh). It takes one level of light and lets no sky light
 * straight down, so the ground under a deck is a little darker; not more: a neighbour's face beside it is lit from its
 * cell (it never occludes one, the mesh does not cover every block face), and a cell that holds all light drew those
 * faces black (2026-10-15). Mined like reinforced concrete (pickaxe, diamond tier); drops nothing.
 * <p>
 * Its collision is the bridge's mesh within the block (BridgeMesh.solid on 1/8 block columns, 1/32 heights; 2026-10-15):
 * a straight wall or parapet on a skewed crossing cuts the block grid, and a whole block of invisible collision stood up
 * to 0.7 m beside the mesh. The shape needs the road network, which the server (RoadNetworkRuntime) and the client
 * (RoadClient) hand over with {@link #works}; without it the block is its layers' box. The top block's layers still cap
 * it (they mark an intact cell for the client's damage check). Where the mesh has nothing in the block nothing collides.
 * <p>
 * Rays (the crosshair, its outline, bullets) do not use these boxes: the block hits on the drawn triangles inside its
 * cell (meshhit/RoadStructureHits, docs/rendering/mesh_hit_runtime_v1.md 0.9). The shape here is only their fallback
 * (no mesh drawn in the cell, the chunk's mesh not built yet), with the layers' box where the mesh has nothing, so the
 * block can still be picked and mined.
 */
public final class RoadStructureBlock extends Block implements SimpleWaterloggedBlock {
    public static final IntegerProperty LAYERS = RoadSurfaceBlock.LAYERS;
    /** Structure generated in water (a pier, an abutment, a culvert's wall in its stream) keeps the water (group D). */
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape[] SHAPES = new VoxelShape[32];
    private static final int CACHE = 200_000;
    /** Structure blocks followed down from a cell's top to find its foot. */
    public static final int MAX_WALK = 64;
    /** Blocks a ground probe (BridgeMesh.Expect#probe) looks down from its start for the ground. */
    public static final int PROBE_WALK = 96;

    static {
        for (int i = 0; i < 32; i++) SHAPES[i] = Block.box(0, 0, 0, 16, (i + 1) * 0.5, 16);
    }

    private record Holder(RoadWorks works, ConcurrentHashMap<Long, VoxelShape> shapes) {}

    private static volatile Holder holder;
    /** Cached "the mesh has nothing in this block". */
    private static final VoxelShape NOTHING = Block.box(0, 0, 0, 0.01, 0.01, 0.01);

    /** The road network the shapes follow (null: none); a new one drops the cached shapes. */
    public static void works(RoadWorks works) {
        Holder h = holder;
        if (h != null && h.works() == works) return;
        holder = works == null ? null : new Holder(works, new ConcurrentHashMap<>());
    }

    /** The road network the shapes follow, or null. */
    @Nullable
    public static RoadWorks boundWorks() {
        Holder h = holder;
        return h == null ? null : h.works();
    }

    /**
     * The structure cells of chunk (cx, cz) and its ring as they are in the world (RoadClient's mesh, the server's hit
     * meshes): from the expected top down at most 3 blocks to the first structure block (road_surface or road_structure),
     * then down to the lowest one of the run. A ground probe looks down from its start for the first block that blocks
     * motion and is not road structure (water does not stop it): its top face is the probe's foot.
     */
    public static Map<Integer, BridgeMesh.Found> scan(BlockGetter level, int cx, int cz, Map<Integer, BridgeMesh.Expect> expect) {
        if (expect == null || expect.isEmpty()) return Map.of();
        Map<Integer, BridgeMesh.Found> out = new HashMap<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (BridgeMesh.Expect e : expect.values()) {
            int x = cx * 16 + RoadCells.lx(e.index()), z = cz * 16 + RoadCells.lz(e.index());
            if (e.probe()) {
                for (int y = e.top(); y >= e.top() - PROBE_WALK; y--) {
                    BlockState s = level.getBlockState(p.set(x, y, z));
                    if (layers(s) > 0 || !s.blocksMotion()) continue;
                    out.put(e.index(), new BridgeMesh.Found(y, false, 0, y + 1));
                    break;
                }
                continue;
            }
            for (int y = e.top(); y >= e.top() - 3; y--) {
                BlockState s = level.getBlockState(p.set(x, y, z));
                int l = layers(s);
                if (l == 0) continue;
                int foot = y;
                for (int n = 0; n < MAX_WALK && layers(level.getBlockState(p.set(x, foot - 1, z))) > 0; n++) foot--;
                out.put(e.index(), new BridgeMesh.Found(y, s.is(AflBlocks.ROAD_SURFACE.get()), l, foot));
                break;
            }
        }
        return out;
    }

    /** Layers of a road collision or structure block, 0 for anything else. */
    public static int layers(BlockState s) {
        if (s.is(AflBlocks.ROAD_SURFACE.get())) return s.getValue(RoadSurfaceBlock.LAYERS);
        if (s.is(AflBlocks.ROAD_STRUCTURE.get())) return s.getValue(LAYERS);
        return 0;
    }

    public RoadStructureBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 32).setValue(WATERLOGGED, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(LAYERS, WATERLOGGED); }

    @Override public FluidState getFluidState(BlockState s) {
        return s.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(s);
    }

    @Override public BlockState updateShape(BlockState s, Direction d, BlockState n, LevelAccessor level, BlockPos p, BlockPos np) {
        if (s.getValue(WATERLOGGED)) level.scheduleTick(p, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(s, d, n, level, p, np);
    }

    /**
     * The mesh's solid in this block, capped by the layers; null without a network or where the mesh has nothing. Built
     * once per position and layers (cached): the column boxes capped, rows with the same x run and heights merged along z,
     * joined unoptimised and optimised once. Shapes.or optimises after every box, which made a tunnel cell with a few
     * dozen boxes cost milliseconds; vanilla's ambient particle tick asks hundreds of new cells a tick when flying fast
     * through a tunnel (2026-10-16).
     */
    private static VoxelShape meshShape(Holder h, BlockState s, BlockPos p) {
        int layers = s.getValue(LAYERS);
        long key = p.asLong() * 33 + layers;
        VoxelShape shape = h.shapes().get(key);
        if (shape == null) {
            List<double[]> boxes = new ArrayList<>();
            for (double[] b : BridgeMesh.boxes(h.works(), p.getX(), p.getY(), p.getZ())) {
                double y1 = Math.min(b[4], layers / 32.0);
                if (y1 > b[1]) boxes.add(new double[]{b[0], b[1], b[2], b[3], y1, b[5]});
            }
            VoxelShape out = Shapes.empty();
            for (double[] b : mergeAlongZ(boxes)) out = Shapes.joinUnoptimized(out, Shapes.box(b[0], b[1], b[2], b[3], b[4], b[5]), BooleanOp.OR);
            shape = out.isEmpty() ? NOTHING : out.optimize();
            if (h.shapes().size() > CACHE) h.shapes().clear();
            h.shapes().put(key, shape);
        }
        return shape == NOTHING ? null : shape;
    }

    /** Boxes with the same x run and heights whose z ranges touch, merged (the rows of a uniform cell become one box). */
    public static List<double[]> mergeAlongZ(List<double[]> boxes) {
        boxes.sort(Comparator.<double[]>comparingDouble(b -> b[0]).thenComparingDouble(b -> b[3]).thenComparingDouble(b -> b[1])
                .thenComparingDouble(b -> b[4]).thenComparingDouble(b -> b[2]));
        List<double[]> out = new ArrayList<>();
        for (double[] b : boxes) {
            double[] last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last != null && last[0] == b[0] && last[3] == b[3] && last[1] == b[1] && last[4] == b[4] && last[5] == b[2]) last[5] = b[5];
            else out.add(b.clone());
        }
        return out;
    }

    /**
     * The collision a road_structure block with these layers has at p (a tunnel fixture that took over a lining cell keeps
     * it, TunnelFixtureBlock); empty where the mesh has nothing there.
     */
    public static VoxelShape structureShape(BlockPos p, int layers) {
        BlockState s = AflBlocks.ROAD_STRUCTURE.get().defaultBlockState().setValue(LAYERS, Math.max(1, Math.min(32, layers)));
        Holder h = holder;
        if (h == null) return SHAPES[s.getValue(LAYERS) - 1];
        VoxelShape m = meshShape(h, s, p);
        return m != null && m != NOTHING ? m : Shapes.empty();
    }

    @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        Holder h = holder;
        VoxelShape m = h == null ? null : meshShape(h, s, p);
        return m != null ? m : SHAPES[s.getValue(LAYERS) - 1];
    }

    @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        Holder h = holder;
        if (h == null) return SHAPES[s.getValue(LAYERS) - 1];
        VoxelShape m = meshShape(h, s, p);
        return m != null ? m : Shapes.empty();
    }

    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }

    @Override public int getLightBlock(BlockState s, BlockGetter l, BlockPos p) { return 1; }

    @Override public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) { return false; }

    @Override public boolean useShapeForLightOcclusion(BlockState s) { return false; }
}
