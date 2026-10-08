package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.meshhit.MeshHitMultiCell;
import com.antaurora.apofirstlight.blockentity.VendingMachineBlockEntity;
import com.antaurora.apofirstlight.item.VendingMachineBlockItem;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.interaction.CrowbarSmashAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/**
 * Vending Machine V2 (2026-10-01, tools/build-vending-machine-v2.mjs, Pure Mesh): one identity over two halves, lower-owned
 * contents (a searchable container: searched through broken glass, the goods behind the glass from the shared goods
 * library), fixed cabinet collision in both glass states. {@link #BROKEN} and {@link #LIT} on both halves; lights only
 * (no cooling), fed through the standard power port on the lower half's back: LIT gives block light {@link #LIGHT_LEVEL}
 * and the mesh's lit light set (VendingMachineBlockEntity, energy/CompressorAppliance in lights-only mode).
 */
public final class VendingMachineBlock extends HorizontalDirectionalBlock implements EntityBlock, MeshHitMultiCell {
    /** The hit mesh (docs/rendering/mesh_hit_runtime_v1.md): every cell hits on the lower half (its block entity draws the machine). */
    @Override
    public BlockPos meshHitMaster(BlockState state, BlockPos pos) {
        return lower(state, pos);
    }

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final int LIGHT_LEVEL = 8;
    /** The glass opening (tools/build-vending-machine-v2.mjs OPENING), block px in the north-facing frame, both halves. */
    private static final double GLASS_X0 = 5.2, GLASS_X1 = 14.8, GLASS_Y0 = 7.6, GLASS_Y1 = 26.6;
    public VendingMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER).setValue(BROKEN, false).setValue(LIT, false));
    }
    public static BlockPos lower(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        BlockPos p = c.getClickedPos();
        if (p.getY() >= c.getLevel().getMaxBuildHeight()-1
                || !c.getLevel().getBlockState(p.above()).canBeReplaced(c)
                || !c.getLevel().getBlockState(p.below()).isFaceSturdy(c.getLevel(), p.below(), Direction.UP)) return null;
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite())
                .setValue(BROKEN, VendingMachineBlockItem.hasBrokenGlass(c.getItemInHand()));
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), UPDATE_ALL);
        // placed by a player: its contents are the player's own, never searched
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof VendingMachineBlockEntity be) be.markPlacedByPlayer();
    }
    @Override public BlockState updateShape(BlockState s, Direction d, BlockState n, LevelAccessor l, BlockPos p, BlockPos np) {
        boolean upper = s.getValue(HALF) == DoubleBlockHalf.UPPER;
        if (d == (upper ? Direction.DOWN : Direction.UP)) {
            if (!n.is(this) || n.getValue(HALF) == s.getValue(HALF) || n.getValue(FACING) != s.getValue(FACING))
                return Blocks.AIR.defaultBlockState();
            if (upper) return s.setValue(BROKEN, n.getValue(BROKEN)).setValue(LIT, n.getValue(LIT));
        }
        if (!upper && d == Direction.DOWN && !n.isFaceSturdy(l, np, Direction.UP)) return Blocks.AIR.defaultBlockState();
        return s;
    }
    @Override public boolean canSurvive(BlockState s, LevelReader l, BlockPos p) {
        if (s.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = l.getBlockState(p.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        return l.getBlockState(p.below()).isFaceSturdy(l,p.below(),Direction.UP);
    }
    @Override public void playerWillDestroy(Level l, BlockPos p, BlockState s, Player player) {
        if (!l.isClientSide && !player.isCreative() && player.hasCorrectToolForDrops(s)) {
            ItemStack dropped = new ItemStack(AflItems.VENDING_MACHINE.get());
            VendingMachineBlockItem.setBrokenGlass(dropped, s.getValue(BROKEN));
            popResource(l, lower(s,p), dropped);
        }
        super.playerWillDestroy(l,p,s,player);
    }
    @Override public void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
        if (!s.is(next.getBlock()) && l.getBlockEntity(p) instanceof VendingMachineBlockEntity be) { be.dropContentsOnce(); be.plugCord().release(); }
        super.onRemove(s,l,p,next,moving);
    }
    /** Lights on / off: LIT on both halves (light level and the mesh's light set follow it). */
    public void setLit(Level level, BlockPos lower, boolean lit) {
        BlockState state = level.getBlockState(lower);
        if (!state.is(this) || state.getValue(HALF) != DoubleBlockHalf.LOWER || state.getValue(LIT) == lit) return;
        level.setBlock(lower, state.setValue(LIT, lit), UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE);
        BlockState upper = level.getBlockState(lower.above());
        if (upper.is(this) && upper.getValue(HALF) == DoubleBlockHalf.UPPER)
            level.setBlock(lower.above(), upper.setValue(LIT, lit), UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE);
    }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return s.getValue(HALF) == DoubleBlockHalf.LOWER ? new VendingMachineBlockEntity(p,s) : null;
    }
    /** Server, lower half: rolls pending world loot at once and runs the lights' power (VendingMachineBlockEntity#serverTick). */
    @Override @SuppressWarnings("unchecked")
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                                                       net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(HALF) != DoubleBlockHalf.LOWER) return null;
        return (net.minecraft.world.level.block.entity.BlockEntityTicker<T>) (net.minecraft.world.level.block.entity.BlockEntityTicker<VendingMachineBlockEntity>)
                (tickerLevel, tickerPos, tickerState, be) -> be.serverTick();
    }
    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        // Tiny symmetric inset matches the saved source. Never remove cabinet collision when glass breaks.
        return box(.18,0,.18,15.82,16,15.82);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(FACING,HALF,BROKEN,LIT); }
    @Override public BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING,r.rotate(s.getValue(FACING))); }
    @Override public BlockState mirror(BlockState s, Mirror m) { return s.rotate(m.getRotation(s.getValue(FACING))); }

    public static Vec3 canonical(Direction f, Vec3 v) {
        return switch(f) {
            case SOUTH -> new Vec3(1-v.x,v.y,1-v.z);
            case EAST -> new Vec3(v.z,v.y,1-v.x);
            case WEST -> new Vec3(1-v.z,v.y,v.x);
            default -> v;
        };
    }
    public static Vec3 frontPoint(BlockState s, BlockPos p, Vec3 eye, BlockHitResult hit) {
        if (hit.getDirection() != s.getValue(FACING)) return null;
        BlockPos base = lower(s,p);
        Vec3 origin = Vec3.atLowerCornerOf(base);
        Vec3 e = canonical(s.getValue(FACING),eye.subtract(origin));
        Vec3 h = canonical(s.getValue(FACING),hit.getLocation().subtract(origin));
        if (e.z >= h.z || h.z > .08 || h.z < -.02) return null;
        // the glass opening only, not the payment column, header or bin
        return h.x >= GLASS_X0/16 && h.x <= GLASS_X1/16 && h.y >= GLASS_Y0/16 && h.y <= GLASS_Y1/16 ? h : null;
    }
    @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && player.getMainHandItem().isEmpty() && !player.isSpectator()) {
            if (l.isClientSide) return InteractionResult.SUCCESS;
            return player instanceof net.minecraft.server.level.ServerPlayer server
                    ? com.antaurora.apofirstlight.energy.PowerPlugs.useDevice(l, lower(s, p), server) : InteractionResult.PASS;
        }
        Vec3 point = frontPoint(s,p,player.getEyePosition(),hit);
        if (point == null || player.isSpectator()) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        BlockPos base = lower(s,p);
        if (!s.getValue(BROKEN)) {
            if (hand!=InteractionHand.MAIN_HAND||!held.is(AflItems.CROWBAR.get())) return InteractionResult.PASS;
            if (player instanceof net.minecraft.server.level.ServerPlayer server) CrowbarSmashAction.begin(server,base,hand);
            return InteractionResult.CONSUME; // The dedicated viewmodel owns this action, not Vanilla swing.
        }
        if(player instanceof net.minecraft.server.level.ServerPlayer server&&CrowbarSmashAction.active(server))return InteractionResult.CONSUME;
        // broken glass: search the machine, or view it once searched (3 x 3 menu)
        if (!(l.getBlockEntity(base) instanceof VendingMachineBlockEntity be)) return InteractionResult.PASS;
        if (!l.isClientSide) player.openMenu(be);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
