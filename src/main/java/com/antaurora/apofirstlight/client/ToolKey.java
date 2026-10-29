package com.antaurora.apofirstlight.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tools work with the attack key (user 2026-10-28: "可以让工具改成左键挖掘…做一个通用接口，把工具都改成左键使用，建筑锤这种有特殊
 * 功能的就先不动"): the one place that says which key a tool's work takes and keeps that key from vanilla. A {@link Tool}
 * says whether its work would go where the player aims now and whether it is mid-action:
 * <ul>
 *   <li>while one targets or is busy, the attack key is the tools': no vanilla attack, no block breaking (held or not), no
 *   swing. Each tool starts its own action on the key's press ({@link #down}: one press, one action);</li>
 *   <li>aiming at an entity no tool targets: the attack is a blow as before (a shovel or a pick is a weapon too);</li>
 *   <li>the use key stays vanilla's (hanging a shovel on a tool rack), except mid-action, when nothing else may start.</li>
 * </ul>
 * Tools on it: the shovel (ShovelClient), the pick mattock (PickClient). The felling axe already fells with the attack key
 * (tree/FellingClient). Tools with special functions keep their own keys: the building hammer, the wiring wrench, the
 * crowbar's vending machine smash (a block use: moving it needs a packet of its own), the folding knife's open and fold.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", value = Dist.CLIENT)
public final class ToolKey {
    /** A tool working with the attack key. */
    public interface Tool {
        /** Whether its work would go where the own player aims now (its item in the main hand, its target in reach). */
        boolean targets(Minecraft mc);

        /** Whether the own player is mid-action with it. */
        boolean busy();
    }

    private static final List<Tool> TOOLS = new CopyOnWriteArrayList<>();

    private ToolKey() {}

    public static void register(Tool tool) { TOOLS.add(tool); }

    /** The key tools work with: the player's attack binding. */
    public static KeyMapping key(Minecraft mc) { return mc.options.keyAttack; }

    /** Whether the tool key is down with no screen open (a tool starts on its press: down now, not last tick). */
    public static boolean down(Minecraft mc) { return key(mc).isDown() && mc.screen == null; }

    /** Aiming at an entity: the tool key is a blow there, never a tool's work. */
    public static boolean onEntity(Minecraft mc) { return mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY; }

    private static boolean busy() {
        for (Tool t : TOOLS) if (t.busy()) return true;
        return false;
    }

    private static boolean targets(Minecraft mc) {
        if (mc.player == null || mc.player.isSpectator() || onEntity(mc)) return false;
        for (Tool t : TOOLS) if (t.targets(mc)) return true;
        return false;
    }

    @SubscribeEvent
    public static void input(InputEvent.InteractionKeyMappingTriggered e) {
        var mc = Minecraft.getInstance();
        if (e.isAttack() && (busy() || targets(mc)) || e.isUseItem() && busy()) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }
}
