package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.camera.CameraDirector;
import com.antaurora.apofirstlight.client.camera.CameraPose;
import com.antaurora.apofirstlight.client.skeleton.SkeletonActions;
import com.antaurora.apofirstlight.item.FoldingKnifeItem;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Folding knife V1 on the client (docs/gameplay/folding_knife_v1.md 5):
 * <ul>
 *   <li><b>The camera dip</b> (client/camera/CameraDirector), as the flip lighter's: in the own first person, while both
 *   hands open or fold the blade, the view glances down to them (at least {@link #DIP_PITCH} deg), drawn back a little,
 *   eased in and out; the mouse is locked while the hands are on the blade.</li>
 *   <li><b>The swing in the air</b>: a left click on nothing with the knife open plays the swish here only (a miss sends
 *   the server nothing; quiet, so no noise is lost). Hits are the server's (item/FoldingKnifeItem: the stab, the tip on a
 *   block).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FoldingKnifeClient {
    private static final String CAMERA = "afl_folding_knife", LOCK = "afl_folding_knife_lock";
    private static final float DIP_PITCH = 28F;
    private static final double PULL_BACK = 0.1, LIFT = 0.05;
    private static final double DIP_IN = 0.3, DIP_OUT = 0.45;
    /** Swishes no closer than this (ticks): a held button repeats misses. */
    private static final int SWING_GAP = 8;
    private static long lastSwing = -SWING_GAP;

    private FoldingKnifeClient() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        ItemStack stack = mc.player.getMainHandItem();
        String act = stack.getItem() instanceof FoldingKnifeItem ? FoldingKnifeItem.act(stack) : null;
        boolean fp = mc.options.getCameraType().isFirstPerson() && SkeletonActions.drawsArms() && !mc.player.isSpectator();
        double t = act == null ? 1e6 : FoldingKnifeItem.actSeconds(stack, mc.level, 0F);
        boolean dip = fp && act != null && t < dipUntil(act);
        boolean holding = CameraDirector.holding(CAMERA);
        if (dip && !holding) CameraDirector.open(new CameraDirector.Request(CAMERA, CameraDirector.PRIORITY_MACHINE, FoldingKnifeClient::shot,
                CameraDirector.Clock.GAME, DIP_IN, DIP_OUT));
        else if (!dip && holding) CameraDirector.close(CAMERA);
        boolean lock = dip && t < lockUntil(act);
        boolean locking = CameraDirector.holding(LOCK);
        if (lock && !locking) CameraDirector.open(new CameraDirector.Request(LOCK, CameraDirector.PRIORITY_MACHINE, (c, cur, pt) -> null,
                CameraDirector.Clock.GAME, 0, 0).locks(CameraDirector.Lock.LOOK));
        else if (!lock && locking) CameraDirector.close(LOCK);
    }

    /** How long into an act the view stays down (s): until the hand starts back down (author-folding-knife-actions.mjs). */
    private static double dipUntil(String act) {
        return switch (act) {
            case "open" -> 1.35;
            case "close" -> 1.2;
            default -> 0;
        };
    }

    /** How long into an act the mouse is locked (s): while the left hand is on the blade. */
    private static double lockUntil(String act) {
        return switch (act) {
            case "open" -> 1.25;
            case "close" -> 1.15;
            default -> 0;
        };
    }

    private static CameraPose shot(Camera camera, CameraPose current, float partial) {
        float yaw = current.yaw() * Mth.DEG_TO_RAD;
        Vec3 back = new Vec3(Mth.sin(yaw), 0, -Mth.cos(yaw)).scale(PULL_BACK);   // the view's ahead is (-sin, 0, cos)
        return new CameraPose(current.position().add(back).add(0, LIFT, 0), current.yaw(), Math.max(current.pitch(), DIP_PITCH));
    }

    @SubscribeEvent
    public static void swingInAir(PlayerInteractEvent.LeftClickEmpty event) {
        var player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof FoldingKnifeItem) || !FoldingKnifeItem.armed(stack, player.level())) return;
        long now = player.level().getGameTime();
        if (now - lastSwing < SWING_GAP && now >= lastSwing) return;
        lastSwing = now;
        Vec3 at = FoldingKnifeItem.handPoint(player);
        player.level().playLocalSound(at.x, at.y, at.z, AflSounds.FOLDING_KNIFE_SWING.get(), SoundSource.PLAYERS,
                0.7F, 0.95F + player.getRandom().nextFloat() * 0.1F, false);
    }
}
