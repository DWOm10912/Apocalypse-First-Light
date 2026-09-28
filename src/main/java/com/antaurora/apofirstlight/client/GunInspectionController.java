package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.client.mesh.AflMeshCache;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Screen-owned view deltas: Field transforms the gun; maintenance changes only view FOV. */
public final class GunInspectionController {
    public static final double MIN_ZOOM = .60, DEFAULT_ZOOM = 1, MAX_ZOOM = 2.50;
    // Time to settle 95% of a step, not a frame-dependent interpolation factor.
    private static final double MOTION_RESPONSE = .10, ZOOM_RESPONSE = .15, RESET_SECONDS = .25;
    private static final double PITCH_LIMIT = 80, DRAG_THRESHOLD = 4, DEGREES_PER_PIXEL = .55;
    private static final double SAFE_DEPTH = .10; // Vanilla near plane .05 plus .05 margin.
    private static final double P = Math.toRadians(MaintenanceCameraController.PITCH);
    private final boolean field;
    private final Vec3 right, up, forward, camera;
    private double projectionY = 1 / Math.tan(Math.toRadians(MaintenanceCameraController.FOV / 2));

    public GunInspectionController(boolean field) {
        this.field = field;
        right = field ? new Vec3(1, 0, 0) : new Vec3(-1, 0, 0);
        up = field ? new Vec3(0, 1, 0) : new Vec3(0, Math.cos(P), Math.sin(P));
        forward = field ? new Vec3(0, 0, -1) : new Vec3(0, -Math.sin(P), Math.cos(P));
        camera = field ? Vec3.ZERO : MaintenanceCameraController.CAMERA.subtract(MaintenanceCameraController.MAT);
    }
    /** Field uses the actual first-person projection also used by FieldAttachmentHotspots. */
    public void projection(float verticalScale) {
        if (field && Float.isFinite(verticalScale) && verticalScale > 0) {
            projectionY = verticalScale;
            if (available()) { panLimits(); panX = clamp(panX, minPanX, maxPanX); panY = clamp(panY, minPanY, maxPanY); }
        }
    }

    private ItemStack snapshot = ItemStack.EMPTY;
    private Object bodyBounds;
    private long generation = -1, lastFrame;
    private GunInspectionGeometry geometry;
    private int width, height;
    private double yaw, pitch, zoom = 1, panX, panY;
    private double targetYaw, targetPitch, targetZoom = 1, targetPanX, targetPanY;
    private double minPanX, maxPanX, minPanY, maxPanY, unitsX, unitsY;
    private long resetStart;
    private double resetYaw, resetPitch, resetZoom, resetX, resetY, resetEndYaw;
    private int dragButton = -1;
    private double startX, startY, lastX, lastY;
    private boolean dragging;

    public void resize(int width, int height) {
        this.width = width; this.height = height;
        cancelDrag();
    }
    // Explicit transparent viewport, reserving the existing bottom hotbar, candidates and notices.
    public boolean inViewport(double x, double y) {
        return width > 32 && height > 88 && x >= 12 && x < width - 12 && y >= 12 && y < height - 64;
    }
    public boolean available() { return geometry != null && width > 32 && height > 88; }

