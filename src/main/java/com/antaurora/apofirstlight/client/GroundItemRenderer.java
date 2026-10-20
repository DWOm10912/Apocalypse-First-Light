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
import net.minecraft.nbt.CompoundTag;
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
import java.util.HashMap;
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
 * <li>Baked block models (blocks, OBJ items) are measured once per item (their ground display recorded,
 * {@link WoodGunRackChunking#record}) and set down on the measured bottom, centred over the entity, drawn as they are.</li>
 * <li>Items with a custom renderer (AFL mesh items, magazines, sights, mesh block items): the ground display of every copy
 * the stack's count draws is recorded once ({@link WoodGunRackChunking#split}), shared by every entity with the same item,
 * tag and count of copies, set down the same way and replayed (2026-10-19).</li>
 * <li>Guns lie on their left side at their real length in the bind pose the gun rack uses, recorded once per stack and
 * replayed; drawn within {@link #GUN_DISTANCE} of the camera, and when more guns than {@link #GUN_LIMIT} lie there, only
 * the nearest that many.</li>
 * <li>Guns and custom-rendered items lying still are drawn by the chunk with Embeddium ({@link GroundItemChunking},
 * 2026-10-19): the renderer then draws only what the chunk does not take.</li>
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
    private record Gun(ItemStack stack, WoodGunRackChunking.Split split, float halfLength, float height, float halfWidth) {
        WoodGunRackChunking.Replay replay() { return split.all; }
    }

    private static final Map<ItemEntity, Gun> GUNS = Collections.synchronizedMap(new WeakHashMap<>());
    /** A custom-rendered item's measured ground display: what to move it by, and its size (as {@link #footprint}). */
    private record Fit(ItemStack stack, long generation, float dx, float dy, float dz, float halfX, float height, float halfZ) {}

    private static final Map<ItemEntity, Fit> FITS = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * A custom-rendered item's ground display: every copy its count draws, recorded at the origin; what to move it by to
     * set it down centred over the entity, and its size (as {@link #footprint}).
     */
    private record Pile(ItemStack stack, int amount, WoodGunRackChunking.Split split, float dx, float dy, float dz,
                        float halfX, float height, float halfZ) {}

    private static final Map<ItemEntity, Pile> PILES = Collections.synchronizedMap(new WeakHashMap<>());

    /** Same item, tag and number of drawn copies (guns: 1) share one recording. */
    private record Key(Item item, @Nullable CompoundTag tag, int amount) {}

    /** Recordings by stack ({@link Gun} / {@link Pile}, or {@link #NOTHING}); dropped when the meshes reload or it grows large. */
    private static final Map<Key, Object> SHARED = new HashMap<>();
    private static final Object NOTHING = new Object();
    private static final int SHARED_LIMIT = 256;
    private static long sharedGeneration = Long.MIN_VALUE;
    private static Set<Integer> nearestGuns = Set.of();
    private static int gunsInRange;
    private static long scanned = -SCAN_TICKS;   // game time starts at 0: the first frame scans

    /** Development counts ({@link #status}): shared lookups (an entity's cached recording missed), recordings made, render time. */
    private static int lookups, recordings, renders, shadowRenders;
    private static long renderNanos, shadowNanos;

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
        Pile pile = PILES.get(entity);
        if (pile != null) return new float[]{Math.max(0.08F, pile.halfX()), Math.max(0.08F, pile.height()), Math.max(0.08F, pile.halfZ())};
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

    /** Development (the frame time probe): since the last call, lookups, recordings, renders and their CPU time (main / shadow). */
    public static String status() {
        String s = String.format("lookups %d, recordings %d, renders %d (%.3f ms), shadow renders %d (%.3f ms)",
                lookups, recordings, renders, renderNanos / 1e6, shadowRenders, shadowNanos / 1e6);
        lookups = recordings = renders = shadowRenders = 0;
        renderNanos = shadowNanos = 0;
        if (!CALLERS.isEmpty()) {
            var b = new StringBuilder(s);
            CALLERS.forEach((k, v) -> b.append("\n     ").append(v).append(" x ").append(k));
            CALLERS.clear();
            s = b.toString();
        }
        return s;
    }

    /** Development (the frame time probe): who calls {@link #render}, counted by call path. */
    public static volatile boolean traceCallers;
    private static final Map<String, Integer> CALLERS = new java.util.concurrent.ConcurrentHashMap<>();

    private static void traceCaller(boolean shadow) {
        String path = StackWalker.getInstance().walk(frames -> frames.skip(2).limit(9)
                .map(f -> f.getClassName().substring(f.getClassName().lastIndexOf('.') + 1) + "." + f.getMethodName())
                .collect(java.util.stream.Collectors.joining(" < ")));
        CALLERS.merge((shadow ? "[shadow] " : "[main] ") + path, 1, Integer::sum);
    }

    @Override
    public void render(ItemEntity entity, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        var stack = entity.getItem();
        if (stack.isEmpty()) return;
        long start = System.nanoTime();
        boolean shadow = AflShaderCompat.activeShadowPass();
        if (traceCallers) traceCaller(shadow);
        draw(entity, stack, pose, buffers, light);
        long took = System.nanoTime() - start;
        if (shadow) { shadowRenders++; shadowNanos += took; } else { renders++; renderNanos += took; }
    }

    private void draw(ItemEntity entity, ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(restYaw(entity)));
        if (!(stack.getItem() instanceof NativeGunItem) || !gun(entity, stack, pose, buffers, light, true)) still(entity, stack, pose, buffers, light, true);
        pose.popPose();
        if (shouldShowName(entity)) renderNameTag(entity, entity.getDisplayName(), pose, buffers, light);
    }

    /**
     * An item lying still at the pose's origin, without the rest turn (the caller turns it): the T15 bed's cargo
     * (client/vehicle/PickupTruckRenderer, 2026-10-16), drawn through an item entity that is never added to the level.
     */
    public void renderResting(ItemEntity entity, PoseStack pose, MultiBufferSource buffers, int light) {
        var stack = entity.getItem();
        if (stack.isEmpty()) return;
        if (!(stack.getItem() instanceof NativeGunItem) || !gun(entity, stack, pose, buffers, light, false)) still(entity, stack, pose, buffers, light, false);
    }

    /**
     * Everything but guns: vanilla's ground display, lying still (flat sprites flat on the ground; custom renderers replayed,
     * or by the chunk). In the level: lying in the world (it may go to the chunk), not the T15 bed's cargo.
     */
    private void still(ItemEntity entity, ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, boolean inLevel) {
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
        if (shown.isCustomRenderer()) {
            Pile pile = pile(entity, stack, model, amount);
            if (pile == null) return;   // not recorded yet and in a shadow pass: the main pass records it
            boolean shadowPass = AflShaderCompat.activeShadowPass();
            if (inLevel && shadowPass && GroundItemChunking.available()) return;   // the chunk's shadow, or none while it moves
            pose.translate(pile.dx(), pile.dy(), pile.dz());
            if (inLevel && !shadowPass) GroundItemChunking.seen(entity, pile.split(), pile.dx(), pile.dy(), pile.dz());
            boolean chunk = inLevel && GroundItemChunking.chunkDraws(entity, pile.split());
            (chunk ? pile.split().rest : pile.split().all).draw(pose, buffers, light, shadowPass);
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
    private Pile pile(ItemEntity entity, ItemStack stack, net.minecraft.client.resources.model.BakedModel model, int amount) {
        Pile pile = PILES.get(entity);
        if (pile != null && pile.amount() == amount && pile.split().all.generation == VextrisMeshes.generation()
                && same(pile.stack(), stack)) return pile;
        if (AflShaderCompat.activeShadowPass()) return null;
        pile = shared(stack, amount, () -> recordPile(stack, model, amount));
        if (pile != null) PILES.put(entity, pile);
        return pile;
    }

    /** Every copy of the stack's ground display at the origin (the seeded scatter of {@link #still}), and how to set it down. */
    private Pile recordPile(ItemStack stack, net.minecraft.client.resources.model.BakedModel model, int amount) {
        var copy = stack.copy();
        random.setSeed(Item.getId(stack.getItem()) + stack.getDamageValue());
        var split = WoodGunRackChunking.split((p, buffers) -> {
            for (int k = 0; k < amount; k++) {
                p.pushPose();
                if (k > 0) p.translate((random.nextFloat() * 2 - 1) * 0.12F, 0, (random.nextFloat() * 2 - 1) * 0.12F);
                // light 0: whatever light a vertex carries afterwards was set by the drawing itself
                items.render(copy, ItemDisplayContext.GROUND, false, p, buffers, 0, OverlayTexture.NO_OVERLAY, model);
                p.popPose();
            }
        });
        float[] b = split.all.bounds();
        return b == null ? new Pile(copy, amount, split, 0, 0, 0, 0.18F, 0.36F, 0.18F)
                : new Pile(copy, amount, split, -(b[0] + b[3]) / 2, -b[1] + 0.002F, -(b[2] + b[5]) / 2,
                        (b[3] - b[0]) / 2, b[4] - b[1], (b[5] - b[2]) / 2);
    }

    /** The recording shared by every stack of the same item, tag and copies, made once; null when it draws nothing. */
    @Nullable
    @SuppressWarnings("unchecked")
    private static <T> T shared(ItemStack stack, int amount, java.util.function.Supplier<T> record) {
        long generation = VextrisMeshes.generation();
        if (generation != sharedGeneration || SHARED.size() > SHARED_LIMIT) {
            SHARED.clear();
            sharedGeneration = generation;
        }
        lookups++;
        var key = new Key(stack.getItem(), stack.getTag() == null ? null : stack.getTag().copy(), amount);
        Object value = SHARED.get(key);
        if (value == null) {
            recordings++;
            long start = System.nanoTime();
            value = record.get();
            if (traceCallers) ApocalypseFirstLight.LOGGER.info("[AFL GROUND] recorded {} x{} in {} ms", stack, amount,
                    String.format("%.2f", (System.nanoTime() - start) / 1e6));
            SHARED.put(key, value == null ? NOTHING : value);
        }
        return value == NOTHING ? null : (T) value;
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

    /**
     * A gun lying on its left side, muzzle toward local +x; false when it has no geometry to draw (vanilla's display then).
     * In the level: lying in the world (it may go to the chunk), not the T15 bed's cargo.
     */
    private boolean gun(ItemEntity entity, ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, boolean inLevel) {
        boolean shadowPass = AflShaderCompat.activeShadowPass();
        if (inLevel && shadowPass && GroundItemChunking.available()) return true;   // the chunk's shadow, or none while it moves
        Gun gun = GUNS.get(entity);
        if (gun == null || gun.replay().generation != VextrisMeshes.generation() || !same(gun.stack(), stack)) {
            if (shadowPass) return true;   // recorded in the main pass only (a mesh skips its translucent parts in a shadow pass)
            gun = shared(stack, 1, () -> record(stack));
            if (gun == null) return false;
            GUNS.put(entity, gun);
        }
        if (inLevel && !shadowPass) GroundItemChunking.seen(entity, gun.split(), 0, 0, 0);
        boolean chunk = inLevel && GroundItemChunking.chunkDraws(entity, gun.split());
        if (!shown(entity)) return true;
        (chunk ? gun.split().rest : gun.replay()).draw(pose, buffers, light, shadowPass);
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
        var split = WoodGunRackChunking.split((p, b) -> {
            // as on the gun rack's shelf: muzzle (model -z) toward +x, the right side up (model +x up), the left side on the ground
            p.mulPose(Axis.YP.rotationDegrees(-90));
            p.mulPose(Axis.ZP.rotationDegrees(90));
            p.scale(scale, scale, scale);
            p.translate(-e[0], -(e[1] + e[4]) / 2, -(e[2] + e[5]) / 2);
            MaintenanceGunRendering.renderBindPose(copy, p, b, 0);
        });
        return new Gun(copy, split, (float) ((e[5] - e[2]) * scale / 2), (float) ((e[3] - e[0]) * scale),
                (float) ((e[4] - e[1]) * scale / 2));
    }

    /** Same item and tag (what the drawing reads). */
    private static boolean same(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem() && Objects.equals(a.getTag(), b.getTag());
    }
}
