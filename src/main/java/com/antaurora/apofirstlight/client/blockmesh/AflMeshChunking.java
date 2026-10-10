package com.antaurora.apofirstlight.client.blockmesh;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshHost;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfile;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfile.Part;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfiles;
import com.antaurora.apofirstlight.client.AflRenderDev;
import com.antaurora.apofirstlight.client.mesh.AflMeshCache;
import com.antaurora.apofirstlight.client.mesh.AflMeshModel;
import com.antaurora.apofirstlight.client.mesh.AflMeshPart;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which parts of an animated mesh block the chunk draws (docs/dev/render_performance_v1.md, step 2).
 * <p>
 * A part that rests (its animation channels settled, open or closed), is visible and is not drawn full bright has a fixed
 * pose until the block's state changes, so the chunk can draw it ({@link AflStaticMeshModel}) instead of the block entity
 * renderer re-submitting it every frame, twice with shaders. Its cutout geometry goes to the chunk's cutout layer and its
 * glass (translucent geometry) to the chunk's translucent layer (since 2026-10-08: drawn by the block entity renderer, glass
 * goes through the pack's block entity program, which in Sundial is the opaque gbuffers_block, so the door glass missed the
 * reflections the Storefront Glazing gets from the terrain translucent program). Emissive parts always stay in the renderer.
 * <ul>
 * <li>{@link Variant}: what the chunk should draw for one block entity, the parts by pre-order index plus the settled
 * channel values, facing and shading. Computed on the client thread from the host's state ({@link #desired}) and handed
 * to the chunk through the block entity's model data ({@link #modelData}, prepared ahead because Forge may ask for it on
 * a chunk builder thread). When it changes, the block entity requests a model data refresh and its chunk section is
 * marked for a rebuild. A part that starts moving or hides leaves the chunk at once; parts that come to rest join at most
 * every {@link #ADD_DELAY_NANOS}, and a host that keeps changing is held for {@link #FREEZE_NANOS} (no joining).</li>
 * <li>Confirmation: the chunk model reports each variant it meshes ({@link #confirm}). The renderer skips a part only when
 * the confirmed variant has it in the same pose and the chunk has had {@link #CONFIRM_FRAMES} frames to upload it, so a
 * part is never missing; when a part starts moving the chunk may still show it at rest for a frame or two.</li>
 * <li>Out of view (not drawn in the main pass for {@link #SEEN_NANOS}) a moving part is given to the chunk at its target
 * pose: no renderer draws it there, and a far door then snaps open instead of vanishing.</li>
 * <li>Every client tick the hosts with a moving channel or a changed animation target are re-checked, and every host about
 * once a second (visibility driven by block entity data, e.g. goods).</li>
 * </ul>
 * Off with the development switch {@code static_mesh off} ({@link AflRenderDev}), and for blocks whose model is not
 * {@link AflStaticMeshModel}: the renderer then draws everything, as before.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AflMeshChunking {
    static final ModelProperty<Snapshot> SNAPSHOT = new ModelProperty<>();
    private static final int CONFIRM_FRAMES = 2, SWEEP_TICKS = 20, BURST = 8;
    private static final long ADD_DELAY_NANOS = 200_000_000L, SEEN_NANOS = 250_000_000L,
            BURST_NANOS = 4_000_000_000L, FREEZE_NANOS = 10_000_000_000L;
    private static final long[] NONE = new long[2];
    /** How far from 0 or 1 a resting value channel counts as between its ends. */
    private static final double BETWEEN = 1e-4;

    /** What the chunk draws for one block entity: parts {@code lo/hi} (pre-order index bits), channels at 1 {@code ones}. */
    record Variant(AflBlockMeshProfile profile, Direction facing, int shading, long lo, long hi, int ones) {
        boolean has(int i) { return i < 64 ? (lo >>> i & 1L) != 0 : (hi >>> (i - 64) & 1L) != 0; }
        boolean empty() { return lo == 0 && hi == 0; }
    }

    /** The model data value: the variant, its owner (identity only) and the owner's request number. */
    record Snapshot(BlockEntity owner, Variant variant, long seq) {}

    private record Confirmed(Variant variant, long lo, long hi, long frame, long seq) {}

    /** A profile's parts in pre-order, with their parents, channels and chained channel bits. */
    static final class Layout {
        final AflMeshModel mesh;
        final Part[] parts;
        final int[] parent, channel, chain;
        final boolean[] geometry;
        final String[] channels;
        final Map<Part, Integer> index = new IdentityHashMap<>();

        Layout(AflBlockMeshProfile profile, AflMeshModel mesh) {
            this.mesh = mesh;
            channels = profile.animations().keySet().stream().sorted().toArray(String[]::new);
            var list = new ArrayList<Part>();
            var parents = new ArrayList<Integer>();
            for (var root : profile.roots()) collect(root, -1, list, parents);
            int n = list.size();
            parts = list.toArray(Part[]::new);
            parent = new int[n]; channel = new int[n]; chain = new int[n]; geometry = new boolean[n];
            for (int i = 0; i < n; i++) {
                index.put(parts[i], i);
                parent[i] = parents.get(i);
                var motion = parts[i].motion();
                int c = -1;
                if (motion != null) for (int j = 0; j < channels.length; j++) if (channels[j].equals(motion.channel())) c = j;
                channel[i] = c;
                chain[i] = (parent[i] >= 0 ? chain[parent[i]] : 0) | (c >= 0 ? 1 << c : 0);
                geometry[i] = !mesh.parts(parts[i].bone(), AflMeshPart.Layer.CUTOUT).isEmpty()
                        || !mesh.parts(parts[i].bone(), AflMeshPart.Layer.TRANSLUCENT).isEmpty();
            }
        }

        private static void collect(Part part, int parent, List<Part> list, List<Integer> parents) {
            int self = list.size();
            list.add(part);
            parents.add(parent);
            for (var child : part.children()) collect(child, self, list, parents);
        }

        int indexOf(Part part) {
            Integer i = index.get(part);
            return i == null ? -1 : i;
        }
    }

    private static final class HostState {
        Variant requested;
        volatile ModelData data;
        volatile Confirmed confirmed;
        long seq, lastRequest, lastSeen = Long.MIN_VALUE / 2, frozenUntil;
        int version = Integer.MIN_VALUE;
        boolean moving, pendingAdd;
        final long[] recent = new long[BURST];
        int recentAt;
    }

    private static final Map<BlockEntity, HostState> HOSTS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<AflBlockMeshProfile, Layout> LAYOUTS = new ConcurrentHashMap<>();
    private static volatile long layoutGeneration = Long.MIN_VALUE;
    private static volatile long frame;
    private static int tick;

    private AflMeshChunking() {}

    static long frame() {
        return frame;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) frame++;
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) HOSTS.clear();
    }

    static Layout layout(AflBlockMeshProfile profile, AflMeshModel mesh) {
        long generation = AflMeshCache.snapshot().generation();
        if (generation != layoutGeneration) { LAYOUTS.clear(); layoutGeneration = generation; }
        Layout layout = LAYOUTS.get(profile);
        if (layout == null || layout.mesh != mesh) {
            layout = new Layout(profile, mesh);
            LAYOUTS.put(profile, layout);
        }
        return layout;
    }

    /** The chunk draws this block's resting parts: the switch is on and the block's model is the static mesh model. */
    static boolean enabled(BlockEntity entity) {
        return AflRenderDev.staticMesh() && entity.getLevel() != null && entity.getLevel().isClientSide
                && AflStaticMeshModel.serves(entity.getBlockState());
    }

    private static HostState state(BlockEntity entity) {
        return HOSTS.computeIfAbsent(entity, k -> new HostState());
    }

    /** The host's model data ({@link AflAnimatedMeshHost#getModelData}): the prepared variant, any thread. */
    public static ModelData modelData(AflAnimatedMeshHost host) {
        if (!(host instanceof BlockEntity entity) || entity.getLevel() == null || !entity.getLevel().isClientSide) return ModelData.EMPTY;
        HostState s = state(entity);
        if (s.data == null && RenderSystem.isOnRenderThread() && enabled(entity))
            evaluate(entity, host, s, System.nanoTime(), false);
        ModelData data = s.data;
        return data == null ? ModelData.EMPTY : data;
    }

    /** Called by the chunk model when it meshes a snapshot: the variant and the parts it really drew. */
    static void confirm(Snapshot snapshot, long lo, long hi) {
        HostState s = HOSTS.get(snapshot.owner());
        if (s == null) return;
        Confirmed c = s.confirmed;
        if (c != null && c.seq >= snapshot.seq()) return;
        s.confirmed = new Confirmed(snapshot.variant(), lo, hi, frame, snapshot.seq());
    }

    /**
     * For the renderer, each pass: the parts it must not draw because the chunk draws them in the same pose
     * ({@code {lo, hi}} pre-order index bits). In the main pass it also keeps the chunk's variant current.
     */
    static long[] skip(BlockEntity entity, AflAnimatedMeshHost host, AflBlockMeshProfile profile, AflMeshModel mesh,
                       double time, boolean shadowPass) {
        if (!enabled(entity)) return NONE;
        HostState s = state(entity);
        long now = System.nanoTime();
        if (!shadowPass) s.lastSeen = now;
        Layout layout = layout(profile, mesh);
        Variant current = desired(entity, host, profile, layout, time, now - s.lastSeen > SEEN_NANOS);
        if (!shadowPass) request(entity, s, current, now);
        Confirmed c = s.confirmed;
        if (c == null || frame < c.frame + CONFIRM_FRAMES) return NONE;
        Variant cv = c.variant;
        if (cv.profile() != profile || cv.facing() != current.facing() || cv.shading() != current.shading()) return NONE;
        long lo = c.lo & current.lo(), hi = c.hi & current.hi();
        int diff = cv.ones() ^ current.ones();
        if (diff != 0) for (int i = 0; i < layout.parts.length; i++) {
            if ((layout.chain[i] & diff) == 0) continue;
            if (i < 64) lo &= ~(1L << i); else hi &= ~(1L << (i - 64));
        }
        return lo == 0 && hi == 0 ? NONE : new long[]{lo, hi};
    }

    /** What the chunk should draw now. {@code snap}: a moving channel counts as already at its target. */
    static Variant desired(BlockEntity entity, AflAnimatedMeshHost host, AflBlockMeshProfile profile, Layout layout,
                           double time, boolean snap) {
        var animation = host.meshAnimation();
        int n = layout.parts.length;
        boolean[] visible = new boolean[n], settled = new boolean[n];
        long lo = 0, hi = 0;
        int ones = 0, used = 0;
        for (int i = 0; i < n; i++) {
            Part part = layout.parts[i];
            int parent = layout.parent[i];
            boolean shown = (parent < 0 || visible[parent]) && host.meshPartVisible(part.bone());
            boolean rest = parent < 0 || settled[parent];
            int c = layout.channel[i];
            if (c >= 0 && rest) {
                double value = snap ? animation.targetValue(layout.channels[c]) : animation.settled(layout.channels[c], time);
                // a value channel resting between its ends (a gauge needle) stays with the renderer: the chunk poses 0 or 1
                if (value < 0 || value > BETWEEN && value < 1 - BETWEEN) rest = false;
                else if (value > 0.5) ones |= 1 << c;
            }
            visible[i] = shown;
            settled[i] = rest;
            if (shown && rest && layout.geometry[i] && !host.meshPartEmissive(part.bone())) {
                if (i < 64) lo |= 1L << i; else hi |= 1L << (i - 64);
                used |= layout.chain[i];
            }
        }
        Direction facing = profile.horizontalFacing() ? host.meshFacing() : Direction.NORTH;
        return new Variant(profile, facing, shading(entity.getLevel()), lo, hi, ones & used);
    }

    /** 0: a shader pack lights it (white, the chunk's own shading); 1: overworld entity lights; 2: nether entity lights. */
    private static int shading(Level level) {
        if (AflShaderCompat.shaderPackInUse()) return 0;
        return level instanceof net.minecraft.client.multiplayer.ClientLevel client && client.effects().constantAmbientLight() ? 2 : 1;
    }

    private static boolean moving(AflAnimatedMeshHost host, AflBlockMeshProfile profile, double time) {
        var animation = host.meshAnimation();
        for (String channel : profile.animations().keySet()) if (animation.settled(channel, time) < 0) return true;
        return false;
    }

    /** Recomputes the variant (client thread) and asks for a rebuild when it changed. */
    private static void evaluate(BlockEntity entity, AflAnimatedMeshHost host, HostState s, long now, boolean ask) {
        var profile = AflBlockMeshProfiles.get(host.meshProfile());
        var mesh = profile == null ? null : AflMeshCache.snapshot().get(profile.geometry());
        if (profile == null || mesh == null || entity.getLevel() == null) return;
        double time = entity.getLevel().getGameTime() + Minecraft.getInstance().getFrameTime();
        s.moving = moving(host, profile, time);
        Variant want = desired(entity, host, profile, layout(profile, mesh), time, now - s.lastSeen > SEEN_NANOS);
        if (ask) request(entity, s, want, now);
        else if (s.requested == null) publish(s, entity, want, now);
    }

    private static void request(BlockEntity entity, HostState s, Variant want, long now) {
        Variant old = s.requested;
        if (old != null && now < s.frozenUntil) want = withParts(want, want.lo() & old.lo(), want.hi() & old.hi());
        if (want.equals(old)) { s.pendingAdd = false; return; }
        if (old != null && !removes(old, want) && now - s.lastRequest < ADD_DELAY_NANOS) { s.pendingAdd = true; return; }
        s.pendingAdd = false;
        publish(s, entity, want, now);
        // a host that keeps changing (a blinking part) would rebuild its section all the time: hold it
        s.recent[s.recentAt++ % BURST] = now;
        if (s.recentAt >= BURST && now - s.recent[s.recentAt % BURST] < BURST_NANOS) s.frozenUntil = now + FREEZE_NANOS;
        entity.requestModelDataUpdate();
        BlockPos pos = entity.getBlockPos();
        var renderer = Minecraft.getInstance().levelRenderer;
        if (renderer != null) renderer.setSectionDirty(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    private static void publish(HostState s, BlockEntity entity, Variant want, long now) {
        s.requested = want;
        s.seq++;
        s.lastRequest = now;
        s.data = ModelData.builder().with(SNAPSHOT, new Snapshot(entity, want, s.seq)).build();
    }

    private static Variant withParts(Variant v, long lo, long hi) {
        return new Variant(v.profile(), v.facing(), v.shading(), lo, hi, v.ones());
    }

    /** The new variant drops a part, moves one, or draws differently: the chunk must change before the renderer can. */
    private static boolean removes(Variant old, Variant want) {
        if (old.profile() != want.profile() || old.facing() != want.facing() || old.shading() != want.shading()) return true;
        if ((old.lo() & ~want.lo()) != 0 || (old.hi() & ~want.hi()) != 0) return true;   // a part leaves
        int diff = old.ones() ^ want.ones();
        if (diff == 0) return false;
        Layout layout = LAYOUTS.get(old.profile());
        if (layout == null) return true;
        for (int i = 0; i < layout.parts.length; i++)   // a part in both changes its pose
            if (old.has(i) && want.has(i) && (layout.chain[i] & diff) != 0) return true;
        return false;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick++;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        List<Map.Entry<BlockEntity, HostState>> hosts;
        synchronized (HOSTS) { hosts = new ArrayList<>(HOSTS.entrySet()); }
        long now = System.nanoTime();
        for (var e : hosts) {
            BlockEntity entity = e.getKey();
            HostState s = e.getValue();
            if (entity == null) continue;
            if (entity.isRemoved() || entity.getLevel() != mc.level) { HOSTS.remove(entity); continue; }
            if (!(entity instanceof AflAnimatedMeshHost host) || !enabled(entity)) continue;
            int version = host.meshAnimation().version();
            boolean due = s.moving || s.pendingAdd || version != s.version
                    || Math.floorMod(tick + entity.getBlockPos().hashCode(), SWEEP_TICKS) == 0;
            if (!due) continue;
            s.version = version;
            host.refreshMeshAnimationTargets();
            evaluate(entity, host, s, now, true);
        }
    }
}
