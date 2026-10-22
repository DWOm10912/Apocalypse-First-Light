package com.antaurora.apofirstlight.cabin;

import com.antaurora.apofirstlight.meshhit.MeshHitMultiCell;
import com.antaurora.apofirstlight.meshshape.AflMeshInteractionBlock;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

/**
 * AFL Workbench V1 (docs/gameplay/afl_workbench_v1_design.md 14, 2026-10-21): a wooden workbench two cells across, one
 * deep, two high (tools/build-afl-workbench-v1.mjs, a Furniture Mesh drawn facing north): a vise end on the viewer's left,
 * two drawers under the maple top on the right, a tool board with five peg pairs and a shelf above:
 * <ul>
 * <li>The top ({@code top}): the crafting page (client/workbench/AflWorkbenchScreen, design doc 16; no recipes yet).</li>
 * <li>Drawers {@code drawer_left} / {@code drawer_right} (as seen from the front): aim at a drawer front and it slides out
 * 40 cm (8 ticks, eased), aim at it again and it slides in; aim into an open drawer for its 9 slots (a plain chest
 * screen, no search: a placed bench is the player's). Each drawer keeps its own 9 ({@link WorkbenchBlockEntity}).</li>
 * <li>Pegs {@code peg0..4} (left to right from the front): one tool hangs on each (any single non-block item that is not a
 * gun, as the tool rack's pegs; not the felling axe, too long for the board), drawn by the block entity renderer.</li>
 * </ul>
 * Cells: the master is the lower left of the north frame ({@link WoodFurnitureV2.Wide}'s convention: x 0..1, the right
 * half at {@code facing.getClockWise()}), holding the block entity; the upper cells hold the board. One cell gone, the
 * bench goes (the master drops it and its things). Mining (AGENTS.md audit): wood furniture, the axe, no tier, no
 * correct-tool requirement.
 */
public final class Workbench extends BaseEntityBlock implements AflMeshInteractionBlock, MeshHitMultiCell {
    public static BlockEntityType<WorkbenchBlockEntity> TYPE;
    public static final String ID = "wooden_workbench";
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<WoodFurnitureV2.Wide.Part> PART = WoodFurnitureV2.Wide.PART;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty DRAWER_LEFT = BooleanProperty.create("drawer_left"), DRAWER_RIGHT = BooleanProperty.create("drawer_right");

    /** The north frame (blocks from the master's corner, tools/build-afl-workbench-v1.mjs): the peg pairs' middles, left to right. */
    public static final double[] SLOT_X = {1.78, 1.51, 1.24, 0.97, 0.70};
    /** The pegs' tops, the plane the hung tools hang in. */
    public static final double PEG_TOP = 1.63, TOOL_Z = 0.80;
    /** The drawers' fronts (x middle, sound height) in the north frame. */
    private static final double[] DRAWER_X = {0.7935, 0.365};
    private static final double DRAWER_Y = 0.70;
    public static final int PEGS = 5;

    private final ResourceLocation shapeProfile = FurnitureOpening.profile(ID);

