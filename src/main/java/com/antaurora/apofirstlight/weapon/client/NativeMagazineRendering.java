package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.weapon.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.item.*;
import net.minecraft.client.renderer.*;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;

/**
 * Uses the original animated bone matrix; the independent accessory is authored around its pivot.
 * <p>
 * A gun that loads from magazine items (NativeMagazines; docs/native_guns/magazine_system_v1.md 5): each magazine bone
 * shows its own magazine, {@link #FITTED} the one in the gun, {@link #INCOMING} a reload's new one, {@link #OUTGOING} the
 * one that comes out. A magazine that replaces the bone (the extended one) is drawn there; the standard one is the gun's
 * own bone; no magazine hides the bone and what hangs under it (follower, top round).
 */
public final class NativeMagazineRendering {
    /** The P9-01 rig's magazine bones (geo p9_01_v3_native). */
    static final String FITTED = "magazine", INCOMING = "reload_magazine", OUTGOING = "empty_old_mag";

    private static NativeMagazines.Role role(String bone) {
        return switch (bone) {
            case FITTED -> NativeMagazines.Role.FITTED;
            case INCOMING -> NativeMagazines.Role.INCOMING;
            case OUTGOING -> NativeMagazines.Role.OUTGOING;
            default -> null;
        };
    }

    /** The magazine a bone of a magazine-item gun shows, or null when the gun is not one or the bone is no magazine bone. */
    private static NativeMagazines.Shown shown(ItemStack gun, GeoBone bone) {
        if (gun == null || !(gun.getItem() instanceof NativeGunItem g) || !NativeMagazines.system(g.definition())) return null;
        var role = role(bone.getName());
        return role == null ? null : NativeMagazines.shown(gun, g.definition(), role);
    }

    public static boolean replaces(ItemStack gun,GeoBone bone){
        if (gun != null && NativeMagazines.system(gun)) {
            var shown = shown(gun, bone);
            return shown != null && (shown.magazine().isEmpty()
                    || shown.magazine().getItem() instanceof NativeMagazineItem m && m.replacesBone(bone.getName()));
        }
        return gun!=null&&NativeAttachments.active(gun,NativeAttachment.Slot.MAGAZINE).getItem() instanceof NativeMagazineItem m
                &&m.replacesBone(bone.getName());
    }
    public static boolean replacesSubtree(ItemStack gun,GeoBone bone){
        if (gun != null && NativeMagazines.system(gun)) {
            var shown = shown(gun, bone);
            return shown != null && (shown.magazine().isEmpty()
                    || shown.magazine().getItem() instanceof NativeMagazineItem m && m.replacesSubtree());
        }
        return NativeAttachments.active(gun,NativeAttachment.Slot.MAGAZINE).getItem() instanceof NativeMagazineItem m&&m.replacesSubtree();
    }
    public static void render(ItemStack gun,GeoBone bone,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(!replaces(gun,bone))return;
        ItemStack magazine;
        if (NativeMagazines.system(gun)) {
            var shown = shown(gun, bone);
            if (shown == null || shown.magazine().isEmpty()) return;
            magazine = shown.magazine();
        } else magazine = NativeAttachments.active(gun,NativeAttachment.Slot.MAGAZINE);
        pose.pushPose();RenderUtils.translateToPivotPoint(pose,bone);
        NativeMuzzleRendering.drawItem(magazine,pose,buffers,light,overlay);
        pose.popPose();
    }

    /**
     * A loaded standalone magazine shows its top rounds where the gun's magazine shows them: the gun's
     * magazine_round_visual.item_rounds (P9-01 one, BR-51 two of the double stack), as many as the magazine holds.
     */
    private static void topRound(ItemStack stack,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(!(stack.getItem() instanceof NativeMagazineItem m))return;
        int rounds=NativeMagazines.rounds(stack);
        var gun=rounds<=0?null:m.roundGun();
        var visual=gun==null?null:gun.magazineRoundVisual();
        if(visual==null)return;
        float[] offset=visual.localOffset(),rotation=visual.localRotation();
        for(int i=0;i<visual.itemRounds().size()&&i<rounds;i++){
            float[] round=visual.itemRounds().get(i);
            pose.pushPose();
            pose.translate(round[0]/16F,round[1]/16F,round[2]/16F);
            if(round[3]!=0)pose.mulPose(Axis.XP.rotationDegrees(round[3]));
            pose.translate(offset[0]/16F,offset[1]/16F,offset[2]/16F);
            if(rotation[0]!=0)pose.mulPose(Axis.XP.rotationDegrees(rotation[0]));
            if(rotation[1]!=0)pose.mulPose(Axis.YP.rotationDegrees(rotation[1]));
            if(rotation[2]!=0)pose.mulPose(Axis.ZP.rotationDegrees(rotation[2]));
            if(!com.antaurora.apofirstlight.client.MeshItemCache.draw(visual.geometry(),visual.texture(),pose,buffers,light,overlay))
                com.antaurora.vextris.compat.geckolib.GeckoMeshRenderer.render(visual.geometry(),visual.texture(),pose,buffers,light,overlay);
            pose.popPose();
        }
    }

    public static final class ItemRenderer extends BlockEntityWithoutLevelRenderer {
        public ItemRenderer(){super(net.minecraft.client.Minecraft.getInstance().getBlockEntityRenderDispatcher(),net.minecraft.client.Minecraft.getInstance().getEntityModels());}
        @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
            float lift=stack.getItem() instanceof NativeMagazineItem m?m.itemLift():0;
            float tilt=stack.getItem() instanceof NativeMagazineItem t?t.itemTilt():0;
            // Inventory: the authored tilt still straightens the magazine, then one orientation and size for every
            // attachment (NativeAttachmentGuiFit) replaces the per-magazine lift.
            boolean gui=context==ItemDisplayContext.GUI;
            pose.pushPose();pose.translate(.5,.5+(gui?0:lift/16),.5);
            if(tilt!=0)pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(tilt));
            if(gui)NativeAttachmentGuiFit.apply(stack,pose);
            NativeMuzzleRendering.drawItem(stack,pose,buffers,light,overlay);
            topRound(stack,pose,buffers,light,overlay);
            pose.popPose();
        }
    }
    private NativeMagazineRendering(){}
}
