package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.apofirstlight.weapon.NativeRecoilState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.animatable.GeoItem;

/** Successful-shot-only feedback, shared by all NativeGunItem definitions. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class NativeGunRecoil {
    private static final NativeRecoilState STATE = new NativeRecoilState();
    private static LocalPlayer owner;
    private static ClientLevel level;
    private static long gunId, lastNanos;
    private static int slot;
    private record Deferred(LocalPlayer player,ClientLevel level,int slot,long gun){}
    private static final java.util.ArrayDeque<Deferred> DEFERRED=new java.util.ArrayDeque<>();

    private NativeGunRecoil() {}

    private static boolean valid(Minecraft mc) {
        return owner != null && owner == mc.player && level == mc.level && owner.isAlive()
                && !owner.isSpectator() && owner.getInventory().selected == slot
                && owner.getMainHandItem().getItem() instanceof NativeGunItem
                && GeoItem.getId(owner.getMainHandItem()) == gunId;
    }

    public static void confirmedShot(int selectedSlot, long confirmedGunId) {
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.screen==null&&mc.options.getCameraType().isFirstPerson()){
            if(DEFERRED.size()>=128)flushDeferred();
            DEFERRED.addLast(new Deferred(mc.player,mc.level,selectedSlot,confirmedGunId));
        }else applyConfirmedShot(selectedSlot,confirmedGunId);
    }
    private static void flushDeferred(){
        var mc=Minecraft.getInstance();
        while(!DEFERRED.isEmpty()){
            var d=DEFERRED.removeFirst();
            if(d.player==mc.player&&d.level==mc.level)applyConfirmedShot(d.slot,d.gun);
        }
    }
    private static void applyConfirmedShot(int selectedSlot, long confirmedGunId) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null || !player.isAlive() || player.isSpectator()
                || player.getInventory().selected != selectedSlot
                || !(player.getMainHandItem().getItem() instanceof NativeGunItem gun)
                || GeoItem.getId(player.getMainHandItem()) != confirmedGunId) return;
        if (!valid(mc)) STATE.clear();
        else advance(mc);
        owner = player; level = mc.level; slot = selectedSlot; gunId = confirmedGunId;
        lastNanos = System.nanoTime();
        double v = STATE.vertical(), h = STATE.horizontal();
        var random = player.getRandom();
        STATE.kick(gun.definition().recoil(), random.nextDouble(), random.nextDouble(),
                random.nextDouble(), random.nextDouble(), player.getXRot() + 90.0);
        applyAim(STATE.vertical() - v, STATE.horizontal() - h);
        if(Boolean.getBoolean("afl.shotSnapshotDebug"))ApocalypseFirstLight.LOGGER.info("[SHOT RECOIL] applied gun={} frame-end-or-before-next-input",confirmedGunId);
    }

    private static void applyAim(double upward, double sideways) {
        float before = owner.getXRot();
        owner.setXRot(Mth.clamp(before - (float) upward, -90, 90));
        owner.setYRot(owner.getYRot() + (float) sideways);
        // Shift the interpolation endpoints by the same delta; never restore an old aim.
        owner.xRotO += owner.getXRot() - before;
        owner.yRotO += (float) sideways;
    }

    private static void advance(Minecraft mc) {
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(.05, (now - lastNanos) / 1_000_000_000.0);
        lastNanos = now;
        if (!valid(mc)) { STATE.clear(); owner = null; return; }
        if (mc.isPaused()) return;
        double v = STATE.vertical(), h = STATE.horizontal();
        STATE.advance(dt);
        applyAim(STATE.vertical() - v, STATE.horizontal() - h);
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) advance(Minecraft.getInstance());
        else flushDeferred();
    }

    /** Queue Vanilla rotation BEFORE the custom shot on the same ordered connection.
     * The server still obtains eye position, look vector, spread and hit itself. */
    public static void syncAimBeforeShot() {
        // Never let another request escape with pre-recoil aim, even between render passes.
        flushDeferred();
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null)
            mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(
                    mc.player.getYRot(), mc.player.getXRot(), mc.player.onGround()));
    }

    /** Camera-space additive carrier outside Display: gun and both evaluated anchors inherit it. */
    public static void applyViewmodel(PoseStack pose) {
        if (!valid(Minecraft.getInstance())) return;
        pose.translate(0, 0, STATE.back());
        pose.mulPose(Axis.XP.rotationDegrees((float) STATE.pitch()));
        pose.mulPose(Axis.YP.rotationDegrees((float) STATE.yaw()));
        pose.mulPose(Axis.ZP.rotationDegrees((float) STATE.roll()));
    }
}
