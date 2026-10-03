package com.antaurora.apofirstlight.containersearch;

import com.antaurora.apofirstlight.noise.NoiseEvent;
import com.antaurora.apofirstlight.noise.NoiseSystem;
import com.antaurora.apofirstlight.noise.NoiseType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;

/**
 * Reveal state of one searchable container (persistent) plus its single shared search session (transient).
 *
 * <p>Owned by exactly one block entity as a plain field. The reveal mask is the only authority for access to a
 * slot; it does not depend on whether the loot table has been unpacked yet. Only revealed slots, the mask itself
 * and content-independent timing ever leave the server.
 */
public final class AflContainerSearchState {
    public static final String TAG = "AflContainerSearch";
    private static final int FORMAT = 1;
    /** 4096 slots; longer arrays in NBT are treated as corrupt and truncated. */
    private static final int MAX_MASK_LONGS = 64;

    // Persistent.
    private boolean initialized;
    private boolean required;
    private long seed;
    private boolean seedMissing;
    private BitSet revealed = new BitSet();
    /** Slot count the mask was written for; -1 when unknown. Reconciled against the live container on use. */
    private int slotCount = -1;

    // Session: never saved, rebuilt from open menus.
    private final List<AflContainerSearchMenu> menus = new ArrayList<>(2);
    private long lastTick = Long.MIN_VALUE;
    private boolean running;
    private int currentSlot = -1;
    private long currentStart;
    private int currentDuration;
    private long lastNoise;
    /** The search sound: when the clients are next reminded that it runs, and whether they were told it does (transient). */
    private static final int SOUND_SYNC_TICKS = 20;
    private long nextSoundSync;
    private boolean soundOn;
    private int[] order;
    private long orderSeed;

    /**
     * Creates the persistent state on first server contact. Returns false only while that is impossible
     * (no level yet, or a client-side copy); callers then answer from {@link AflSearchableContainer#aflSearchRequiredOnInit()}
     * without persisting anything.
     */
    public boolean ensureInitialized(AflSearchableContainer owner) {
        if (initialized) {
            reconcile(owner);
            return true;
        }
        Level level = owner.getLevel();
        if (level == null || level.isClientSide) {
            return false;
        }
        initialized = true;
        required = owner.aflSearchRequiredOnInit();
        seed = RandomSource.create().nextLong();
        seedMissing = false;
        revealed = new BitSet();
        slotCount = owner.getContainerSize();
        order = null;
        owner.setChanged();
        return true;
    }

    public boolean isRevealed(AflSearchableContainer owner, int slot) {
        if (slot < 0 || slot >= owner.getContainerSize()) {
            return false;
        }
        if (!ensureInitialized(owner)) {
            return !owner.aflSearchRequiredOnInit();
        }
        return !required || revealed.get(slot);
    }

    public boolean isComplete(AflSearchableContainer owner) {
        if (!ensureInitialized(owner)) {
            return !owner.aflSearchRequiredOnInit();
        }
        return !required || revealed.nextClearBit(0) >= slotCount;
    }

    public int revealedCount(AflSearchableContainer owner) {
        if (!ensureInitialized(owner)) {
            return owner.aflSearchRequiredOnInit() ? 0 : owner.getContainerSize();
        }
        return required ? revealed.get(0, slotCount).cardinality() : slotCount;
    }

    /** Sixteen reveal bits starting at slot {@code word * 16}; the unit of menu synchronization. */
    int maskWord(AflSearchableContainer owner, int word) {
        int bits = 0;
        for (int bit = 0; bit < 16; bit++) {
            if (isRevealed(owner, word * 16 + bit)) {
                bits |= 1 << bit;
            }
        }
        return bits;
    }

    boolean isRunning() {
        return running;
    }

    int currentSlot() {
        return currentSlot;
    }

    long currentStart() {
        return currentStart;
    }

    int currentDuration() {
        return currentDuration;
    }

    void attach(AflContainerSearchMenu menu) {
        if (!menus.contains(menu)) {
            menus.add(menu);
        }
    }

    /** The last searcher leaving pauses immediately; the unfinished slot's progress is discarded. */
    void detach(AflSearchableContainer owner, AflContainerSearchMenu menu) {
        menus.remove(menu);
        if (running && searchers().isEmpty()) {
            stop(owner);
        }
    }