    /** Called exactly once at render-tick START, before world BER and GUI hotspot projection. */
    public void frame(ItemStack stack, long now) {
        double dt = lastFrame == 0 ? 0 : Math.max(0, (now - lastFrame) / 1e9);
        lastFrame = now;
        var bounds = MaintenanceGunRendering.bounds(stack);
        long resources = AflMeshCache.snapshot().generation();
        if (bodyBounds != bounds || generation != resources || !ItemStack.isSameItemSameTags(stack, snapshot)) {
            boolean differentGun = stack.getItem() != snapshot.getItem();
            Vec3 retainedPivot = !differentGun && bodyBounds == bounds && generation == resources && geometry != null
                    ? geometry.pivot() : null;
            snapshot = stack.copy(); bodyBounds = bounds; generation = resources;
            geometry = stack.isEmpty() ? null : GunInspectionGeometry.capture(stack, retainedPivot, field);
            cancelDrag();
            if (differentGun) defaults();
        }
        if (!available()) return;
        panLimits();
        if (resetStart != 0) {
            double t = clamp((now - resetStart) / 1e9 / RESET_SECONDS, 0, 1);
            double ease = 1 - Math.pow(1 - t, 3);
            yaw = lerp(resetYaw, resetEndYaw, ease); pitch = lerp(resetPitch, 0, ease);
            zoom = lerp(resetZoom, 1, ease); panX = lerp(resetX, 0, ease); panY = lerp(resetY, 0, ease);
            if (t >= 1) defaults();
        } else {
            double motion = 1 - Math.exp(-Math.log(20) * dt / MOTION_RESPONSE);
            double magnify = 1 - Math.exp(-Math.log(20) * dt / ZOOM_RESPONSE);
            yaw = lerp(yaw, targetYaw, motion); pitch = lerp(pitch, targetPitch, motion);
            zoom = lerp(zoom, Math.min(targetZoom, safeZoom(rotation())), magnify);
            targetPanX = clamp(targetPanX, minPanX, maxPanX);
            targetPanY = clamp(targetPanY, minPanY, maxPanY);
            panX = lerp(panX, targetPanX, motion); panY = lerp(panY, targetPanY, motion);
        }
        // Safety applies to the CURRENT pose too, including rotation/reset, resize and new accessories.
        zoom = Math.min(zoom, safeZoom(rotation()));
        panX = clamp(panX, minPanX, maxPanX); panY = clamp(panY, minPanY, maxPanY);
    }

