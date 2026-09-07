package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.ServicePistolAnimationController;
import com.antaurora.apofirstlight.weapon.ServicePistolItem;
import com.antaurora.apofirstlight.weapon.client.*;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.RenderUtils;
import java.nio.file.*;
import java.util.*;

/** DEV: real Vanilla emitted vertices versus saved bbmodel reference; never draws pixels. */
public final class NativeHandContractChecks {
    private static final float EPS=1e-5F;
    private final Map<String,JsonObject> groups=new HashMap<>(), byId=new HashMap<>();
    private final Map<String,String> parents=new HashMap<>();
    private final Map<String,String> elementParents=new HashMap<>();
    private final Map<String,JsonObject> cubes=new HashMap<>();
    private float maxClassic, maxSlim, gripDrift;
    private int comparisons, frames;
    private JsonObject runtimeDisplay;
    private float previewDisplayScale;
    private float maxScaleError, maxContactError;
    private JsonArray reloadFpKeys;

    private NativeHandContractChecks() throws java.io.IOException {
        Path root=Path.of("").toAbsolutePath();
        if (root.getFileName().toString().equals("run")) root=root.getParent();
        var source=JsonParser.parseString(Files.readString(root.resolve(
                "src/main/blockbench/service_pistol_v03_8_fire_slide_cleanup.bbmodel"))).getAsJsonObject();
        previewDisplayScale=source.getAsJsonObject("display").getAsJsonObject("firstperson_righthand")
                .getAsJsonArray("scale").get(0).getAsFloat();
        for (var e:source.getAsJsonArray("groups")) {
            var g=e.getAsJsonObject(); groups.put(g.get("name").getAsString(),g);
            byId.put(g.get("uuid").getAsString(),g);
        }
        outline(source.getAsJsonArray("outliner"),null);
        for(var entry:source.getAsJsonArray("animations")) {
            var clip=entry.getAsJsonObject();
            if(clip.get("name").getAsString().equals("animation.service_pistol.reload"))
                reloadFpKeys=clip.getAsJsonObject("animators").getAsJsonObject(groups.get("fp_root").get("uuid").getAsString()).getAsJsonArray("keyframes");
        }
        for (var e:source.getAsJsonArray("elements")) {
            var c=e.getAsJsonObject(); cubes.put(c.get("name").getAsString(),c);
        }
    }

    private void outline(JsonArray nodes,String parent) {
        for (var e:nodes) if (e.isJsonObject()) {
            var n=e.getAsJsonObject(); var g=byId.get(n.get("uuid").getAsString());
            String name=g.get("name").getAsString(); parents.put(name,parent);
            outline(n.getAsJsonArray("children"),name);
        } else elementParents.put(e.getAsString(),parent);
    }

    public static void verify(Minecraft mc,ServicePistolItem item) throws java.io.IOException {
        var test=new NativeHandContractChecks();
        var model=new ServicePistolModel(); model.getBakedModel(model.getModelResource(item));
        var before=new HashMap<software.bernie.geckolib.core.animatable.model.CoreGeoBone,float[]>();
        for(var b:model.getAnimationProcessor().getRegisteredBones())
            before.put(b,new float[]{b.getRotX(),b.getRotY(),b.getRotZ(),b.getPosX(),b.getPosY(),b.getPosZ(),b.getScaleX(),b.getScaleY(),b.getScaleZ()});
        try {
            test.run(mc,item);
        } finally {
            before.forEach((b,v)->{
                b.updateRotation(v[0],v[1],v[2]);b.updatePosition(v[3],v[4],v[5]);b.updateScale(v[6],v[7],v[8]);
            });
        }
        ApocalypseFirstLight.LOGGER.info("[AFL HAND PHYSICAL] NUMERICAL PASS: Classic maxError={}, Slim maxError={}, epsilon={}, correspondences={}, frames={}, rightGripDrift={}; No visibility changes. USER VISUAL = PENDING_USER",
                test.maxClassic,test.maxSlim,EPS,test.comparisons,test.frames,test.gripDrift);
    }

