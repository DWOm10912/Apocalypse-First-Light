package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.client.MaintenanceHotspots;
import com.antaurora.apofirstlight.weapon.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;
import org.joml.Vector4f;
import java.util.*;

/** Collect only slot anchor matches during the existing traversal, never walk a second model tree. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class FieldAttachmentHotspots {
    private record Hit(MaintenanceHotspots.Point point,boolean preferred){}
    private static final Map<NativeAttachment.Slot,Hit> HITS=new EnumMap<>(NativeAttachment.Slot.class);
    public static void clear(){HITS.clear();}
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e){if(e.phase==TickEvent.Phase.START)clear();}
    public static MaintenanceHotspots.Point project(NativeAttachment.Slot slot){
        var hit=HITS.get(slot);return hit==null?null:hit.point();
    }
    public static void capture(ItemStack stack,GeoBone bone,PoseStack incoming){
        if(!FieldAttachmentViewState.matches(stack))return;
        for(var slot:NativeAttachment.Slot.values()){
            var d=AttachmentHotspotDefinition.forSlot(stack,slot);if(d==null)continue;
            boolean preferred=bone.getName().equals(d.preferred());
            if(!preferred&&!bone.getName().equals(d.fallback()))continue;
            var previous=HITS.get(slot);if(previous!=null&&previous.preferred()&&!preferred)continue;
            var pose=P901RenderMatrices.detachedCopy(incoming);
            RenderUtils.prepMatrixForBone(pose,bone);RenderUtils.translateToPivotPoint(pose,bone);
            var point=new Vector4f(preferred?0:d.x(),preferred?0:d.y(),preferred?0:d.z(),1);
            pose.last().pose().transform(point);
            RenderSystem.getProjectionMatrix().transform(point);
            if(!Float.isFinite(point.x+point.y+point.z+point.w)||point.w<=.0001f)continue;
            var window=Minecraft.getInstance().getWindow();
            double x=(point.x/point.w+1)*.5*window.getGuiScaledWidth();
            double y=(1-point.y/point.w)*.5*window.getGuiScaledHeight();
            if(Math.abs(point.x/point.w)>1||Math.abs(point.y/point.w)>1)continue;
            HITS.put(slot,new Hit(new MaintenanceHotspots.Point(x,y),preferred));
        }
    }
    private FieldAttachmentHotspots(){}
}
