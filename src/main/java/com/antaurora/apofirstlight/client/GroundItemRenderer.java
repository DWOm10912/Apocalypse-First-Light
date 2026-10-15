package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.inventory.HotbarRoles;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.antaurora.vextris.client.VextrisMeshes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Ground items (docs/ui/afl_inventory_grid_v0_plan.md 12): what lies on the ground lies still, turned to a fixed angle per
 * item (from its UUID), no bobbing and no spinning.
 * <ul>
 * <li>Flat items (generated sprites) lie flat on the ground.</li>
 * <li>Everything else (blocks, meshes, GeckoLib items) is measured once per item (its ground display recorded,
 * {@link WoodGunRackChunking#record}) and set down on the measured bottom, centred over the entity.</li>
 * <li>Guns lie on their left side at their real length in the bind pose the gun rack uses, recorded once per item and
 * replayed ({@link WoodGunRackChunking#record}); drawn within {@link #GUN_DISTANCE} of the camera, and when more guns
 * than {@link #GUN_LIMIT} lie there, only the nearest that many.</li>
 * </ul>
 * Replaces vanilla's item entity renderer (EntityType.ITEM). Flat or not is read from the model the ground display really
 * draws ({@link #groundModel}): a forge:separate_transforms wrapper (most AFL mesh items) says "not custom, no transforms"
 * itself, which first sent the meshes down the block path, half a block up (2026-10-15).
 */
public final class GroundItemRenderer extends ItemEntityRenderer {
    private static final double GUN_DISTANCE = 32;
    private static final int GUN_LIMIT = 16, SCAN_TICKS = 10;
    /** Flat items: half the ground-scaled sprite, its lift (half its thickness). */
    private static final float FLAT_HALF = 0.25F, FLAT_LIFT = 0.016F;

    /** One gun's recording and its size as it lies (local x: length, y: up, z: width). */
    private record Gun(ItemStack stack, WoodGunRackChunking.Replay replay, float halfLength, float height, float halfWidth) {}

    private static final Map<ItemEntity, Gun> GUNS = Collections.synchronizedMap(new WeakHashMap<>());
    /** A custom-rendered item's measured ground display: what to move it by, and its size (as {@link #footprint}). */
    private record Fit(ItemStack stack, long generation, float dx, float dy, float dz, float halfX, float height, float halfZ) {}

    private static final Map<ItemEntity, Fit> FITS = Collections.synchronizedMap(new WeakHashMap<>());
    private static Set<Integer> nearestGuns = Set.of();
    private static int gunsInRange;
    private static long scanned = -SCAN_TICKS;   // game time starts at 0: the first frame scans

    private final ItemRenderer items;
    private final RandomSource random = RandomSource.create();

    public GroundItemRenderer(EntityRendererProvider.Context context) {
        super(context);
        items = context.getItemRenderer();
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void register(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(EntityType.ITEM, GroundItemRenderer::new);
        }
    }

    /** The fixed angle (radians about +y) an item lies at, the same every frame and for every viewer. */
    public static float restYaw(ItemEntity entity) {
        var id = entity.getUUID();
        long bits = id.getMostSignificantBits() ^ id.getLeastSignificantBits() * 0x9E3779B97F4A7C15L;
        return (float) ((bits >>> 11) * 0x1.0p-53 * Math.PI * 2);
    }

    /**
     * The box an item takes as it lies, in its turned frame ({@link #restYaw}) above the entity's position: {half x, height,
     * half z}. For the look-at prompt (GroundItemPick).
     */
    public static float[] footprint(ItemEntity entity) {
        var stack = entity.getItem();
        if (stack.getItem() instanceof NativeGunItem) {
            Gun gun = GUNS.get(entity);
            return gun != null ? new float[]{gun.halfLength(), gun.height(), gun.halfWidth()} : new float[]{0.4F, 0.15F, 0.12F};
        }
        var model = Minecraft.getInstance().getItemRenderer().getModel(stack, entity.level(), null, entity.getId());
        if (flat(groundModel(model))) return new float[]{FLAT_HALF, 0.08F, FLAT_HALF};
        Fit fit = FITS.get(entity);
        if (fit != null) return new float[]{Math.max(0.08F, fit.halfX()), Math.max(0.08F, fit.height()), Math.max(0.08F, fit.halfZ())};
        return new float[]{0.18F, 0.36F, 0.18F};
    }

    /** The model the ground display draws (a separate_transforms wrapper hands over its ground perspective / base model). */
    private static net.minecraft.client.resources.model.BakedModel groundModel(net.minecraft.client.resources.model.BakedModel model) {
        return model.applyTransform(ItemDisplayContext.GROUND, new PoseStack(), false);
    }

    /** A plain sprite item (generated model): laid flat. */
    private static boolean flat(net.minecraft.client.resources.model.BakedModel shown) {
        return !shown.isGui3d() && !shown.isCustomRenderer();
    }

    @Override
    public void render(ItemEntity entity, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        var stack = entity.getItem();
        if (stack.isEmpty()) return;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(restYaw(entity)));
        if (!(stack.getItem() instanceof NativeGunItem) || !gun(entity, stack, pose, buffers, light)) still(entity, stack, pose, buffers, light);
        pose.popPose();
        if (shouldShowName(entity)) renderNameTag(entity, entity.getDisplayName(), pose, buffers, light);
    }

    /** Everything but guns: vanilla's ground display, lying still (flat sprites flat on the ground). */
    private void still(ItemEntity entity, ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light) {
        var model = items.getModel(stack, entity.level(), null, entity.getId());
        var shown = groundModel(model);
        var ground = shown.getTransforms().getTransform(ItemDisplayContext.GROUND);
        random.setSeed(Item.getId(stack.getItem()) + stack.getDamageValue());
        int amount = getRenderAmount(stack);
        if (flat(shown)) {
            // the sprite's front up, the ground display's lift taken out (it raised the upright sprite off the ground)
            pose.translate(0, FLAT_LIFT, 0);
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            for (int k = 0; k < amount; k++) {
                pose.pushPose();
                if (k > 0) pose.translate((random.nextFloat() * 2 - 1) * 0.12F, (random.nextFloat() * 2 - 1) * 0.12F, k * 0.033F);
                pose.translate(-ground.translation.x(), -ground.translation.y(), -ground.translation.z());
                items.render(stack, ItemDisplayContext.GROUND, false, pose, buffers, light, OverlayTexture.NO_OVERLAY, model);
                pose.popPose();
            }
            return;
        }
        // set down on the measured bottom, centred over the entity
        Fit fit = fit(entity, stack, model);
        if (fit == null) return;   // not measured yet and in a shadow pass: the main pass measures it
        pose.translate(fit.dx(), fit.dy(), fit.dz());
        for (int k = 0; k < amount; k++) {
            pose.pushPose();
            if (k > 0) pose.translate((random.nextFloat() * 2 - 1) * 0.12F, 0, (random.nextFloat() * 2 - 1) * 0.12F);
            items.render(stack, ItemDisplayContext.GROUND, false, pose, buffers, light, OverlayTexture.NO_OVERLAY, model);
            pose.popPose();
        }
    }

    @Nullable
    private Fit fit(ItemEntity entity, ItemStack stack, net.minecraft.client.resources.model.BakedModel model) {
        Fit fit = FITS.get(entity);
        long generation = VextrisMeshes.generation();
        if (fit != null && fit.generation() == generation && same(fit.stack(), stack)) return fit;
        if (AflShaderCompat.activeShadowPass()) return null;
        var copy = stack.copy();
        float[] b = WoodGunRackChunking.record((p, buffers) ->
                items.render(copy, ItemDisplayContext.GROUND, false, p, buffers, 0, OverlayTexture.NO_OVERLAY, model)).bounds();
        fit = b == null ? new Fit(copy, generation, 0, 0, 0, 0.18F, 0.36F, 0.18F)
                : new Fit(copy, generation, -(b[0] + b[3]) / 2, -b[1] + 0.002F, -(b[2] + b[5]) / 2,
                        (b[3] - b[0]) / 2, b[4] - b[1], (b[5] - b[2]) / 2);
        FITS.put(entity, fit);
        return fit;
    }

    /** A gun lying on its left side, muzzle toward local +x; false when it has no geometry to draw (vanilla's display then). */
    private boolean gun(ItemEntity entity, ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light) {
        boolean shadowPass = AflShaderCompat.activeShadowPass();
        Gun gun = GUNS.get(entity);
        if (gun == null || gun.replay().generation != VextrisMeshes.generation() || !same(gun.stack(), stack)) {
            if (shadowPass) return true;   // recorded in the main pass only (a mesh skips its translucent parts in a shadow pass)
            gun = record(stack);
            if (gun == null) return false;
            GUNS.put(entity, gun);
        }
        if (!shown(entity)) return true;
        gun.replay().draw(pose, buffers, light, shadowPass);
        return true;
    }

    /** Within reach of the camera, and among the nearest {@link #GUN_LIMIT} when more guns lie around. */
    private static boolean shown(ItemEntity entity) {
        var mc = Minecraft.getInstance();
        var camera = mc.gameRenderer.getMainCamera().getPosition();
        if (entity.position().distanceToSqr(camera) > GUN_DISTANCE * GUN_DISTANCE) return false;
        long now = entity.level().getGameTime();
        if (now - scanned >= SCAN_TICKS || now < scanned) {
            scanned = now;
            List<ItemEntity> guns = entity.level().getEntitiesOfClass(ItemEntity.class, new AABB(camera, camera).inflate(GUN_DISTANCE),
                    e -> e.getItem().getItem() instanceof NativeGunItem && e.position().distanceToSqr(camera) <= GUN_DISTANCE * GUN_DISTANCE);
            gunsInRange = guns.size();
            var nearest = new HashSet<Integer>();
            guns.stream().sorted(java.util.Comparator.comparingDouble(e -> e.position().distanceToSqr(camera))).limit(GUN_LIMIT)
                    .forEach(e -> nearest.add(e.getId()));
            nearestGuns = nearest;
        }
        return gunsInRange <= GUN_LIMIT || nearestGuns.contains(entity.getId());
    }

    @Nullable
    private static Gun record(ItemStack stack) {
        List<AABB> boxes = MaintenanceGunRendering.bounds(stack);
        if (boxes.isEmpty()) return null;
        double[] e = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (AABB b : boxes) {
            e[0] = Math.min(e[0], b.minX); e[1] = Math.min(e[1], b.minY); e[2] = Math.min(e[2], b.minZ);
            e[3] = Math.max(e[3], b.maxX); e[4] = Math.max(e[4], b.maxY); e[5] = Math.max(e[5], b.maxZ);
        }
        if (!(e[3] > e[0] && e[5] > e[2])) return null;
        boolean pistol = HotbarRoles.weaponRole(stack) == HotbarRoles.Role.SECONDARY;
        float scale = (float) (WoodGunRackRenderer.length(stack, pistol) / (e[5] - e[2]));
        var copy = stack.copy();
        var replay = WoodGunRackChunking.record((p, b) -> {
            // as on the gun rack's shelf: muzzle (model -z) toward +x, the right side up (model +x up), the left side on the ground
            p.mulPose(Axis.YP.rotationDegrees(-90));
            p.mulPose(Axis.ZP.rotationDegrees(90));
            p.scale(scale, scale, scale);
            p.translate(-e[0], -(e[1] + e[4]) / 2, -(e[2] + e[5]) / 2);
            MaintenanceGunRendering.renderBindPose(copy, p, b, 0);
        });
        return new Gun(copy, replay, (float) ((e[5] - e[2]) * scale / 2), (float) ((e[3] - e[0]) * scale),
                (float) ((e[4] - e[1]) * scale / 2));
    }

    /** Same item and tag (what the drawing reads). */
    private static boolean same(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem() && Objects.equals(a.getTag(), b.getTag());
    }
}
