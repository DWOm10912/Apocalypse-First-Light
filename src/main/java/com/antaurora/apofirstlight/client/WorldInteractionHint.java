package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.BeverageCoolerBlock;
import com.antaurora.apofirstlight.block.BeverageCoolerDoorRaycast;
import com.antaurora.apofirstlight.block.VendingMachineBlock;
import com.antaurora.apofirstlight.meshshape.AflMeshInteractionBlock;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * World interaction prompt (formerly VendingMachineHint): one {@link AttachmentHintStyle} label with a shared fade. Each
 * block decides from the crosshair whether it offers an action and which; the server-side use() applies the same test.
 * The label sits at the crosshair (vending machine), or at a world prompt anchor projected to the screen (Mesh Shape
 * regions, e.g. the locker's lock), falling back to the crosshair when the anchor is behind the camera.
 */
@Mod.EventBusSubscriber(modid=ApocalypseFirstLight.MOD_ID,value=Dist.CLIENT)
public final class WorldInteractionHint {
    private record Target(Component label, Vec3 anchor) {}
    private static float fade;
    private static long last=System.nanoTime();
    private static Component label;
    private static Vec3 anchor;
    private static final Matrix4f VIEW=new Matrix4f(), PROJECTION=new Matrix4f();
    private static Vec3 camera=Vec3.ZERO;

    /** This frame's world view / projection (with view bobbing), for projecting prompt anchors. */
    @SubscribeEvent public static void captureCamera(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_SKY) return;
        VIEW.set(event.getPoseStack().last().pose());PROJECTION.set(event.getProjectionMatrix());camera=event.getCamera().getPosition();
    }

    @SubscribeEvent public static void render(RenderGuiOverlayEvent.Post event) {
        if(!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) return;
        var mc=Minecraft.getInstance();long now=System.nanoTime();
        float step=Math.min(.2f,(now-last)/1_000_000_000f)/AttachmentHintStyle.FADE_SECONDS;last=now;
        if(mc.player==null||mc.level==null||mc.screen!=null||mc.options.hideGui) {fade=0;return;}
        Target target=null;
        if(!mc.player.isSpectator() && mc.hitResult instanceof BlockHitResult hit) {
            target=vendingMachine(mc,hit);
            if(target==null) target=coolerDoor(mc,hit);
            if(target==null) target=meshInteraction(mc,hit);
        }
        if(target!=null) {label=target.label();anchor=target.anchor();}   // keep the last label while fading out
        fade=Math.max(0,Math.min(1,fade+(target!=null?step:-step)));
        if(fade<=.03f||label==null) return;
        int w=event.getWindow().getGuiScaledWidth(),h=event.getWindow().getGuiScaledHeight();
        float[] at=anchor==null?null:project(anchor,w,h);
        AttachmentHintStyle.draw(event.getGuiGraphics(),label,at==null?w/2:Math.round(at[0]),at==null?h/2:Math.round(at[1]),w,fade);
    }

    private static float[] project(Vec3 world,int width,int height) {
        var v=new Vector4f((float)(world.x-camera.x),(float)(world.y-camera.y),(float)(world.z-camera.z),1);
        VIEW.transform(v);PROJECTION.transform(v);
        if(v.w<=.05f) return null;
        return new float[]{(v.x/v.w*.5f+.5f)*width,(.5f-v.y/v.w*.5f)*height};
    }

    private static Target vendingMachine(Minecraft mc,BlockHitResult hit) {
        if(CrowbarSmashClient.active() || !mc.player.getMainHandItem().is(AflItems.CROWBAR.get())) return null;
        var s=mc.level.getBlockState(hit.getBlockPos());
        return s.getBlock() instanceof VendingMachineBlock && !s.getValue(VendingMachineBlock.BROKEN)
                && VendingMachineBlock.frontPoint(s,hit.getBlockPos(),mc.player.getEyePosition(),hit)!=null
                ? new Target(Component.translatable("hint.apocalypse_firstlight.break_glass"),null) : null;
    }

    /**
     * Beverage cooler doors, resolved like a click (BeverageCoolerDoorInput first ray-tests open leaves, also where they
     * swing out of their cells, then the vanilla hit): open / close, drawn at the door's pull handle.
     */
    private static Target coolerDoor(Minecraft mc,BlockHitResult hit) {
        Vec3 eye=mc.player.getEyePosition();
        Vec3 end=eye.add(mc.player.getViewVector(1.0F).scale(mc.gameMode==null?4.5:mc.gameMode.getPickRange()));
        var leaf=BeverageCoolerDoorRaycast.find(mc.level,mc.player,eye,end);
        if(leaf!=null) {
            var facing=mc.level.getBlockState(leaf.master()).getValue(BeverageCoolerBlock.FACING);
            return new Target(Component.translatable("hint.apocalypse_firstlight.beverage_cooler.close"),
                    BeverageCoolerBlock.promptAnchor(leaf.master(),facing,leaf.left(),true));
        }
        if(hit.getType()!=HitResult.Type.BLOCK) return null;
        var s=mc.level.getBlockState(hit.getBlockPos());
        if(!(s.getBlock() instanceof BeverageCoolerBlock)) return null;
        var door=BeverageCoolerBlock.promptDoor(mc.level,hit.getBlockPos(),s,hit.getLocation());
        return door==null?null:new Target(Component.translatable("hint.apocalypse_firstlight.beverage_cooler."+(door.open()?"close":"open")),
                BeverageCoolerBlock.promptAnchor(door.master(),door.facing(),door.left(),door.open()));
    }

    /** Any Mesh Shape interaction block: aimed region + the block's own state -> prompt, drawn at the region's anchor. */
    private static Target meshInteraction(Minecraft mc,BlockHitResult hit) {
        var s=mc.level.getBlockState(hit.getBlockPos());
        if(!(s.getBlock() instanceof AflMeshInteractionBlock block)) return null;
        var region=block.meshInteraction(s,hit.getBlockPos(),mc.player);
        if(region==null) return null;
        var key=block.interactionHintKey(mc.level,s,hit.getBlockPos(),region.region());
        return key==null?null:new Target(Component.translatable(key),region.anchor());
    }
}
