package com.antaurora.apofirstlight.block;

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

/** One identity, lower-owned inventory, fixed cabinet collision in both glass states. */
public final class VendingMachineBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");
    public VendingMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER).setValue(BROKEN, false));
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
    }
    @Override public BlockState updateShape(BlockState s, Direction d, BlockState n, LevelAccessor l, BlockPos p, BlockPos np) {
        boolean upper = s.getValue(HALF) == DoubleBlockHalf.UPPER;
        if (d == (upper ? Direction.DOWN : Direction.UP)) {
            if (!n.is(this) || n.getValue(HALF) == s.getValue(HALF) || n.getValue(FACING) != s.getValue(FACING))
                return Blocks.AIR.defaultBlockState();
            if (upper) return s.setValue(BROKEN, n.getValue(BROKEN));
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
        if (!s.is(next.getBlock()) && l.getBlockEntity(p) instanceof VendingMachineBlockEntity be) be.dropContents();
        super.onRemove(s,l,p,next,moving);
    }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return s.getValue(HALF) == DoubleBlockHalf.LOWER ? new VendingMachineBlockEntity(p,s) : null;
    }
    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        // Tiny symmetric inset matches the saved source. Never remove cabinet collision when glass breaks.
        return box(.18,0,.18,15.82,16,15.82);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(FACING,HALF,BROKEN); }
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
        // Match the glass's saved source X/Y, not the full front/payment panel.
        return h.x >= 4.26/16 && h.x <= 14.33/16 && h.y >= 7.78/16 && h.y <= 26.68/16 ? h : null;
    }
    public static int slot(Vec3 front) {
        if (front == null) return -1;
        int row = (int)Math.floor((front.y*16-7.78)/4.7);
        if (row < 0 || row > 4) return -1;
        int column = front.x < 7.43/16 ? 0 : front.x < 11.03/16 ? 1 : 2;
        return Math.min(row, 3)*3 + column;
    }
    @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player player, InteractionHand hand, BlockHitResult hit) {
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
        int slot = slot(point);
        if (slot < 0 || !(l.getBlockEntity(base) instanceof VendingMachineBlockEntity be)) return InteractionResult.PASS;
        if (!l.isClientSide) {
            if (held.isEmpty()) {
                ItemStack out = be.take(slot);
                if (!player.getInventory().add(out)) player.drop(out,false);
            } else if (be.put(slot,held) && !player.isCreative()) held.shrink(1);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
