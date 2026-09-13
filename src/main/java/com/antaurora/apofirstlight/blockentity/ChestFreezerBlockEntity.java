package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.ChestFreezerBlock;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Only the LEFT cell owns a BE. The stable lid state is persisted by BlockState. */
public final class ChestFreezerBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private ChestFreezerBlock.LidState pending;
    private long finishTick;

    public ChestFreezerBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.CHEST_FREEZER.get(), pos, state);
    }

    @Override
    public AABB getRenderBoundingBox() {
        Direction otherDirection = getBlockState().getValue(ChestFreezerBlock.FACING).getCounterClockWise();
        BlockPos other = worldPosition.relative(otherDirection);
        return new AABB(Math.min(worldPosition.getX(), other.getX()) - 0.5, worldPosition.getY() - 0.25,
                Math.min(worldPosition.getZ(), other.getZ()) - 0.5,
                Math.max(worldPosition.getX(), other.getX()) + 1.5, worldPosition.getY() + 1.5,
                Math.max(worldPosition.getZ(), other.getZ()) + 1.5);
    }

    public boolean startTransition(ChestFreezerBlock.LidState target, long tick) {
        if (pending != null || !(getBlockState().getBlock() instanceof ChestFreezerBlock)) return false;
        ChestFreezerBlock.LidState current = getBlockState().getValue(ChestFreezerBlock.LID);
        if (target == current || (target != ChestFreezerBlock.LidState.CLOSED
                && current != ChestFreezerBlock.LidState.CLOSED)) return false;
        pending = target;
        finishTick = tick + ChestFreezerBlock.ANIMATION_TICKS;
        boolean visualLeft = target == ChestFreezerBlock.LidState.LEFT_OPEN
                || current == ChestFreezerBlock.LidState.LEFT_OPEN;
        // GeckoLib mirrors exported Geo X: Blockbench's right_lid is the
        // visually left/master half. Keep state and VoxelShape tied to that half.
        triggerAnim(visualLeft ? "right_lid_controller" : "left_lid_controller",
                target == ChestFreezerBlock.LidState.CLOSED ? "close" : "open");
        return true;
    }

    public void completeDue(long tick) {
        if (pending == null || tick < finishTick || level == null
                || !(getBlockState().getBlock() instanceof ChestFreezerBlock block)) return;
        ChestFreezerBlock.LidState target = pending;
        pending = null;
        block.commitLid(level, worldPosition, target);
    }

    public long ticksUntilCompletion(long tick) {
        return pending == null ? 0 : Math.max(1, finishTick - tick);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<ChestFreezerBlockEntity> left = new AnimationController<>(this,
                "left_lid_controller", 0, state -> state.setAndContinue(RawAnimation.begin().thenLoop(
                getBlockState().getValue(ChestFreezerBlock.LID) == ChestFreezerBlock.LidState.RIGHT_OPEN
                        ? "left_lid_open_pose" : "left_lid_closed_pose")));
        left.triggerableAnim("open", RawAnimation.begin().thenPlay("left_lid_open"));
        left.triggerableAnim("close", RawAnimation.begin().thenPlay("left_lid_close"));
        controllers.add(left);

        AnimationController<ChestFreezerBlockEntity> right = new AnimationController<>(this,
                "right_lid_controller", 0, state -> state.setAndContinue(RawAnimation.begin().thenLoop(
                getBlockState().getValue(ChestFreezerBlock.LID) == ChestFreezerBlock.LidState.LEFT_OPEN
                        ? "right_lid_open_pose" : "right_lid_closed_pose")));
        right.triggerableAnim("open", RawAnimation.begin().thenPlay("right_lid_open"));
        right.triggerableAnim("close", RawAnimation.begin().thenPlay("right_lid_close"));
        controllers.add(right);
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public double getTick(Object object) { return level == null ? 0 : level.getGameTime(); }
}
