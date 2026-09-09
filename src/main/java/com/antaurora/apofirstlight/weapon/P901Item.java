package com.antaurora.apofirstlight.weapon;

import com.antaurora.apofirstlight.weapon.client.P901Renderer;
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

/** V0.6 stack-local magazine; GeckoLibID remains render identity only. */
public final class P901Item extends Item implements GeoItem, NativeGunItem {
    public static final String CONTROLLER = "action";

    @Override
    public NativeGunDefinition definition() { return NativeGunData.get(new net.minecraft.resources.ResourceLocation("apocalypse_firstlight","p9_01")); }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (slotChanged || oldStack.getItem() != newStack.getItem()
                || oldStack.getCount() != newStack.getCount()) return true;
        var oldTag = NativeGunAmmo.tagWithoutAmmo(oldStack);
        var newTag = NativeGunAmmo.tagWithoutAmmo(newStack);
        // First accepted action assigns render identity. Subsequent identity changes still re-equip.
        if (!oldTag.contains(GeoItem.ID_NBT_KEY)) newTag.remove(GeoItem.ID_NBT_KEY);
        return !oldTag.equals(newTag);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide) NativeGunAmmo.initialize(stack, definition());
    }
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public P901Item() {
        super(new Properties().stacksTo(1));
        GeoItem.registerSyncedAnimatable(this);
        // A source 'step' holds the prior value until the segment ends. GeckoLib's
        // built-in stepped easing instead subdivides the interval and is not equivalent.
        GeckoLibUtil.addCustomEasingType("afl_hold", argument -> progress -> 0);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new P901AnimationController(this)
                .triggerableAnim("fire", RawAnimation.begin().thenPlay("animation.p9_01.fire"))
                .triggerableAnim("reload", RawAnimation.begin().thenPlay("animation.p9_01.reload"))
                .triggerableAnim("fire_last_round", RawAnimation.begin().thenPlay("animation.p9_01.fire_last_round"))
                .triggerableAnim("reload_empty", RawAnimation.begin().thenPlay("animation.p9_01.reload_empty")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) { return false; }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) { return true; }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(NativeGunAmmo.capacity(stack,definition())==24
                ?"tooltip.apocalypse_firstlight.p9_01.spec_extended":"tooltip.apocalypse_firstlight.p9_01.spec")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.apocalypse_firstlight.p9_01.description")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private P901Renderer renderer;
            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(
                    net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.InteractionHand hand, ItemStack stack) {
                return hand == net.minecraft.world.InteractionHand.MAIN_HAND
                        ? com.antaurora.apofirstlight.weapon.client.P901PlayerPose.PISTOL : null;
            }
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new P901Renderer();
                return renderer;
            }
        });
    }
}
