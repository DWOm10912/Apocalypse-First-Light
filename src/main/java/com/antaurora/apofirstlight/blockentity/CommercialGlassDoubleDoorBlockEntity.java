package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.CommercialGlassDoubleDoorBlock;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

public class CommercialGlassDoubleDoorBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private long lastToggleTick = -100;

    public CommercialGlassDoubleDoorBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.COMMERCIAL_GLASS_DOUBLE_DOOR.get(), position, state);
    }

    @Override
    public AABB getRenderBoundingBox() {
        Direction width = getBlockState().getValue(CommercialGlassDoubleDoorBlock.FACING).getClockWise();
        BlockPos otherHalf = worldPosition.relative(width);
        // The only renderer is attached to the lower-left part, but its model spans both
        // columns and both levels. Leave room in depth for either leaf to swing open.
        return new AABB(
                Math.min(worldPosition.getX(), otherHalf.getX()) - 1.0,
                worldPosition.getY() - 0.125,
                Math.min(worldPosition.getZ(), otherHalf.getZ()) - 1.0,
                Math.max(worldPosition.getX(), otherHalf.getX()) + 2.0,
                worldPosition.getY() + 2.125,
                Math.max(worldPosition.getZ(), otherHalf.getZ()) + 2.0);
    }

    public boolean canToggle(long tick) { return tick - lastToggleTick >= 12; }
    public void markToggled(long tick) { lastToggleTick = tick; }

    public void triggerDoorAnimation(boolean open) {
        String trigger = open ? "open" : "close";
        triggerAnim("door_controller", trigger);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<CommercialGlassDoubleDoorBlockEntity> controller =
                new AnimationController<>(this, "door_controller", state ->
                        state.setAndContinue(RawAnimation.begin().thenLoop(getBlockState().getValue(
                                CommercialGlassDoubleDoorBlock.OPEN) ? "door_open_pose" : "door_closed_pose")));
        controller.triggerableAnim("open", RawAnimation.begin().thenPlay("door_open"));
        controller.triggerableAnim("close", RawAnimation.begin().thenPlay("door_close"));
        controllers.add(controller);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        return level == null ? 0.0D : level.getGameTime();
    }

}
