package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.ConfiguredNativeGunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/** BR51 rotation-only pilot. No retained camera offset, player rotation writes or separate clock. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class NativeCameraBoneConsumer {
    private static final float DEGREES = 180f / (float)Math.PI;
    private static final boolean DEBUG = Boolean.getBoolean("afl.nativeCameraDebug");
    private static long lastDebug;
    private static GeoModel<?> preparedModel;
    private static long preparedId;
    private static double preparedTick;

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) preparedModel = null;
    }

    /** Share Gecko's sampled time with the later FP hand pass; never invent another clock. */
    static void shareFrameTick(GeoModel<?> model, long id, AnimationState<?> state) {
        var perspective = state.getData(DataTickets.ITEM_RENDER_PERSPECTIVE);
        if (model == preparedModel && id == preparedId && perspective != null && perspective.firstPerson())
            state.setData(DataTickets.TICK, preparedTick);
    }

    public static boolean supportedAction(String name) {
        return name != null && switch (name) {
            case "inspect", "reload_tactical", "reload_empty", "draw", "put_away" -> true;
            default -> false; // Includes shoot and both static baselines.
        };
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        // The event starts from the engine's fresh camera pose on EVERY frame. Returning is exact zero.
        if (player == null || mc.level == null || !player.isAlive() || player.isSpectator()
                || player.isSleeping() || player.isScoping() || mc.screen != null || mc.getOverlay() != null
                || !mc.isWindowActive() || !mc.options.getCameraType().isFirstPerson()
                || event.getCamera().getEntity() != player) return;
        var stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ConfiguredNativeGunItem gun)
                || !gun.profile.id().equals("br51_01")) return;
        long id = GeoItem.getId(stack);
        if (id == Long.MAX_VALUE) return;
        if (!(IClientItemExtensions.of(stack).getCustomRenderer() instanceof NativeAnimatedWeaponRenderer<?> renderer)) return;
        var model = (GeoModel<ConfiguredNativeGunItem>)(GeoModel<?>)renderer.getGeoModel();
        var resource = model.getModelResource(gun);
        // Missing/loading/reloaded resources are ordinary no-data states, not per-frame exceptions.
        if (!GeckoLibCache.getBakedModels().containsKey(resource)
                || !GeckoLibCache.getBakedAnimations().containsKey(model.getAnimationResource(gun))) return;
        model.getBakedModel(resource); // Activates/replaces processor's indexed bones after resource reload.
        var bone = model.getAnimationProcessor().getBone("camera"); // Hash lookup, not a tree walk.
        if (bone == null) return;
        var perspective = player.getMainArm() == HumanoidArm.RIGHT
                ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        var manager = gun.getAnimatableInstanceCache().getManagerForId(id);
        var state = new AnimationState<>(gun, 0, 0, (float)event.getPartialTick(), false);
        // Same data, model, manager and frame tick as GeoItemRenderer.actuallyRender.
        // GeoItem.getTick uses wall time, so explicitly reuse this sample in the later hand pass.
        preparedModel = model;
        preparedId = id;
        preparedTick = gun.getTick(stack);
        state.setData(DataTickets.TICK, preparedTick);
        state.setData(DataTickets.ITEM_RENDER_PERSPECTIVE, perspective);
        state.setData(DataTickets.ITEMSTACK, stack);
        manager.setData(DataTickets.ITEM_RENDER_PERSPECTIVE, perspective);
        model.addAdditionalStateData(gun, id, state::setData);
        model.handleAnimations(gun, id, state);
        var action = manager.getAnimationControllers().get("action");
        if (action == null || action.getTriggeredAnimation() == null
                || action.getAnimationState() == AnimationController.State.STOPPED
                || action.getCurrentAnimation() == null) return;
        String clip = action.getCurrentAnimation().animation().name();
        if (!supportedAction(clip)) return;
        // Read final eased rotation, not JSON or a second interpolation. Subtract bind rotation.
        var bind = bone.getInitialSnapshot();
        float x = bone.getRotX() - bind.getRotX();
        float y = bone.getRotY() - bind.getRotY();
        float z = bone.getRotZ() - bind.getRotZ();
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return;
        // Gecko's loader negates runtime JSON X/Y; the exporter already negated author X/Y.
        // Thus these radians are author-space angles. Camera view rotation is their inverse.
        float pitch = -x * DEGREES, yaw = -y * DEGREES, roll = -z * DEGREES;
        event.setPitch(event.getPitch() + pitch);
        event.setYaw(event.getYaw() + yaw);
        event.setRoll(event.getRoll() + roll);
        if (DEBUG && System.nanoTime() - lastDebug > 1_000_000_000L) {
            lastDebug = System.nanoTime();
            ApocalypseFirstLight.LOGGER.info("[Native Camera] bone=FOUND action={} rawRad={}/{}/{} mappedDeg={}/{}/{} scale=1 applied=YES",
                    clip, x, y, z, pitch, yaw, roll);
        }
    }

    private NativeCameraBoneConsumer() {}
}
