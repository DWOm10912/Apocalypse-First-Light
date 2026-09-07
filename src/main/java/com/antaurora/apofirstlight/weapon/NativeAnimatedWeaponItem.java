package com.antaurora.apofirstlight.weapon;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.*;
import java.util.function.Consumer;

/** Configurable animated-asset integration, deliberately separate from ballistic gun definitions. */
public class NativeAnimatedWeaponItem extends Item implements GeoItem {
    public record Profile(String id, String idle, List<String> clips, Set<String> loops,
                          String rightAnchor, String leftAnchor, String muzzleAnchor, String ejectionAnchor,
                          float barrelExitOffset) {
        public Profile(String id, String idle, List<String> clips, Set<String> loops,
                       String rightAnchor, String leftAnchor, String muzzleAnchor, String ejectionAnchor) {
            this(id, idle, clips, loops, rightAnchor, leftAnchor, muzzleAnchor, ejectionAnchor, 0);
        }
        public Profile(String id, String idle, List<String> clips, Set<String> loops, String rightAnchor, String leftAnchor) {
            this(id, idle, clips, loops, rightAnchor, leftAnchor, null, null);
        }
        public ResourceLocation resource(String folder, String suffix) {
            return new ResourceLocation("apocalypse_firstlight", folder + "/" + id + suffix);
        }
    }
    public final Profile profile;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public NativeAnimatedWeaponItem(Profile profile) {
        super(new Properties().stacksTo(1));
        this.profile = profile;
        GeoItem.registerSyncedAnimatable(this);
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Idle supplies channels absent from a one-shot (notably shoot's hand transforms).
        controllers.add(new AnimationController<>(this, "baseline", 0,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop(profile.idle()))));
        var action = new AnimationController<>(this, "action", 0, state -> PlayState.STOP);
        for (String clip : profile.clips()) action.triggerableAnim(clip, profile.loops().contains(clip)
                ? RawAnimation.begin().thenLoop(clip) : RawAnimation.begin().thenPlay(clip));
        controllers.add(action);
    }
    @Override public boolean canAttackBlock(BlockState s, Level l, BlockPos p, Player player) { return false; }
    @Override public boolean onLeftClickEntity(ItemStack s, Player p, Entity e) { return true; }
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private com.antaurora.apofirstlight.weapon.client.NativeAnimatedWeaponRenderer renderer;
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new com.antaurora.apofirstlight.weapon.client.NativeAnimatedWeaponRenderer(profile);
                return renderer;
            }
        });
    }
}
