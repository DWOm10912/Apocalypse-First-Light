package com.antaurora.apofirstlight.weapon;

import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.*;
import java.util.function.Consumer;

/** Production gun: shared authoritative combat, configurable visual rig and source timelines. */
public final class ConfiguredNativeGunItem extends Item implements NativeGunItem {
    private final net.minecraft.resources.ResourceLocation definitionId;
    public final NativeAnimatedWeaponItem.Profile profile;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public ConfiguredNativeGunItem(net.minecraft.resources.ResourceLocation definitionId, NativeAnimatedWeaponItem.Profile profile) {
        super(new Properties().stacksTo(1));
        this.definitionId = definitionId; this.profile = profile;
        GeoItem.registerSyncedAnimatable(this);
    }
    @Override public NativeGunDefinition definition() { return NativeGunData.get(definitionId); }
    @Override public String animationAsset() { return profile.id(); }
    @Override public String fireClip(boolean last) { return "shoot"; }
    @Override public String reloadClip(boolean empty) { return empty ? "reload_empty" : "reload_tactical"; }
    @Override public net.minecraft.sounds.SoundEvent fireSound() {
        return Objects.requireNonNull(net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(
                new net.minecraft.resources.ResourceLocation("apocalypse_firstlight", profile.id() + "_fire")));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide) NativeGunAmmo.initialize(stack, definition());
    }
    @Override public boolean shouldCauseReequipAnimation(ItemStack a, ItemStack b, boolean changed) {
        if (changed || a.getItem() != b.getItem() || a.getCount() != b.getCount()) return true;
        var x = NativeGunAmmo.tagWithoutAmmo(a); var y = NativeGunAmmo.tagWithoutAmmo(b);
        if (!x.contains(GeoItem.ID_NBT_KEY)) y.remove(GeoItem.ID_NBT_KEY);
        return !x.equals(y);
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        // Shoot omits hand channels, so the original ready pose remains the underlying layer.
        registrar.add(new AnimationController<>(this, "baseline", 0,
                s -> s.setAndContinue(RawAnimation.begin().thenLoop(profile.idle()))));
        var action = new AnimationController<>(this, "action", 0, s -> {
            var stack = s.getData(software.bernie.geckolib.constant.DataTickets.ITEMSTACK);
            return s.setAndContinue(RawAnimation.begin().thenLoop(stack != null && NativeGunAmmo.read(stack, definition()) == 0
                    ? "static_bolt_caught" : profile.idle()));
        });
        for (String clip : profile.clips()) if (!profile.loops().contains(clip))
            // thenPlay uses the resource's loop default, not an explicit one-shot.
            action.triggerableAnim(clip, RawAnimation.begin().then(clip, Animation.LoopType.PLAY_ONCE));
        registrar.add(action);
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal(NativeGunAmmo.read(stack, definition()) + " / " + definition().magazineCapacity()));
        lines.add(Component.translatable("tooltip.apocalypse_firstlight.br51_01.ammunition"));
    }
    @Override public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState s, Level l,
            net.minecraft.core.BlockPos p, net.minecraft.world.entity.player.Player player) { return false; }
    @Override public boolean onLeftClickEntity(ItemStack s, net.minecraft.world.entity.player.Player p, Entity e) { return true; }
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(
                    net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.InteractionHand hand, ItemStack stack) {
                return hand == net.minecraft.world.InteractionHand.MAIN_HAND
                        ? net.minecraft.client.model.HumanoidModel.ArmPose.CROSSBOW_HOLD : null;
            }
            private com.antaurora.apofirstlight.weapon.client.NativeAnimatedWeaponRenderer<ConfiguredNativeGunItem> renderer;
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new com.antaurora.apofirstlight.weapon.client.NativeAnimatedWeaponRenderer<>(profile);
                return renderer;
            }
        });
    }
}
