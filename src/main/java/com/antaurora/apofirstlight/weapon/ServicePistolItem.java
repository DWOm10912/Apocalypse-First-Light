package com.antaurora.apofirstlight.weapon;

import com.antaurora.apofirstlight.weapon.client.ServicePistolRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

/** Animation/audio prototype only. GeckoLibID identifies the renderer, not ammo/gameplay state. */
public final class ServicePistolItem extends Item implements GeoItem {
    public static final String CONTROLLER = "action";
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ServicePistolItem() {
        super(new Properties().stacksTo(1));
        GeoItem.registerSyncedAnimatable(this);
        // A source 'step' holds the prior value until the segment ends. GeckoLib's
        // built-in stepped easing instead subdivides the interval and is not equivalent.
        GeckoLibUtil.addCustomEasingType("afl_hold", argument -> progress -> 0);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new ServicePistolAnimationController(this)
                .triggerableAnim("fire", RawAnimation.begin().thenPlay("animation.service_pistol.fire"))
                .triggerableAnim("reload", RawAnimation.begin().thenPlay("animation.service_pistol.reload")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) { return false; }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) { return true; }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.apocalypse_firstlight.service_pistol.prototype"));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private ServicePistolRenderer renderer;
            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(
                    net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.InteractionHand hand, ItemStack stack) {
                return hand == net.minecraft.world.InteractionHand.MAIN_HAND
                        ? com.antaurora.apofirstlight.weapon.client.ServicePistolPlayerPose.PISTOL : null;
            }
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new ServicePistolRenderer();
                return renderer;
            }
        });
    }
}
