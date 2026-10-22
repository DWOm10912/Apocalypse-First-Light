package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.cabin.EmergencyRadio;
import com.antaurora.apofirstlight.map.StartMap;
import com.antaurora.apofirstlight.meshhit.MeshHitReachesDown;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The PRAIRIE road map spread on a table (StartMap; user 2026-10-21): a fuel station's free state road map, opened to
 * 60 x 45 cm, a pencil beside it. Lying on a wooden table, counter, office desk or nightstand under it, it sinks onto
 * that top as the emergency radio does ({@link EmergencyRadio#surface}, {@link #SURFACE}, its shape in the cell below:
 * {@link MeshHitReachesDown}); FACING is the way its print's top points (its reader faces that way). Right-click picks it
 * up: a new map whose record already shows the way from the forest home to Linden (StartMap#make), into the empty hand
 * or the inventory. Broken by hand it drops that map too. Paper: breaks at once, no tool, no loot table (the map is made
 * when it is taken); no collision.
 */
public class PrairieRoadMapBlock extends HorizontalDirectionalBlock implements MeshHitReachesDown {
    public static final EnumProperty<EmergencyRadio.Surface> SURFACE = EmergencyRadio.Radio.SURFACE;
    /** The sheet's outline in the model's frame (print top to the north), px; a little thicker than the paper to aim at. */
    static final double X0 = 3.2, X1 = 12.8, Z0 = 4.4, Z1 = 11.6, THICK = 0.5;
    private static final VoxelShape[][] SHAPES = new VoxelShape[EmergencyRadio.Surface.values().length][2];

    static {
        for (EmergencyRadio.Surface s : EmergencyRadio.Surface.values()) {
            double y = -16 * s.sink;
            SHAPES[s.ordinal()][0] = Block.box(X0, y, Z0, X1, y + THICK, Z1);
            SHAPES[s.ordinal()][1] = Block.box(Z0, y, X0, Z1, y + THICK, X1);
        }
    }

    public PrairieRoadMapBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SURFACE, EmergencyRadio.Surface.FLOOR));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SURFACE);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        EmergencyRadio.Surface s = EmergencyRadio.surface(context.getLevel(), context.getClickedPos());
        return s == null ? null : defaultBlockState().setValue(FACING, context.getHorizontalDirection()).setValue(SURFACE, s);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(SURFACE).ordinal()][state.getValue(FACING).getAxis() == Direction.Axis.Z ? 0 : 1];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** The surface under it changed: it sinks onto the new one; with nothing to lie on it falls as the map. */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (level.isClientSide || !from.equals(pos.below())) return;
        EmergencyRadio.Surface s = EmergencyRadio.surface(level, pos);
        if (s == null) {
            level.removeBlock(pos, false);
            if (level instanceof ServerLevel sl) popResource(level, pos, StartMap.make(sl.getServer()));
        } else if (s != state.getValue(SURFACE)) level.setBlock(pos, state.setValue(SURFACE, s), Block.UPDATE_CLIENTS);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level instanceof ServerLevel sl) {
            level.removeBlock(pos, false);
            ItemStack map = StartMap.make(sl.getServer());
            if (player.getItemInHand(hand).isEmpty()) player.setItemInHand(hand, map);
            else if (!player.getInventory().add(map)) popResource(level, pos, map);
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.9F, 0.8F);
        }
        // no swing: the sheet is lifted off the table
        return InteractionResult.CONSUME;
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity be, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, be, tool);
        if (level instanceof ServerLevel sl) popResource(level, pos, StartMap.make(sl.getServer()));
    }
}
