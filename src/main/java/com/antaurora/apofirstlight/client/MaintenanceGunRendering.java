package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.apofirstlight.weapon.client.NativeSightRendering;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.util.RenderUtils;
import java.util.*;

/** Shared cached native cubes in immutable bind pose: no controller, arm layer, or render identity writes. */
public final class MaintenanceGunRendering implements GeoRenderer<GeoItem> {
    private static final MaintenanceGunRendering DRAWER=new MaintenanceGunRendering();
    private static final Map<BakedGeoModel,List<GeoBone>> CACHE=new WeakHashMap<>();
    private static final Map<BakedGeoModel,List<net.minecraft.world.phys.AABB>> BOUNDS=new WeakHashMap<>();
    private static BakedGeoModel model(ItemStack stack){
        if(!(stack.getItem() instanceof NativeGunItem gun))return null;
        var id=gun.definition().id();return GeckoLibCache.getBakedModels().get(new ResourceLocation(id.getNamespace(),"geo/"+id.getPath()+".geo.json"));
    }
    private static List<GeoBone> bones(ItemStack stack,BakedGeoModel model){return CACHE.computeIfAbsent(model,m->{
        // InitialSnapshot is populated lazily by animation controllers, so it can be null before first use.
        // Read authored rotations once per resource generation, never transient shared live bone rotations.
        var rotations=new HashMap<String,float[]>();var id=((NativeGunItem)stack.getItem()).definition().id();
        try(var reader=net.minecraft.client.Minecraft.getInstance().getResourceManager().openAsReader(new ResourceLocation(id.getNamespace(),"geo/"+id.getPath()+".geo.json"))){
            var json=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            for(var element:json.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")){
                var bone=element.getAsJsonObject();var r=bone.getAsJsonArray("rotation");
                if(r!=null)rotations.put(bone.get("name").getAsString(),new float[]{(float)Math.toRadians(-r.get(0).getAsDouble()),(float)Math.toRadians(-r.get(1).getAsDouble()),(float)Math.toRadians(r.get(2).getAsDouble())});
            }
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot load maintenance bind pose for "+id,e);}
        return m.topLevelBones().stream().map(b->copy(b,null,rotations)).filter(Objects::nonNull).toList();
    });}
    public static void transform(ItemStack stack,PoseStack pose){
        var p=MaintenanceViewProfile.of(stack);pose.translate(p.offsetX(),p.offsetY(),p.offsetZ());
        pose.mulPose(Axis.YP.rotationDegrees(p.rotationY()));pose.mulPose(Axis.XP.rotationDegrees(p.rotationX()));pose.mulPose(Axis.ZP.rotationDegrees(p.rotationZ()));
        pose.scale(p.scale(),p.scale(),p.scale());pose.translate(-p.centerX(),-p.centerY(),-p.centerZ());
    }
    public static List<net.minecraft.world.phys.AABB> bounds(ItemStack stack){
        var model=model(stack);if(model==null)return List.of();
        return BOUNDS.computeIfAbsent(model,m->{var result=new ArrayList<net.minecraft.world.phys.AABB>();var pose=new PoseStack();for(var b:bones(stack,m))collect(b,pose,result);return List.copyOf(result);});
    }
    private static void collect(GeoBone bone,PoseStack pose,List<net.minecraft.world.phys.AABB> result){
        pose.pushPose();RenderUtils.prepMatrixForBone(pose,bone);
        for(var cube:bone.getCubes()){
            pose.pushPose();RenderUtils.translateToPivotPoint(pose,cube);RenderUtils.rotateMatrixAroundCube(pose,cube);RenderUtils.translateAwayFromPivotPoint(pose,cube);
            double x=Double.POSITIVE_INFINITY,y=x,z=x,xx=-x,yy=-x,zz=-x;
            for(var q:cube.quads())if(q!=null)for(var v:q.vertices()){
                var p=pose.last().pose().transformPosition(new org.joml.Vector3f(v.position()));
                x=Math.min(x,p.x);y=Math.min(y,p.y);z=Math.min(z,p.z);xx=Math.max(xx,p.x);yy=Math.max(yy,p.y);zz=Math.max(zz,p.z);
            }
            if(Double.isFinite(x))result.add(new net.minecraft.world.phys.AABB(x,y,z,xx,yy,zz));pose.popPose();
        }
        for(var b:bone.getChildBones())collect(b,pose,result);pose.popPose();
    }
    public static void render(ItemStack stack,PoseStack pose,MultiBufferSource buffers,int light) {
        if(!(stack.getItem() instanceof NativeGunItem gun))return;
        var id=gun.definition().id();var model=GeckoLibCache.getBakedModels().get(new ResourceLocation(id.getNamespace(),"geo/"+id.getPath()+".geo.json"));
        if(model==null)return;
        var bones=bones(stack,model);
        pose.pushPose();transform(stack,pose);
        var type=RenderType.entityCutoutNoCull(new ResourceLocation(id.getNamespace(),"textures/item/"+id.getPath()+".png"));
        for(var bone:bones)draw(bone,stack,pose,buffers,type,light);
        pose.popPose();
    }
    private static GeoBone copy(GeoBone source,GeoBone parent,Map<String,float[]> rotations) {
        String n=source.getName();
        if(n.startsWith("empty_old") || Set.of("reload_magazine","additional_magazine","lefthand","righthand","camera","view","positioning").contains(n))return null;
        var b=new GeoBone(parent,n,source.getMirror(),source.getInflate(),source.getReset(),source.shouldNeverRender());
        b.setPivotX(source.getPivotX());b.setPivotY(source.getPivotY());b.setPivotZ(source.getPivotZ());
        var initial=rotations.get(n);
        if(initial!=null && !n.equals("fp_root")) {
            b.setRotX(initial[0]);b.setRotY(initial[1]);b.setRotZ(initial[2]);
        }
        b.getCubes().addAll(source.getCubes());
        for(var child:source.getChildBones()){var c=copy(child,b,rotations);if(c!=null)b.getChildBones().add(c);}return b;
    }
    private static void draw(GeoBone b,ItemStack stack,PoseStack p,MultiBufferSource buffers,RenderType type,int light) {
        p.pushPose();RenderUtils.prepMatrixForBone(p,b);
        for(var cube:b.getCubes()) {
            p.pushPose();DRAWER.renderCube(p,cube,buffers.getBuffer(type),light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,1,1,1,1);p.popPose();
        }
        NativeSightRendering.render(stack,b,p,buffers,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        for(var child:b.getChildBones())draw(child,stack,p,buffers,type,light);
        p.popPose();
    }
    @Override public GeoModel<GeoItem> getGeoModel(){return null;}
    @Override public GeoItem getAnimatable(){return null;}
    @Override public void fireCompileRenderLayersEvent(){}
    @Override public boolean firePreRenderEvent(PoseStack p,BakedGeoModel m,MultiBufferSource b,float t,int l){return true;}
    @Override public void firePostRenderEvent(PoseStack p,BakedGeoModel m,MultiBufferSource b,float t,int l){}
    @Override public void updateAnimatedTextureFrame(GeoItem item){}
}