    /**
     * Advances the one shared session by at most one step per game tick, however many viewers call it.
     * Driven from the viewers' menu synchronization, so it only runs while someone is actually looking.
     */
    void tick(AflSearchableContainer owner) {
        if (!(owner.getLevel() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (now == lastTick) {
            return;
        }
        lastTick = now;
        menus.removeIf(AflContainerSearchMenu::isStale);
        if (!ensureInitialized(owner)) {
            return;
        }
        List<ServerPlayer> searchers = searchers();
        if (owner.isRemoved() || searchers.isEmpty() || isComplete(owner)) {
            if (running) {
                stop(owner);
            }
            return;
        }
        if (!running) {
            running = true;
            lastNoise = now;
            nextSoundSync = now;
            owner.onAflSearchStarted(level);
        }
        if (currentSlot < 0 && !beginNextSlot(owner, level, now, searchers)) {
            return;
        }
        if (now - currentStart >= currentDuration) {
            int slot = currentSlot;
            revealed.set(slot);
            currentSlot = -1;
            owner.setChanged();
            owner.onAflSearchSlotRevealed(level, slot);
            if (isComplete(owner)) {
                running = false;
                soundOff(owner, level);
                owner.onAflSearchCompleted(level);
                return;
            }
            beginNextSlot(owner, level, now, searchers);
        }
        emitNoise(owner, level, now, searchers.get(0));
        syncSound(owner, level, now);
    }

    /**
     * While the session runs: tells the clients tracking the container's chunk to loop its search sound there
     * (client/ContainerSearchSoundController; 8 blocks, sounds.json), at once and then every {@link #SOUND_SYNC_TICKS} as
     * a keep-alive, so a player walking up later hears it too. One session per container: several viewers never stack it.
     */
    private void syncSound(AflSearchableContainer owner, ServerLevel level, long now) {
        var sound = owner.aflSearchSound();
        if (sound == null || now < nextSoundSync) return;
        nextSoundSync = now + SOUND_SYNC_TICKS;
        soundOn = true;
        AflNetwork.containerSearchSound(level, owner.getBlockPos(), sound.getLocation());
    }

    /** The session paused, finished or lost its container: the loop fades out (clients also end it when the reminders stop). */
    private void soundOff(AflSearchableContainer owner, ServerLevel level) {
        if (!soundOn) return;
        soundOn = false;
        AflNetwork.containerSearchSound(level, owner.getBlockPos(), null);
    }

    private boolean beginNextSlot(AflSearchableContainer owner, ServerLevel level, long now,
                                  List<ServerPlayer> searchers) {
        int next = nextHiddenSlot();
        if (next < 0) {
            return false;
        }
        currentSlot = next;
        currentStart = now;
        currentDuration = AflContainerSearchSpeed.slotDurationTicks(owner, level, next, seed, searchers);
        return true;
    }

    private void stop(AflSearchableContainer owner) {
        running = false;
        currentSlot = -1;
        if (owner.getLevel() instanceof ServerLevel level) {
            soundOff(owner, level);
            owner.onAflSearchStopped(level);
        }
    }

    private List<ServerPlayer> searchers() {
        List<ServerPlayer> searchers = new ArrayList<>(menus.size());
        for (AflContainerSearchMenu menu : menus) {
            ServerPlayer player = menu.searcher();
            if (player != null && !searchers.contains(player)) {
                searchers.add(player);
            }
        }
        return searchers;
    }

    private int nextHiddenSlot() {
        if (order == null || orderSeed != seed || order.length != slotCount) {
            order = searchOrder(seed, slotCount);
            orderSeed = seed;
        }
        for (int slot : order) {
            if (!revealed.get(slot)) {
                return slot;
            }
        }
        return -1;
    }

    private void emitNoise(AflSearchableContainer owner, ServerLevel level, long now, ServerPlayer source) {
        AflContainerSearchSettings settings = owner.aflSearchSettings();
        if (!settings.emitsNoise() || now - lastNoise < settings.noiseIntervalTicks()) {
            return;
        }
        lastNoise = now;
        NoiseSystem.emit(new NoiseEvent(source, owner.getBlockPos().getCenter(), NoiseType.INTERACTION, now,
                BuiltInRegistries.BLOCK.getKey(owner.getBlockState().getBlock()), settings.noiseRadius()), level);
    }

    /**
     * Applies the live slot count to a mask written for another size: bits past the end are dropped; new slots
     * are revealed only when the old mask was already complete (no loot can be waiting in them), else hidden.
     */
    private void reconcile(AflSearchableContainer owner) {
        int size = owner.getContainerSize();
        boolean changed = false;
        if (seedMissing) {
            seed = mix(owner.getBlockPos().asLong() ^ 0x5DEECE66DL);
            seedMissing = false;
            changed = true;
        }
        if (size != slotCount) {
            if (required && slotCount >= 0 && size > slotCount && revealed.nextClearBit(0) >= slotCount) {
                revealed.set(slotCount, size);
            }
            if (revealed.length() > size) {
                revealed.clear(size, revealed.length());
            }
            slotCount = size;
            order = null;
            if (currentSlot >= size) {
                currentSlot = -1;
            }
            changed = true;
        }
        Level level = owner.getLevel();
        if (changed && level != null && !level.isClientSide) {
            owner.setChanged();
        }
    }

    /**
     * Missing tag = not yet initialized (old worlds, fresh placements). A present tag with a missing mask keeps every
     * slot hidden (items stay, nothing is re-rolled); a missing seed is replaced by a position-derived one.
     */
    public void load(CompoundTag tag) {
        currentSlot = -1;
        order = null;
        if (!tag.contains(TAG, Tag.TAG_COMPOUND)) {
            initialized = false;
            required = false;
            seed = 0L;
            seedMissing = false;
            revealed = new BitSet();
            slotCount = -1;
            return;
        }
        CompoundTag data = tag.getCompound(TAG);
        initialized = true;
        required = data.getBoolean("Required");
        seedMissing = !data.contains("Seed", Tag.TAG_LONG);
        seed = data.getLong("Seed");
        long[] words = data.getLongArray("Revealed");
        revealed = BitSet.valueOf(words.length > MAX_MASK_LONGS ? Arrays.copyOf(words, MAX_MASK_LONGS) : words);
        slotCount = data.contains("Slots", Tag.TAG_INT) ? Math.max(-1, data.getInt("Slots")) : -1;
    }

    public void save(CompoundTag tag) {
        if (!initialized) {
            return;
        }
        CompoundTag data = new CompoundTag();
        data.putInt("Format", FORMAT);
        data.putBoolean("Required", required);
        if (!seedMissing) {
            data.putLong("Seed", seed);
        }
        if (required) {
            data.putLongArray("Revealed", revealed.toLongArray());
        }
        if (slotCount >= 0) {
            data.putInt("Slots", slotCount);
        }
        tag.put(TAG, data);
    }

    /** Read-only: never initializes, so inspecting a container does not commit its search decision. */
    String describe(AflSearchableContainer owner) {
        if (!initialized) {
            return "uninitialized (first contact would set required=" + owner.aflSearchRequiredOnInit() + ")";
        }
        reconcile(owner);
        long elapsed = currentSlot >= 0 && owner.getLevel() != null ? owner.getLevel().getGameTime() - currentStart : 0;
        return String.format(Locale.ROOT,
                "required=%s seed=%016x revealed=%d/%d complete=%s running=%s current=%d progress=%d/%d searchers=%d",
                required, seed, revealedCount(owner), owner.getContainerSize(), isComplete(owner), running,
                currentSlot, elapsed, currentSlot >= 0 ? currentDuration : 0, searchers().size());
    }

    /** Deterministic shuffle of all slot indices; independent of contents and stable across reloads. */
    static int[] searchOrder(long seed, int size) {
        int[] order = new int[Math.max(0, size)];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        long state = seed;
        for (int i = order.length - 1; i > 0; i--) {
            state += 0x9E3779B97F4A7C15L;
            int j = (int) Long.remainderUnsigned(mix(state), i + 1);
            int swap = order[i];
            order[i] = order[j];
            order[j] = swap;
        }
        return order;
    }

    /** Seed-derived value in [-1, 1) for one slot. Never looks at the slot's contents. */
    static double jitterUnit(long seed, int slot) {
        long bits = mix(seed ^ (slot + 1L) * 0xD1B54A32D192ED03L);
        return (bits >>> 11) * 0x1.0p-52 - 1.0;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
