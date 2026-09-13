package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.VendingMachineBlock;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=ApocalypseFirstLight.MOD_ID,value=Dist.CLIENT)
public final class VendingMachineHint {
    private static float fade;
    private static long last=System.nanoTime();
    @SubscribeEvent public static void render(RenderGuiOverlayEvent.Post event) {
        if(!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) return;
        var mc=Minecraft.getInstance();long now=System.nanoTime();
        float step=Math.min(.2f,(now-last)/1_000_000_000f)/AttachmentHintStyle.FADE_SECONDS;last=now;
        if(mc.player==null||mc.level==null||mc.screen!=null||mc.options.hideGui) {fade=0;return;}
        boolean show=false;
        if(!mc.player.isSpectator() && !CrowbarSmashClient.active() && mc.player.getMainHandItem().is(AflItems.CROWBAR.get())
                && mc.hitResult instanceof BlockHitResult hit) {
            var s=mc.level.getBlockState(hit.getBlockPos());
            show=s.getBlock() instanceof VendingMachineBlock && !s.getValue(VendingMachineBlock.BROKEN)
                    && VendingMachineBlock.frontPoint(s,hit.getBlockPos(),mc.player.getEyePosition(),hit)!=null;
        }
        fade=Math.max(0,Math.min(1,fade+(show?step:-step)));
        if(fade>.03f) AttachmentHintStyle.draw(event.getGuiGraphics(),Component.translatable("hint.apocalypse_firstlight.break_glass"),
                event.getWindow().getGuiScaledWidth()/2,event.getWindow().getGuiScaledHeight()/2,event.getWindow().getGuiScaledWidth(),fade);
    }
}
