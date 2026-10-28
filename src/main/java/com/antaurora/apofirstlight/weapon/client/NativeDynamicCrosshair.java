package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.client.config.NativeCrosshairConfigManager;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.apofirstlight.weapon.NativeStanceAccuracy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
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
    /**
     * The hip-fire crosshair (Crosshair V1, user 2026-10-26: a dot and an X; the spread and the hit flash stay): the dot
     * (client/AflCrosshair) and four diagonal arms whose gap is the spread estimate (GUI pixels, as the layout's numbers,
     * times the GUI scale; along the diagonal). hit: a confirmed hit's age 0..1 (or < 0: none), head a headshot: the arms
     * flash white / red and reach out a little, fading as it ages. Between AflCrosshair#begin and #end.
     */
    public static void render(GuiGraphics g,float partial,double hit,boolean head) {
        if(!bind())return;
        target=target();advance(partial);
        var c=NativeCrosshairConfigManager.get();
        double gs=com.antaurora.apofirstlight.client.AflCrosshair.guiScale();float u=com.antaurora.apofirstlight.client.AflCrosshair.unit();
        float opacity=(float)(c.alpha()*(player.isSprinting()?c.sprintAlpha():1));
        double flash=hit<0?0:(1-hit)*(1-hit);
        // GUI pixels times the GUI scale, along the diagonal; the width 0.6 x thickness (2 px at the defaults and scale 4)
        float half=Mth.clamp((float)(c.thickness()*gs*0.3),0.75F*u,2.5F*u);
        float from=(float)Math.max(MOTION.gap(c)*gs,(com.antaurora.apofirstlight.client.AflCrosshair.DOT_RADIUS+3)*u);
        float len=(float)Math.max((c.lineLength()+2*flash)*gs,2*half+2*u);
        int rgb=flash>0?mix(c.color(),head?HEAD:BODY,flash):c.color();
        com.antaurora.apofirstlight.client.AflCrosshair.dot(g,opacity,c.color());
        com.antaurora.apofirstlight.client.AflCrosshair.arms(g,from,len,half,(float)Math.max(opacity,flash),rgb);
    }
    /** A confirmed hit while aiming down the sights (no crosshair): the X alone, closing in from 7 to 4 GUI pixels as it fades. */
    public static void hitOnly(GuiGraphics g,double hit,boolean head) {
        double gs=com.antaurora.apofirstlight.client.AflCrosshair.guiScale();float u=com.antaurora.apofirstlight.client.AflCrosshair.unit();
        double a=(1-hit)*(1-hit);
        com.antaurora.apofirstlight.client.AflCrosshair.arms(g,(float)((7-3*hit)*gs),(float)Math.max(3*gs,4*u),Math.max(0.75F*u,(float)(0.25*gs)),
                (float)a,head?HEAD:BODY);
    }
    private static final int HEAD=0xFF3B30,BODY=0xFFFFFF;
    private static int mix(int a,int b,double t) {
        int r=(int)Math.round(((a>>16)&255)*(1-t)+((b>>16)&255)*t),gr=(int)Math.round(((a>>8)&255)*(1-t)+((b>>8)&255)*t),bl=(int)Math.round((a&255)*(1-t)+(b&255)*t);
        return r<<16|gr<<8|bl;
    }
    private NativeDynamicCrosshair() {}
}
