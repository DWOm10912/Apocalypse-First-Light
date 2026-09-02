package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class LeadChestBlockEntity extends ChestBlockEntity implements GeoBlockEntity {
    private static final String CONTROLLER_NAME = "lead_chest_controller";
    private static final String OPEN_TRIGGER = "open";
    private static final String CLOSE_TRIGGER = "close";
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public LeadChestBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.LEAD_CHEST.get(), position, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.apocalypse_firstlight.lead_chest");
    }

    @Override
    protected void signalOpenCount(Level level, BlockPos position, BlockState state,
                                   int oldCount, int newCount) {
        super.signalOpenCount(level, position, state, oldCount, newCount);
        if (level.isClientSide()) {
            return;
        }
        if (oldCount == 0 && newCount > 0) {
            triggerAnim(CONTROLLER_NAME, OPEN_TRIGGER);
        } else if (oldCount > 0 && newCount == 0) {
            triggerAnim(CONTROLLER_NAME, CLOSE_TRIGGER);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<LeadChestBlockEntity> controller =
                new AnimationController<>(this, CONTROLLER_NAME, state -> PlayState.STOP);
        controller.triggerableAnim(OPEN_TRIGGER, RawAnimation.begin().thenPlay("lead_chest_open"));
        controller.triggerableAnim(CLOSE_TRIGGER, RawAnimation.begin().thenPlay("lead_chest_close"));
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
