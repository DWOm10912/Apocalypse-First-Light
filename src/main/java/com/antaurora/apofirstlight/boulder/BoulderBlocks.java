package com.antaurora.apofirstlight.boulder;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Rock R1a (docs/gameplay/rock_r1_stones_boulders_design.md 3.2, 11.2): the boulder blocks. A boulder is one root block
 * plus plain cells, as a Mesh tree is (tree/TreeBlocks); its rock, collision and drawing follow from the root's state (the
 * node: which rock or piece, BoulderData; the turn) and a cell's offset from the form origin.
 * <ul>
 * <li>{@code boulder}: the root, in the node's anchor cell. Its block entity keeps the strikes the piece has taken.</li>
 * <li>{@code boulder_cell}: the other cells the rock fills above the ground, and (placed by the earthwork, Earthwork#settle)
 * the buried cells a dig has bared.</li>
 * </ul>
 * Mining audit: not breakable by hand or any vanilla tool (strength -1, no drops, no mineable tag); only the pick mattock
 * strikes the rock (BoulderStrike), creative players remove it with the dev command. Pistons cannot move it. No outline
 * (terrainsmooth/client/SmoothOutline): rock is ground. Light never asks the index (the light engine's threads have no
 * side): it lets sky light through and occludes nothing, as tree cells. Overworld only, as trees.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BoulderBlocks {
    public static final ResourceLocation ROOT_ID = new ResourceLocation("apocalypse_firstlight", "boulder"),
            CELL_ID = new ResourceLocation("apocalypse_firstlight", "boulder_cell");
    public static final IntegerProperty PIECE = IntegerProperty.create("piece", 0, BoulderData.NODES - 1);
    public static final IntegerProperty TURN = IntegerProperty.create("turn", 0, 3);

    private static Root root;
    private static Cell cell;
    private static BlockEntityType<Hits> hitsType;

    private BoulderBlocks() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> {
            root = new Root();
            cell = new Cell();
            helper.register(ROOT_ID, root);
            helper.register(CELL_ID, cell);
        });
        event.register(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, helper -> {
            hitsType = BlockEntityType.Builder.of(Hits::new, root).build(null);
            helper.register(ROOT_ID, hitsType);
        });
    }

    public static boolean ready() { return root != null; }
    public static Root root() { return root; }
    public static Cell cell() { return cell; }

    public static boolean isBoulder(BlockState s) {
        Block b = s.getBlock();
        return root != null && (b == root || b == cell);
    }

    public static BlockState rootState(int node, int turn) {
        return root.defaultBlockState().setValue(PIECE, node).setValue(TURN, turn & 3);
    }

    // ---------------------------------------------------------------------------------------------- collision

    private static final ThreadLocal<ArrayList<BoulderIndex.Root>> FOUND = ThreadLocal.withInitial(ArrayList::new);

    /** A cell's collision: the column boxes of every rock filling it (usually one). */
    public static VoxelShape collision(BlockGetter level, BlockPos pos) {
        if (root == null) return Shapes.empty();
        var found = FOUND.get();
        found.clear();
        BoulderIndex.of(level).roots(pos.getX(), pos.getY(), pos.getZ(), found);
        VoxelShape shape = null;
        for (var r : found) {
            var c = r.cellAt(pos.getX(), pos.getY(), pos.getZ());
            if (c == null) continue;
            VoxelShape s = BoulderData.shape(r.node, r.turn, c);
            shape = shape == null ? s : Shapes.or(shape, s);
        }
        found.clear();
        return shape == null ? Shapes.empty() : shape;
    }

    private static BlockBehaviour.Properties rock() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable().dynamicShape()
                .sound(SoundType.STONE).pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, e) -> false)
                .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false);
    }

    /** What root and cells share: shape from the index, no light blocking, paths blocked where the rock is. */
    abstract static class Rock extends Block {
        Rock() { super(rock()); }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return collision(level, pos);
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return collision(level, pos);
        }

        @Override
        public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

        @Override
        public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return true; }

        @Override
        public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) { return 0; }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
            // the column boxes are no full block: vanilla would call the cell open and walkers would run into the rock
            return collision(level, pos).isEmpty() && super.isPathfindable(state, level, pos, type);
        }
    }

    /** The anchor: the rock's identity. On the server it keeps the server's index and tells the clients. */
    public static final class Root extends Rock implements EntityBlock {
        Root() {
            registerDefaultState(stateDefinition.any().setValue(PIECE, 0).setValue(TURN, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(PIECE, TURN);
        }

        @Override
        public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Hits(pos, state); }

        @Override
        public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
            super.onPlace(state, level, pos, old, moving);
            if (level instanceof ServerLevel server && !old.equals(state)) BoulderNetwork.rootChanged(server, pos, state);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moving) {
            super.onRemove(state, level, pos, now, moving);
            if (level instanceof ServerLevel server && !now.equals(state)) BoulderNetwork.rootChanged(server, pos, now);
        }
    }

    /** A cell the rock fills (no state of its own). */
    public static final class Cell extends Rock {}

    /** The root's strikes so far (server only; saved with the chunk). */
    public static final class Hits extends BlockEntity {
        int hits;

        Hits(BlockPos pos, BlockState state) { super(hitsType, pos, state); }

        public int hits() { return hits; }

        /** One more strike taken; the strikes so far. */
        public int strike() {
            hits++;
            setChanged();
            return hits;
        }

        /** A new piece in a root cell that held another (the block entity stays with the block): no strikes yet. */
        public void reset() {
            hits = 0;
            setChanged();
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            tag.putInt("hits", hits);
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            hits = tag.getInt("hits");
        }
    }
}
