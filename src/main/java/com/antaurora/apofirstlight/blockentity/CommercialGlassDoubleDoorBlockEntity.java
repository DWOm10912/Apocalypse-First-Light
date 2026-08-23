package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class CommercialGlassDoubleDoorBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public CommercialGlassDoubleDoorBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.COMMERCIAL_GLASS_DOUBLE_DOOR.get(), position, state);
        ApocalypseFirstLight.LOGGER.debug(
                "[AFL GLASS DOOR DEBUG] blockEntity lifecycle=constructor side=UNKNOWN pos={} beIdentity={}",
                position, System.identityHashCode(this));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        ApocalypseFirstLight.LOGGER.debug(
                "[AFL GLASS DOOR DEBUG] blockEntity lifecycle=onLoad side={} pos={} beIdentity={}",
                debugSide(), getBlockPos(), System.identityHashCode(this));
    }

    @Override
    public void setRemoved() {
        ApocalypseFirstLight.LOGGER.debug(
                "[AFL GLASS DOOR DEBUG] blockEntity lifecycle=setRemoved side={} pos={} beIdentity={}",
                debugSide(), getBlockPos(), System.identityHashCode(this));
        super.setRemoved();
    }

    public void triggerDoorAnimation(boolean open) {
        String trigger = open ? "open" : "close";
        ApocalypseFirstLight.LOGGER.debug(
                "[AFL GLASS DOOR DEBUG] triggerAnim side={} masterBE={} beIdentity={} controller=door_controller trigger={}",
                debugSide(), getBlockPos(), System.identityHashCode(this), trigger);
        triggerAnim("door_controller", trigger);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        ApocalypseFirstLight.LOGGER.debug(
                "[AFL GLASS DOOR DEBUG] registerControllers side={} pos={} beIdentity={} controller=door_controller triggers=open->door_open,close->door_close",
                debugSide(), getBlockPos(), System.identityHashCode(this));
        AnimationController<CommercialGlassDoubleDoorBlockEntity> controller =
                new AnimationController<>(this, "door_controller", state -> PlayState.STOP);
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

    private String debugSide() {
        return level == null ? "UNKNOWN" : level.isClientSide() ? "CLIENT" : "SERVER";
    }
}
