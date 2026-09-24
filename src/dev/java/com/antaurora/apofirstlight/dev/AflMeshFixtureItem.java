package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.NativeAnimatedWeaponItem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;

import java.util.List;
import java.util.Set;

/** DEV visual viewer. Uses the shared Native renderer, without registering a combat gun. */
@Mod.EventBusSubscriber(modid=ApocalypseFirstLight.MOD_ID, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class AflMeshFixtureItem extends NativeAnimatedWeaponItem {
    private static final String PAUSED = "AflMeshFixturePaused";

    private AflMeshFixtureItem() {
        super(new Profile("afl_mesh_core_fixture", "static_idle", List.of(), Set.of(),
                "unused_right_anchor", "unused_left_anchor"));
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (FMLEnvironment.production) return;
        event.register(ForgeRegistries.Keys.ITEMS,
                new ResourceLocation(ApocalypseFirstLight.MOD_ID, "afl_mesh_core_fixture"), AflMeshFixtureItem::new);
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "baseline", 0, state -> {
            var stack = state.getData(DataTickets.ITEMSTACK);
            boolean paused = stack != null && stack.hasTag() && stack.getTag().getBoolean(PAUSED);
            return state.setAndContinue(RawAnimation.begin().thenLoop(paused ? "static_idle" : "fixture_move"));
        }));
    }

    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level instanceof ServerLevel server) GeoItem.getOrAssignId(stack, server);
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            var tag = stack.getOrCreateTag();
            boolean paused = !tag.getBoolean(PAUSED);
            tag.putBoolean(PAUSED, paused);
            player.displayClientMessage(Component.literal(paused ? "Mesh fixture: 静态姿态" : "Mesh fixture: 播放测试动画"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override public Component getName(ItemStack stack) {
        return Component.literal("AFL Mesh Core Fixture (DEV)");
    }

    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("1 Cube + 3 Mesh · 右键切换动画/静态"));
        lines.add(Component.literal("仅可视测试；非战斗枪械，不能放入维护台"));
    }
}