    private void defaults() {
        yaw = pitch = panX = panY = targetYaw = targetPitch = targetPanX = targetPanY = 0;
        zoom = targetZoom = 1; resetStart = 0;
    }
    private void panLimits() {
        if (!field) {
            minPanX = maxPanX = minPanY = maxPanY = unitsX = unitsY = 0;
            panX = panY = targetPanX = targetPanY = 0;
            return;
        }
        Vec3 delta = geometry.pivot().subtract(camera);
        double depth = delta.dot(forward);
        double focal = height * projectionY / 2;
        double screenX = width / 2d + delta.dot(right) * focal / depth;
        double screenY = height / 2d - delta.dot(up) * focal / depth;
        double viewportWidth = width - 24, viewportHeight = height - 76;
        unitsX = viewportWidth * depth / focal; unitsY = viewportHeight * depth / focal;
        minPanX = Math.max(-.35, (20 - screenX) / viewportWidth);
        maxPanX = Math.min(.35, (width - 20 - screenX) / viewportWidth);
        minPanY = Math.max(-.25, (20 - screenY) / viewportHeight);
        maxPanY = Math.min(.25, (height - 72 - screenY) / viewportHeight);
        // Tiny/resized windows with a baseline point outside the viewport still retain a valid interval.
        if (minPanX > maxPanX) minPanX = maxPanX = (width / 2d - screenX) / viewportWidth;
        if (minPanY > maxPanY) minPanY = maxPanY = ((height - 52) / 2d - screenY) / viewportHeight;
    }
    private Quaternionf rotation() {
        return rotation(1);
    }
    private Quaternionf rotation(float blend) {
        if (!field) return new Quaternionf(); // Maintenance must never change its authored orientation.
        return new Quaternionf().rotationAxis((float)Math.toRadians(pitch * blend), (float)right.x, 0, 0)
                .rotateAxis((float)Math.toRadians(Math.IEEEremainder(yaw, 360) * blend), 0, (float)up.y, (float)up.z);
    }
    /** Eight cached AABB corners, not mesh geometry; includes all actually rendered attachments. */
    private double safeZoom(Quaternionf rotation) {
        if (!field) return MAX_ZOOM; // FOV zoom does not move the near plane or the weapon.
        var b = geometry.bounds(); var pivot = geometry.pivot();
        double towardCamera = 0;
        for (int i = 0; i < 8; i++) {
            var v = rotation.transform(new Vector3f(
                    (float)(((i & 1) == 0 ? b.minX : b.maxX) - pivot.x),
                    (float)(((i & 2) == 0 ? b.minY : b.maxY) - pivot.y),
                    (float)(((i & 4) == 0 ? b.minZ : b.maxZ) - pivot.z)));
            towardCamera = Math.max(towardCamera, -(v.y * forward.y + v.z * forward.z));
        }
        double depth = pivot.subtract(camera).dot(forward);
        return towardCamera < 1e-8 ? MAX_ZOOM : Math.max(.001, Math.min(MAX_ZOOM, (depth - SAFE_DEPTH) / towardCamera));
    }
    public double maintenanceFov() {
        return Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(MaintenanceCameraController.FOV / 2))
                / (available() ? zoom : DEFAULT_ZOOM)));
    }
    public void apply(PoseStack pose) {
        apply(pose, 1);
    }
    public void apply(PoseStack pose, float blend) {
        if (!field || !available()) return;
        var pivot = geometry.pivot();
        Vec3 pan = field ? right.scale(panX * unitsX * blend).add(up.scale(-panY * unitsY * blend)) : Vec3.ZERO;
        pose.translate(pivot.x + pan.x, pivot.y + pan.y, pivot.z + pan.z);
        pose.mulPose(rotation(blend));
        float scale = (float)(1 + (zoom - 1) * blend);
        pose.scale(scale, scale, scale);
        pose.translate(-pivot.x, -pivot.y, -pivot.z);
    }
    public void beginDrag(double x, double y, int button) {
        if (!field || !available() || !inViewport(x, y) || (button != 2 && button != 0)) return;
        dragButton = button; startX = lastX = x; startY = lastY = y; dragging = false;
    }
    public void drag(double x, double y, int button) {
        if (button != dragButton) return;
        // beginDrag owns the viewport check; keep a captured gesture until release/cancellation.
        if (!dragging && Math.hypot(x - startX, y - startY) < DRAG_THRESHOLD) return;
        if (!dragging) { interruptReset(); dragging = true; }
        double dx = x - lastX, dy = y - lastY; lastX = x; lastY = y;
        if (button == 0) {
            targetYaw += dx * DEGREES_PER_PIXEL;
            targetPitch = clamp(targetPitch + dy * DEGREES_PER_PIXEL, -PITCH_LIMIT, PITCH_LIMIT);
        } else {
            targetPanX = clamp(targetPanX + dx / (width - 24d), minPanX, maxPanX);
            targetPanY = clamp(targetPanY + dy / (height - 76d), minPanY, maxPanY);
        }
    }
    public void cancelDrag() { dragButton = -1; dragging = false; }
    public void scroll(double delta) {
        if (!available()) return;
        interruptReset();
        // Do not accumulate invisible wheel steps beyond the current orientation's safety cap.
        double limit = safeZoom(rotation());
        targetZoom = clamp(Math.min(targetZoom, limit) * Math.pow(1.12, delta), MIN_ZOOM, Math.max(MIN_ZOOM, limit));
    }
    public void reset(long now) {
        if (!available()) return;
        cancelDrag(); resetStart = now;
        resetYaw = yaw; resetPitch = pitch; resetZoom = zoom; resetX = panX; resetY = panY;
        resetEndYaw = yaw - Math.IEEEremainder(yaw, 360); // Nearest equivalent default, not multiple turns.
        targetYaw = resetEndYaw; targetPitch = targetPanX = targetPanY = 0; targetZoom = 1;
    }
    private void interruptReset() {
        if (resetStart == 0) return;
        resetStart = 0; targetYaw = yaw; targetPitch = pitch; targetZoom = zoom; targetPanX = panX; targetPanY = panY;
    }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