    private void run(Minecraft mc,ServicePistolItem item) {
        runtimeDisplay=software.bernie.geckolib.loading.FileLoader.loadFile(
                new net.minecraft.resources.ResourceLocation(ApocalypseFirstLight.MOD_ID,"models/item/service_pistol_in_hand.json"),
                mc.getResourceManager()).getAsJsonObject("display");
        var model=new ServicePistolModel(); model.getBakedModel(model.getModelResource(item));
        var processor=model.getAnimationProcessor();
        var skins=new ModelPart[]{mc.getEntityModels().bakeLayer(ModelLayers.PLAYER),
                mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM)};
        for (String side:new String[]{"right","left"}) {
            var ref=groups.get(side+"_arm_reference"); var cube=cubes.get(side+"_arm_reference_cube");
            require(!ref.get("export").getAsBoolean() && !cube.get("export").getAsBoolean(),"source-only reference");
            require(model.getBone(side+"_arm_reference").isEmpty(),"no runtime reference");
            require(parents.get(side+"_arm_reference").equals(side+"_hand_anchor"),"reference parent");
            require(vector(ref,"rotation").length()<EPS,"reference binding rotation is identity");
            var size=vector(cube,"to").sub(vector(cube,"from"));
            require(size.distance(new Vector3f(4*NativePlayerArmRenderer.PRESENTATION_X,
                    12*NativePlayerArmRenderer.PRESENTATION_Y,4*NativePlayerArmRenderer.PRESENTATION_Z)
                    .div(previewDisplayScale))<EPS,"source-only presentation of full Classic reference");
            require(vector(ref,"origin").distance(vector(groups.get(side+"_hand_anchor"),"origin"))<EPS,"reference/locator same origin");
        }
        for (var g:groups.values()) if (g.get("export").getAsBoolean()) {
            String name=g.get("name").getAsString(); var b=model.getBone(name).orElseThrow();
            require(new Vector3f(b.getPivotX(),b.getPivotY(),b.getPivotZ()).distance(vector(g,"origin"))<EPS,"source/runtime pivot "+name);
            String parent=b.getParent()==null ? null : b.getParent().getName();
            require(Objects.equals(parent,parents.get(name)),"source/runtime parent "+name);
            var r=vector(g,"rotation").mul((float)(Math.PI/180));
            require(r.distance(new Vector3f(b.getRotX(),b.getRotY(),b.getRotZ()))<EPS,"source/runtime rest rotation "+name);
        }
        for (boolean right:new boolean[]{true,false}) for(float scale:new float[]{.30F,.41F,.50F,.80F,1F}) {
            var base=new PoseStack();
            base.translate(.1,-.2,-.6);
            base.mulPose(new Quaternionf().rotationZYX(.2F,-.3F,.1F));
            base.scale(scale,scale,scale);
            for(String side:new String[]{"right","left"}) {
                var anchor=locator(model,side+"_hand_anchor",base);
                var frozen=new Matrix4f(anchor.last().pose());
                var bound=NativePlayerArmRenderer.canonicalPose(anchor);
                require(bound!=null,"uniform parent supported");
                var m=bound.last().pose();
                var actual=new Vector3f(m.getColumn(0,new org.joml.Vector4f()).length(),
                        m.getColumn(1,new org.joml.Vector4f()).length(),m.getColumn(2,new org.joml.Vector4f()).length());
                maxScaleError=Math.max(maxScaleError,actual.distance(new Vector3f(
                        NativePlayerArmRenderer.PRESENTATION_X,NativePlayerArmRenderer.PRESENTATION_Y,NativePlayerArmRenderer.PRESENTATION_Z)));
                require(maxScaleError<EPS,"universal final axes across parent scales");
                for(boolean slim:new boolean[]{false,true}) {
                    var skinPose=ServicePistolRenderMatrices.detachedCopy(bound);
                    NativeHandBinding.apply(skinPose,side.equals("right"),slim);
                    var contact=skinPose.last().pose().transformPosition(new Vector3f(
                            NativeHandBinding.centreX(side.equals("right"),slim),10,0).div(16));
                    maxContactError=Math.max(maxContactError,contact.distance(frozen.getTranslation(new Vector3f())));
                }
                require(error(frozen,anchor.last().pose())==0,"caller matrix untouched");
            }
        }
        require(maxContactError<EPS,"cap-centred presentation contact drift");
        var invalid=new PoseStack();invalid.scale(.4F,.5F,.4F);
        require(NativePlayerArmRenderer.canonicalPose(invalid)==null,"reject unsupported anisotropic parent; never orthogonalize it");
        ApocalypseFirstLight.LOGGER.info("[AFL HAND V051 SCALE] PASS parents=.30/.41/.50/.80/1.00 axes={}/{}/{} maxScaleError={} contactDrift={}",
                NativePlayerArmRenderer.PRESENTATION_X,NativePlayerArmRenderer.PRESENTATION_Y,NativePlayerArmRenderer.PRESENTATION_Z,maxScaleError,maxContactError);
        for(boolean right:new boolean[]{true,false}) {
            var base=new PoseStack(); applyRuntimeDisplay(base,right); compareBoth(model,skins,base);
            verifyReadyAimline(model,base,right);
        }
        var manager=new AnimatableManager<ServicePistolItem>(item);
        double tick=0; int actions=0;
        Matrix4f expectedGrip=relativeGrip(model,new PoseStack());
        Matrix4f readyGun=boneMatrix(model,"gun",new PoseStack());
        Matrix4f readyRight=locator(model,"right_hand_anchor",new PoseStack()).last().pose();
        Matrix4f readyLeft=locator(model,"left_hand_anchor",new PoseStack()).last().pose();
        try {
            for (int cycle=0;cycle<8;cycle++) for (String action:new String[]{"fire","fire","reload","fire","reload"}) {
                manager.tryTriggerAnimation(ServicePistolItem.CONTROLLER,action);
                int steps=action.equals("reload")?320:90;
                double[] marks={0,.04,.08,.12,.16,.20,.24,.4,.42,.60,.85,.93,1.30}; int next=0;
                Vector3f previousLift=new Vector3f(),previousTurn=new Vector3f();
                double previousSample=0;
                for (int step=0;step<=steps;step++,tick+=.1) {
                    processor.tickAnimation(item,model,manager,tick,new AnimationState<>(item,0,0,0,false),true);
                    var controller=(ServicePistolAnimationController)manager.getAnimationControllers().get(ServicePistolItem.CONTROLLER);
                    double seconds=controller.getReloadSeconds();
                    var base=new PoseStack();
                    applyRuntimeDisplay(base,true);
                    var frozen=boneMatrix(model,"gun",base);
                    compareBoth(model,skins,base);
                    require(error(frozen,boneMatrix(model,"gun",base))==0,"arm pass leaves gun untouched");
                    gripDrift=Math.max(gripDrift,error(expectedGrip,relativeGrip(model,base)));
                    require(gripDrift<EPS,"grip relative matrix drift");
                    if (cycle==0 && action.equals("reload") && seconds>=0 && next<marks.length && seconds+1e-6>=marks[next]) {
                        if(marks[next]<=.24) {
                            var fp=model.getBone("fp_root").orElseThrow();
                            var lift=new Vector3f(-fp.getPosX(),fp.getPosY(),fp.getPosZ());
                            var turn=new Vector3f(fp.getRotX(),fp.getRotY(),fp.getRotZ()).mul((float)(180/Math.PI));
                            require(lift.distance(sampleSourceEntrance("position",seconds))<.0005F,"Gecko/source entrance position at "+seconds);
                            require(turn.distance(sampleSourceEntrance("rotation",seconds))<.0005F,"Gecko/source entrance rotation at "+seconds);
                            double dt=seconds-previousSample;
                            ApocalypseFirstLight.LOGGER.info("[AFL V052 ENTRANCE] target={} actual={} sourceUnits={} degrees={} speedUnitsPerSec={} speedDegreesPerSec={}; evaluated Gecko frame, NOT visual PASS",
                                    marks[next],seconds,lift,turn,dt>1e-6?lift.distance(previousLift)/dt:0,dt>1e-6?turn.distance(previousTurn)/dt:0);
                            previousLift.set(lift);previousTurn.set(turn);previousSample=seconds;
                        }
                        var left=locator(model,"left_hand_anchor",new PoseStack()).last().pose().getTranslation(new Vector3f()).mul(16);
                        var mag=locator(model,"magazine",new PoseStack()).last().pose().getTranslation(new Vector3f()).mul(16);
                        var guide=locator(model,"reload_magazine",new PoseStack()).last().pose().getTranslation(new Vector3f()).mul(16);
                        ApocalypseFirstLight.LOGGER.info("[AFL HAND V05 SYNC] target={} actual={} left={} mag={} guide={} magScale={}; same evaluated frame, geometry contact requires user review",marks[next],seconds,left,mag,guide,model.getBone("magazine").orElseThrow().getScaleY());
                        next++;
                    }
                    frames++;
                }
                require(error(readyGun,boneMatrix(model,"gun",new PoseStack()))<EPS,"gun returns READY");
                require(error(readyRight,locator(model,"right_hand_anchor",new PoseStack()).last().pose())<EPS,"right returns READY");
                require(error(readyLeft,locator(model,"left_hand_anchor",new PoseStack()).last().pose())<EPS,"left returns READY");
                actions++;
            }
            ApocalypseFirstLight.LOGGER.info("[AFL HAND V05 REPEAT] PASS {} actions, {} frames; full arms all states, preview equivalence and stable grip; visual/collision acceptance NOT asserted",actions,frames);
        } finally {
            for(var b:processor.getRegisteredBones()) {
                var s=b.getInitialSnapshot(); b.updateRotation(s.getRotX(),s.getRotY(),s.getRotZ());
                b.updatePosition(s.getOffsetX(),s.getOffsetY(),s.getOffsetZ()); b.updateScale(s.getScaleX(),s.getScaleY(),s.getScaleZ());
            }
        }
    }

    private Vector3f sampleSourceEntrance(String channel,double seconds) {
        var keys=new ArrayList<JsonObject>();
        for(var entry:reloadFpKeys)if(entry.getAsJsonObject().get("channel").getAsString().equals(channel))keys.add(entry.getAsJsonObject());
        keys.sort(Comparator.comparingDouble(k->k.get("time").getAsDouble()));
        var previous=keys.get(0);
        for(var key:keys) {
            double t=key.get("time").getAsDouble(),start=previous.get("time").getAsDouble();
            if(t>=seconds) {
                var a=sourceKeyVector(previous);
                var b=sourceKeyVector(key);
                return a.lerp(b,(float)(t==start?0:(seconds-start)/(t-start)));
            }
            previous=key;
        }
        return sourceKeyVector(previous);
    }

    private static Vector3f sourceKeyVector(JsonObject key) {
        var p=key.getAsJsonArray("data_points").get(0).getAsJsonObject();
        return new Vector3f(p.get("x").getAsFloat(),p.get("y").getAsFloat(),p.get("z").getAsFloat());
    }

    private void verifyReadyAimline(ServicePistolModel model,PoseStack base,boolean right) {
        var muzzle=locator(model,"muzzle_anchor",base).last().pose().getTranslation(new Vector3f());
        var forward=boneMatrix(model,"barrel",base).transformDirection(new Vector3f(0,0,-1)).normalize();
        var front=locator(model,"front_sight",base).last().pose().getTranslation(new Vector3f());
        var rear=locator(model,"rear_sight",base).last().pose().getTranslation(new Vector3f());
        var sight=locator(model,"sight_anchor",base).last().pose().getTranslation(new Vector3f());
        var error=projectRay(muzzle,forward);
        var sightError=projectRay(sight,front.sub(rear).normalize());
        require(Math.hypot(error.x,error.y)<.05,"Ready muzzle ray calibration at reference plane");
        ApocalypseFirstLight.LOGGER.info("[AFL V052 AIMLINE] rightContext={} muzzle={} forward={} READY_AIMLINE_SCREEN_ERROR_X={} READY_AIMLINE_SCREEN_ERROR_Y={} sightError={}; FOV70 H1080 cameraZ=-20, no bob/equip, NOT ADS or visual PASS",
                right,muzzle,forward,error.x,error.y,sightError);
    }

    private static Vector3f projectRay(Vector3f origin,Vector3f direction) {
        require(direction.z<-.1F,"barrel forward must face camera -Z");
        var p=new Vector3f(direction).mul((-20-origin.z)/direction.z).add(origin);
        float factor=(float)(540/Math.tan(Math.toRadians(35))/-p.z);
        return new Vector3f(p.x*factor,-p.y*factor,0);
    }

    private void applyRuntimeDisplay(PoseStack pose,boolean right) {
        var d=runtimeDisplay.getAsJsonObject(right?"firstperson_righthand":"firstperson_lefthand");
        var t=vector(d,"translation").div(16);
        var r=vector(d,"rotation").mul((float)(Math.PI/180));
        var s=d.has("scale")?vector(d,"scale"):new Vector3f(1);
        pose.translate(right?t.x:-t.x,t.y,t.z);
        pose.mulPose(new Quaternionf().rotationXYZ(r.x,right?r.y:-r.y,right?r.z:-r.z));
        pose.scale(s.x,s.y,s.z);
        pose.translate(0,.01,0);
    }

    private void compareBoth(ServicePistolModel model,ModelPart[] skins,PoseStack base) {
        for(int skin=0;skin<2;skin++) for(boolean right:new boolean[]{true,false}) {
            String side=right?"right":"left"; boolean slim=skin==1;
            var anchor=locator(model,side+"_hand_anchor",base);
            var bound=NativePlayerArmRenderer.canonicalPose(anchor);
            require(bound!=null,"valid evaluated hand frame");
            NativeHandBinding.apply(bound,right,slim);
            var preview=sourceMatrix(model,side+"_arm_reference",base);
            // Direct source hierarchy. No normalization, fitted arm scale, or
            // DEV folding of a displaced reference cube into an expected locator.
            var cube=cubes.get(side+"_arm_reference"+(slim?"_slim":"")+"_cube");
            var lo=vector(cube,"from"); var hi=vector(cube,"to");
            Vector3f tip=new Vector3f((lo.x+hi.x)/2,hi.y,(lo.z+hi.z)/2).div(16);
            float tipError=preview.transformPosition(tip).distance(bound.last().pose().transformPosition(
                    new Vector3f(NativeHandBinding.centreX(right,slim),10,0).div(16)));
            record(slim,tipError);
            for(boolean sleeve:new boolean[]{false,true}) {
                var part=skins[skin].getChild(side+(sleeve?"_sleeve":"_arm"));
                var actual=NativeGunArmChecks.vertices(part,bound);
                require(actual.vertices==24,"full six-face arm/sleeve");
                var inflate=new Vector3f(NativePlayerArmRenderer.PRESENTATION_X,
                        NativePlayerArmRenderer.PRESENTATION_Y,NativePlayerArmRenderer.PRESENTATION_Z)
                        .mul((sleeve?.25F:0)/previewDisplayScale);
                var expected=new ArrayList<Vector3f>();
                for(int x=0;x<2;x++) for(int y=0;y<2;y++) for(int z=0;z<2;z++) {
                    var point=new Vector3f(x==0?lo.x-inflate.x:hi.x+inflate.x,y==0?lo.y-inflate.y:hi.y+inflate.y,z==0?lo.z-inflate.z:hi.z+inflate.z).div(16);
                    expected.add(preview.transformPosition(point));
                }
                for(var p:expected) record(slim,nearest(p,actual.points));
                for(var p:actual.points) record(slim,nearest(p,expected));
            }
        }
    }

    // Independent bbmodel hierarchy evaluation. Animation deltas come from the
    // same evaluated frame, not a second clock; rest transforms come from disk.
    private Matrix4f sourceMatrix(ServicePistolModel model,String name,PoseStack base) {
        var result=new Matrix4f(base.last().pose()); var chain=new ArrayList<String>();
        for(String n=name;n!=null;n=parents.get(n)) chain.add(n);
        Collections.reverse(chain);
        for(String n:chain) {
            var g=groups.get(n); var p=vector(g,"origin").div(16); var r=vector(g,"rotation").mul((float)(Math.PI/180));
            var optional=model.getBone(n);
            if(optional.isPresent()) {
                var b=optional.get(); var s=b.getInitialSnapshot();
                result.translate(-b.getPosX()/16F,b.getPosY()/16F,b.getPosZ()/16F);
                if(s!=null) r.add(b.getRotX()-s.getRotX(),b.getRotY()-s.getRotY(),b.getRotZ()-s.getRotZ());
            }
            result.translate(p).rotateZ(r.z).rotateY(r.y).rotateX(r.x);
            if(optional.isPresent()) {var b=optional.get();result.scale(b.getScaleX(),b.getScaleY(),b.getScaleZ());}
            result.translate(-p.x,-p.y,-p.z);
        }
        return result;
    }

    private static PoseStack locator(ServicePistolModel model,String name,PoseStack base) {
        var pose=ServicePistolRenderMatrices.detachedCopy(base); var chain=new ArrayList<GeoBone>();
        for(var b=model.getBone(name).orElseThrow();b!=null;b=b.getParent()) chain.add(b);
        Collections.reverse(chain); for(var b:chain) RenderUtils.prepMatrixForBone(pose,b);
        RenderUtils.translateToPivotPoint(pose,model.getBone(name).orElseThrow()); return pose;
    }
    private static Matrix4f boneMatrix(ServicePistolModel model,String name,PoseStack base) {
        var pose=locator(model,name,base); RenderUtils.translateAwayFromPivotPoint(pose,model.getBone(name).orElseThrow());
        return new Matrix4f(pose.last().pose());
    }
    private static Matrix4f relativeGrip(ServicePistolModel model,PoseStack base) {
        // frame includes the grip and has no independent animation: a real gun reference, not the hand carrier.
        return boneMatrix(model,"frame",base).invert().mul(locator(model,"right_hand_anchor",base).last().pose());
    }
    private void record(boolean slim,float error) {
        require(Float.isFinite(error)&&error<EPS,"preview/runtime mismatch "+error);
        if(slim) maxSlim=Math.max(maxSlim,error); else maxClassic=Math.max(maxClassic,error); comparisons++;
    }
    private static float nearest(Vector3f p,List<Vector3f> points) {
        float min=Float.POSITIVE_INFINITY; for(var q:points) min=Math.min(min,p.distance(q)); return min;
    }
    private static Vector3f vector(JsonObject obj,String key) {
        if(!obj.has(key)) return new Vector3f(); var a=obj.getAsJsonArray(key);
        return new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());
    }
    private static float error(Matrix4f a,Matrix4f b) {
        float[] x=new float[16],y=new float[16];a.get(x);b.get(y);float e=0;
        for(int i=0;i<16;i++) e=Math.max(e,Math.abs(x[i]-y[i]));return e;
    }
    private static void require(boolean condition,String message) { NativeGunArmChecks.require(condition,message); }
}