    Workbench(Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(PART, WoodFurnitureV2.Wide.Part.LEFT)
                .setValue(HALF, DoubleBlockHalf.LOWER).setValue(DRAWER_LEFT, false).setValue(DRAWER_RIGHT, false));
    }

    /** Called from CabinBlocks#register after the blocks. */
    static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, helper -> {
            TYPE = BlockEntityType.Builder.of(WorkbenchBlockEntity::new, CabinBlocks.block(ID)).build(null);
            helper.register(CabinBlocks.id(ID), TYPE);
        });
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, PART, HALF, DRAWER_LEFT, DRAWER_RIGHT); }

    // ------------------------------------------------------------------------------------------------ cells

    static Direction toRight(BlockState state) { return state.getValue(FACING).getClockWise(); }

    public static boolean isMaster(BlockState state) { return state.getValue(PART) == WoodFurnitureV2.Wide.Part.LEFT && state.getValue(HALF) == DoubleBlockHalf.LOWER; }

    /** The lower left cell, with the block entity, of any cell. */
    public static BlockPos master(BlockState state, BlockPos pos) {
        BlockPos p = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        return state.getValue(PART) == WoodFurnitureV2.Wide.Part.LEFT ? p : p.relative(toRight(state).getOpposite());
    }

    /** The four cells, master first. */
    static BlockPos[] cells(BlockPos master, Direction facing) {
        BlockPos right = master.relative(facing.getClockWise());
        return new BlockPos[]{master, master.above(), right, right.above()};
    }

    /** A north-frame point (blocks from the master cell's corner) in the world. */
    public static Vec3 local(BlockPos master, Direction facing, double x, double y, double z) {
        return WoodFurnitureV2.Wide.local(master, facing, x, y, z);
    }

    @Override
    public BlockPos meshHitMaster(BlockState state, BlockPos pos) { return master(state, pos); }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (pos.getY() >= level.getMaxBuildHeight() - 1) return null;
        BlockPos[] cells = cells(pos, facing);
        for (int i = 1; i < cells.length; i++)
            if (!level.getBlockState(cells[i]).canBeReplaced(context) || !level.getWorldBorder().isWithinBounds(cells[i])) return null;
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        BlockPos[] cells = cells(pos, state.getValue(FACING));
        level.setBlock(cells[1], state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
        level.setBlock(cells[2], state.setValue(PART, WoodFurnitureV2.Wide.Part.RIGHT), 3);
        level.setBlock(cells[3], state.setValue(PART, WoodFurnitureV2.Wide.Part.RIGHT).setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    /** A cell beside or over / under this one gone (or not this bench's): this one goes too (the master drops the bench and its things). */
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction d, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        boolean left = state.getValue(PART) == WoodFurnitureV2.Wide.Part.LEFT, lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
        Direction across = left ? toRight(state) : toRight(state).getOpposite(), vertical = lower ? Direction.UP : Direction.DOWN;
        if (d == across) {
            return neighbour.is(this) && neighbour.getValue(PART) != state.getValue(PART) && neighbour.getValue(HALF) == state.getValue(HALF)
                    && neighbour.getValue(FACING) == state.getValue(FACING) ? state : Blocks.AIR.defaultBlockState();
        }
        if (d == vertical) {
            return neighbour.is(this) && neighbour.getValue(HALF) != state.getValue(HALF) && neighbour.getValue(PART) == state.getValue(PART)
                    && neighbour.getValue(FACING) == state.getValue(FACING) ? state : Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    /** Another cell broken in creative: the master goes without a drop (its things still drop), as the wardrobe's. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && (player.isCreative() || !player.hasCorrectToolForDrops(state)) && !isMaster(state)) {
            BlockPos m = master(state, pos);
            BlockState ms = level.getBlockState(m);
            if (ms.is(this) && isMaster(ms)) {
                level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, m, Block.getId(ms));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof WorkbenchBlockEntity bench) bench.dropContents();
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return isMaster(state) ? new WorkbenchBlockEntity(pos, state) : null; }

    // ------------------------------------------------------------------------------------------------ shapes

    @Override
    public ResourceLocation meshShapeProfile() { return shapeProfile; }

    /** The left cells by their drawers (left_00 .. left_11: the left, then the right drawer open), the right cells one state. */
    @Override
    public String meshShapeState(BlockState state) {
        if (state.getValue(PART) == WoodFurnitureV2.Wide.Part.RIGHT) return "right";
        return "left_" + (state.getValue(DRAWER_LEFT) ? 1 : 0) + (state.getValue(DRAWER_RIGHT) ? 1 : 0);
    }

    @Override
    public int meshShapeCell(BlockState state) { return state.getValue(HALF) == DoubleBlockHalf.UPPER ? 1 : 0; }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return meshSelectionShape(state); }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return meshPhysicalShape(state); }

    // ------------------------------------------------------------------------------------------- the drawers

    /** 0: the left drawer (seen from the front), 1: the right; -1: not a drawer region. */
    static int drawerOf(String region) {
        return region.endsWith("_left") ? 0 : region.endsWith("_right") ? 1 : -1;
    }

    static BooleanProperty drawerProperty(int side) { return side == 0 ? DRAWER_LEFT : DRAWER_RIGHT; }

    public static boolean drawerOpen(BlockGetter level, BlockPos master, int side) {
        BlockState s = level.getBlockState(master);
        return s.hasProperty(DRAWER_LEFT) && s.getValue(drawerProperty(side));
    }

    /** The drawer's look on both left cells (the master's state drives the animation, the upper's its shape). */
    static void setDrawerState(Level level, BlockPos master, int side, boolean open) {
        for (BlockPos p : new BlockPos[]{master, master.above()}) {
            BlockState s = level.getBlockState(p);
            if (s.hasProperty(DRAWER_LEFT)) level.setBlock(p, s.setValue(drawerProperty(side), open), 3);
        }
    }

    /** Where a drawer's sound plays: at its front, pulled out or in. */
    static Vec3 drawerFront(BlockPos master, Direction facing, int side, boolean open) {
        return local(master, facing, DRAWER_X[side], DRAWER_Y, open ? -0.35 : 0.05);
    }

    // ---------------------------------------------------------------------------------------------- the pegs

    /** The peg of a region (peg0 .. peg4), or -1. */
    public static int pegOf(String region) {
        if (!region.startsWith("peg") || region.length() != 4) return -1;
        int k = region.charAt(3) - '0';
        return k >= 0 && k < PEGS ? k : -1;
    }

    /** What a peg takes: the tool rack's long tools, less the felling axe (96 cm: it would stand on the bench top). */
    public static boolean hangable(ItemStack s) { return WoodFurnitureV2.ToolRack.longTool(s) && !s.is(AflItems.FELLING_AXE.get()); }

    /** The peg an item in hand goes to when aimed at `aimed`: that one if it is free, else the first free one; -1: none. */
    public static int target(WorkbenchBlockEntity bench, ItemStack held, int aimed) {
        if (!hangable(held)) return -1;
        if (aimed >= 0 && bench.tool(aimed).isEmpty()) return aimed;
        for (int k = 0; k < PEGS; k++) if (bench.tool(k).isEmpty()) return k;
        return -1;
    }

    /** Where a peg's tool hangs (for the prompt). */
    public static Vec3 pegPoint(BlockPos master, Direction facing, int peg) { return local(master, facing, SLOT_X[peg], PEG_TOP - 0.12, TOOL_Z - 0.04); }

    // ------------------------------------------------------------------------------------------- interaction

    /** The top: craft; drawers open / close, an open drawer's inside view; the pegs' prompts are the tool rack's (client WorldInteractionHint). */
    @Override
    public @Nullable String interactionHintKey(Level level, BlockState state, BlockPos pos, String region) {
        if (region.equals("top")) return "hint.apocalypse_firstlight.workbench.craft";
        int side = drawerOf(region);
        if (side < 0) return null;
        boolean open = drawerOpen(level, master(state, pos), side);
        String k = "hint.apocalypse_firstlight.cabinet.";
        if (region.startsWith("drawer")) return k + (open ? "close" : "open");
        return region.startsWith("interior") && open ? k + "view" : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        var region = meshInteraction(state, pos, player);
        BlockPos m = master(state, pos);
        if (region == null || !(level.getBlockEntity(m) instanceof WorkbenchBlockEntity bench)) return InteractionResult.PASS;
        String name = region.region();
        if (name.equals("top")) {
            if (level.isClientSide) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.workbench.WorkbenchClient.open(m));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        int peg = pegOf(name);
        if (peg >= 0) return usePeg(bench, level, m, player, hand, peg);
        int side = drawerOf(name);
        if (side < 0) return InteractionResult.PASS;
        boolean open = drawerOpen(level, m, side);
        if (name.startsWith("drawer")) {
            if (!level.isClientSide) bench.setDrawer(side, !open, player);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!name.startsWith("interior") || !open) return InteractionResult.PASS;
        if (!level.isClientSide) player.openMenu(bench.drawerMenu(side));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The tool rack's hands-only pegs: an empty hand takes the aimed tool, a tool in hand hangs where aimed or on the next free peg. */
    private static InteractionResult usePeg(WorkbenchBlockEntity bench, Level level, BlockPos master, Player player, InteractionHand hand, int aimed) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            if (bench.tool(aimed).isEmpty()) return InteractionResult.PASS;
            if (!level.isClientSide) {
                player.setItemInHand(hand, bench.takeTool(aimed));
                level.playSound(null, master.above(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8F, 0.9F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        int slot = target(bench, held, aimed);
        if (slot < 0) return hangable(held) ? InteractionResult.CONSUME : InteractionResult.PASS;
        if (!level.isClientSide) {
            bench.putTool(slot, held.split(1));
            level.playSound(null, master.above(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 0.9F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ------------------------------------------------------------------------------------------------- misc

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 20; }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }
}
