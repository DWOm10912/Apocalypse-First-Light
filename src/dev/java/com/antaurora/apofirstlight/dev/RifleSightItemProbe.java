package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Isolated inventory presentation regression; not included in release jars. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class RifleSightItemProbe {
    private static int ticks;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(!Boolean.getBoolean("afl.rifleSightItemProbe")||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        mc.options.pauseOnLostFocus=false;ticks++;
        if(ticks==140)mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.getInventory().setItem(9,new ItemStack(AflItems.RIFLE_RED_DOT_01.get()));
            p.containerMenu.broadcastChanges();
        });
        if(ticks==200){
            var renderer=net.minecraftforge.client.extensions.common.IClientItemExtensions.of(AflItems.RIFLE_RED_DOT_01.get()).getCustomRenderer();
            if(!(renderer instanceof com.antaurora.apofirstlight.weapon.client.NativeMuzzleRendering.ItemRenderer))throw new IllegalStateException("Missing sight item renderer");
            mc.setScreen(new InventoryScreen(mc.player));
        }
        if(ticks==250){
            Screenshot.grab(mc.gameDirectory,"rifle_red_dot_inventory.png",mc.getMainRenderTarget(),m->{});
            com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[RIFLE SIGHT ITEM] PASS renderer registered; inventory screenshot captured");
            mc.stop();
        }
    }
}
