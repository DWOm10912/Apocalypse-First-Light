package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.BeverageCoolerBlock;
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

/** The master owns two concurrent animation controllers; BlockState owns durable end poses. */
public final class BeverageCoolerBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Boolean pendingLeft;
    private Boolean pendingRight;
    private long leftFinishTick;
    private long rightFinishTick;

    public BeverageCoolerBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.BEVERAGE_COOLER.get(), pos, state);
    }

    @Override
    public AABB getRenderBoundingBox() {
        Direction right = getBlockState().getValue(BeverageCoolerBlock.FACING).getCounterClockWise();
        BlockPos other = worldPosition.relative(right);
        return new AABB(Math.min(worldPosition.getX(), other.getX()) - 1.5, worldPosition.getY(),
                Math.min(worldPosition.getZ(), other.getZ()) - 1.5,
                Math.max(worldPosition.getX(), other.getX()) + 2.5, worldPosition.getY() + 2.1,
                Math.max(worldPosition.getZ(), other.getZ()) + 2.5);
    }

    public boolean startDoor(boolean left, boolean targetOpen, long tick) {
        if (left ? pendingLeft != null : pendingRight != null) return false;
        if (left) {
            pendingLeft = targetOpen;
            leftFinishTick = tick + BeverageCoolerBlock.ANIMATION_TICKS;
        } else {
            pendingRight = targetOpen;
            rightFinishTick = tick + BeverageCoolerBlock.ANIMATION_TICKS;
        }
        triggerAnim(left ? "left_door_controller" : "right_door_controller", targetOpen ? "open" : "close");
        return true;
    }

    public void completeDue(long tick) {
        if (!(getBlockState().getBlock() instanceof BeverageCoolerBlock block) || level == null) return;
        if (pendingLeft != null && tick >= leftFinishTick) {
            boolean target = pendingLeft;
            pendingLeft = null;
            block.commitDoor(level, worldPosition, true, target);
        }
        if (pendingRight != null && tick >= rightFinishTick) {
            boolean target = pendingRight;
            pendingRight = null;
            block.commitDoor(level, worldPosition, false, target);
        }
    }

    public long ticksUntilNextCompletion(long tick) {
        long left = pendingLeft == null ? Long.MAX_VALUE : Math.max(1, leftFinishTick - tick);
        long right = pendingRight == null ? Long.MAX_VALUE : Math.max(1, rightFinishTick - tick);
        return Math.min(left, right) == Long.MAX_VALUE ? 0 : Math.min(left, right);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<BeverageCoolerBlockEntity> left = new AnimationController<>(this,
                "left_door_controller", 0, state -> state.setAndContinue(RawAnimation.begin().thenLoop(
                getBlockState().getValue(BeverageCoolerBlock.LEFT_OPEN)
                        ? "left_door_open_pose" : "left_door_closed_pose")));
        left.triggerableAnim("open", RawAnimation.begin().thenPlay("left_door_open"));
        left.triggerableAnim("close", RawAnimation.begin().thenPlay("left_door_close"));
        controllers.add(left);

        AnimationController<BeverageCoolerBlockEntity> right = new AnimationController<>(this,
                "right_door_controller", 0, state -> state.setAndContinue(RawAnimation.begin().thenLoop(
                getBlockState().getValue(BeverageCoolerBlock.RIGHT_OPEN)
                        ? "right_door_open_pose" : "right_door_closed_pose")));
        right.triggerableAnim("open", RawAnimation.begin().thenPlay("right_door_open"));
        right.triggerableAnim("close", RawAnimation.begin().thenPlay("right_door_close"));
        controllers.add(right);
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public double getTick(Object object) { return level == null ? 0 : level.getGameTime(); }
}
