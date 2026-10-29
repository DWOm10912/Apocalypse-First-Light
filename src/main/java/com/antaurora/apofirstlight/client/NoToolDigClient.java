package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.earthwork.Earthwork;
import com.antaurora.apofirstlight.world.VanillaRetirementEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Vanilla Survival Rework V1.8 (docs/gameplay/vanilla_survival_rework_v1.md 4.12, user 2026-10-23): with no tool in the main
 * hand a left click on a block does nothing at all, the first click and the held key alike: no breaking begins (no crack, no
 * hit sound, no particles), the arm does not swing, nothing goes to the server. A break already under way (the tool swapped
 * out with the key held) is aborted. Last, so whatever takes the click for itself first (guns, doors, the crowbar's smash,
 * the orbit camera's turn, a tool's work: ToolKey) still has it; entities and empty air are struck as before. The server
 * refuses the same ({@link VanillaRetirementEvents#cannotDig}).
 * <p>
 * Terrain rework R4 round 2 (user 2026-10-28: "工具貌似没有拦截原版挖掘的音效和粒子呢，只有空手才拦截"): the same with a tool in
 * hand on a block no tool breaks in survival (Earthwork#unbreakable: loose ground, natural rock, edited ground; a road's
 * pavement, PavementMining; a boulder), and the click says what it takes instead (the server's message, which no longer
 * gets the click: Earthwork#hit, RockFace#hit, PavementMining#hit), once a press; nothing with the right tool in hand (its
 * work is out of reach).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class NoToolDigClient {
    /** the game tick of the last "it takes a shovel / a pick" (once a press: the held key repeats the click every tick) */
    private static long toldAt = Long.MIN_VALUE;

    private NoToolDigClient() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void attack(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || mc.player == null || mc.level == null || mc.gameMode == null
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        var state = mc.level.getBlockState(hit.getBlockPos());
        if (state.isAir()) return;
        boolean noTool = VanillaRetirementEvents.cannotDig(mc.player), survival = !mc.player.isCreative() && !mc.player.isSpectator();
        boolean road = survival && com.antaurora.apofirstlight.boulder.PavementMining.pavement(state);
        boolean boulder = survival && com.antaurora.apofirstlight.boulder.BoulderBlocks.ready() && com.antaurora.apofirstlight.boulder.BoulderBlocks.isBoulder(state);
        if (!noTool && !road && !boulder && !Earthwork.unbreakable(mc.player, mc.level, hit.getBlockPos(), state)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (mc.gameMode.isDestroying()) mc.gameMode.stopDestroyBlock();
        long now = mc.level.getGameTime();
        var held = mc.player.getMainHandItem().getItem();
        boolean rock = road || boulder || Earthwork.rock(state.getBlock());
        // the right tool in hand, out of reach: nothing to tell
        boolean rightTool = rock ? held instanceof com.antaurora.apofirstlight.item.PickMattockItem : held instanceof com.antaurora.apofirstlight.item.ShovelItem;
        boolean repeat = now - toldAt <= 1;
        toldAt = now;
        if (noTool || repeat || rightTool) return;
        mc.player.displayClientMessage(Component.translatable(road ? "message.apocalypse_firstlight.pick.needs_pick_road"
                : rock ? "message.apocalypse_firstlight.pick.needs_pick" : "message.apocalypse_firstlight.earthwork.needs_shovel"), true);
    }
}
