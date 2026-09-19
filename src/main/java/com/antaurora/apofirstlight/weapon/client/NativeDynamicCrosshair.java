package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.client.config.NativeCrosshairConfigManager;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.apofirstlight.weapon.NativeStanceAccuracy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import software.bernie.geckolib.animatable.GeoItem;

/** Local HUD estimate. Never calls server accuracy sampling or writes weapon state. */
public final class NativeDynamicCrosshair {
    private static final NativeCrosshairMotion MOTION=new NativeCrosshairMotion();
    private static LocalPlayer player;
    private static ClientLevel level;
    private static int slot;
    private static long gunId,sampleTime,lastMoving;
    private static double x,z,speed,clock,target;
    private static NativeStanceAccuracy.Stance previous=NativeStanceAccuracy.Stance.STAND_STILL;

    private static boolean bind() {
        var mc=Minecraft.getInstance();var p=mc.player;
        if(p==null||mc.level==null||!p.isAlive()||p.isSpectator()||!(p.getMainHandItem().getItem() instanceof NativeGunItem)) {
            player=null;level=null;return false;
        }
        long id=GeoItem.getId(p.getMainHandItem());
        if(player!=p||level!=mc.level||slot!=p.getInventory().selected||gunId!=id) {
            player=p;level=mc.level;slot=p.getInventory().selected;gunId=id;
            x=p.getX();z=p.getZ();sampleTime=level.getGameTime();speed=0;lastMoving=Long.MIN_VALUE;
            clock=sampleTime+mc.getFrameTime();
            target=target();MOTION.reset(target);
        }
        return true;
    }
    public static void tick() {
        if(!bind())return;
        long now=level.getGameTime();
        double velocity=Math.sqrt(player.getDeltaMovement().horizontalDistanceSqr());
        if(now>sampleTime) {
            double distance=Math.hypot(player.getX()-x,player.getZ()-z);
            speed=distance>4?velocity:Math.max(velocity,distance/(now-sampleTime));
            if(distance>4)lastMoving=Long.MIN_VALUE;
            x=player.getX();z=player.getZ();sampleTime=now;
        } else speed=velocity;
        target=target();
    }
    private static double target() {
        var c=NativeCrosshairConfigManager.get();
        var d=((NativeGunItem)player.getMainHandItem().getItem()).definition();
        var stance=NativeStanceAccuracy.classify(player.onGround(),player.isSprinting(),player.isCrouching(),speed);
        double mult=NativeStanceAccuracy.multiplier(d.accuracy(),stance);
        boolean still=stance==NativeStanceAccuracy.Stance.STAND_STILL||stance==NativeStanceAccuracy.Stance.CROUCH_STILL;
        if(!still) {lastMoving=level.getGameTime();previous=stance;}
        else if(lastMoving!=Long.MIN_VALUE) mult+=NativeStanceAccuracy.recovery(
            NativeStanceAccuracy.multiplier(d.accuracy(),previous),mult,level.getGameTime()-lastMoving,
            player.isCrouching()?NativeStanceAccuracy.CROUCH_RECOVERY_TICKS:NativeStanceAccuracy.STAND_RECOVERY_TICKS);
        double extra=c.movementGap()*Math.min(1,speed/.15);
        if(player.isSprinting())extra+=c.sprintGap();
        if(!player.onGround())extra+=c.airborneGap();
        if(player.onClimbable())extra+=c.ladderGap();
        if(player.isInWaterOrBubble()||player.isSwimming())extra+=c.waterGap();
        return Math.min(c.maxGap(),c.baseGap()+d.spreadDegrees()*mult*c.spreadScale()+extra);
    }
    private static void advance(float partial) {
        double now=level.getGameTime()+partial;
        MOTION.advance(Math.max(0,now-clock)/20,target,NativeCrosshairConfigManager.get());clock=now;
    }
    public static void shot(int confirmedSlot,long confirmedId) {
        if(!bind()||slot!=confirmedSlot||gunId!=confirmedId)return;
        advance(Minecraft.getInstance().getFrameTime());MOTION.shot(NativeCrosshairConfigManager.get());
    }
    public static void render(GuiGraphics g,int centerX,int centerY,float partial) {
        if(!bind())return;
        target=target();advance(partial);
        var c=NativeCrosshairConfigManager.get();
        float gap=(float)MOTION.gap(c),length=(float)c.lineLength(),thickness=(float)c.thickness();
        double opacity=c.alpha()*(player.isSprinting()?c.sprintAlpha():1);
        int color=((int)(255*opacity)<<24)|c.color();
        int border=((int)(255*opacity*.65)<<24)|0x202020;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.pose().pushPose();g.pose().translate(centerX,centerY,0);
        try {
            for(int i=0;i<4;i++) {
                g.pose().pushPose();
                g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(i*90));
                if(c.outline())rect(g,gap-1,-thickness/2-1,length+2,thickness+2,border);
                rect(g,gap,-thickness/2,length,thickness,color);
                g.pose().popPose();
            }
        } finally {g.pose().popPose();com.mojang.blaze3d.systems.RenderSystem.disableBlend();}
    }
    private static void rect(GuiGraphics g,float x,float y,float width,float height,int color) {
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(width,height,1);
        g.fill(0,0,1,1,color);g.pose().popPose();
    }
    private NativeDynamicCrosshair() {}
}
