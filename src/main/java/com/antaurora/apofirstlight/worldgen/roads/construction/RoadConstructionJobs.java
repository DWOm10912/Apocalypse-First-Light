package com.antaurora.apofirstlight.worldgen.roads.construction;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Server-thread-only, explicitly authorized construction. It never owns chunk tickets and never
 * starts work on load. Saved before/after snapshots support reconciliation, not atomic rollback.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RoadConstructionJobs extends SavedData {
    public static final String DATA_ID = "afl_road_construction_v1";
    public static final int MAX_PLANS = 4;
    private static final int MAX_TOTAL_EDITS = 240_000, MAX_TOTAL_GUARDS = 1_000_000;
    private static final int MAX_PLAN_EDITS = 240_000, MAX_PLAN_GUARDS = 500_000, MAX_PALETTE = 8192;
    private static final Map<ServerLevel, RoadConstructionJobs> ACTIVE = new WeakHashMap<>();
    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private String loadFailure = "";
    private String lastSaveFailure = "";

    public enum State { PREPARED, RUNNING, PAUSED, COMPLETED, FAILED }
    public enum Phase { VALIDATING, WRITING }
    public record StatusView(String planId, State state, Phase phase, int completedEdits, int totalEdits,
            int checkedGuards, int totalGuards, int completedChunks, int totalChunks,
            long createdTick, long expiresTick, BoundsXZ bounds, String reason, boolean writeStarted) {}

    private record Edit(BlockPos pos, BlockState before, BlockState after) {}
    private record Guard(BlockPos pos, BlockState state) {}

    private static final class Job {
        String id, sourceId, version, dimension;
        UUID owner;
        BoundsXZ bounds;
        long created, expires;
        State state = State.PREPARED;
        Phase phase = Phase.VALIDATING;
        String reason = "AWAITING_EXPLICIT_CONFIRM";
        int checksPerTick, editsPerTick, checkCursor, editCursor, chunkCheckCursor;
        boolean writeStarted, chunkChecked;
        long currentChunk = Long.MIN_VALUE;
        List<Edit> edits = List.of();
        List<Guard> guards = List.of();
        final Map<Long, Edit> editsByPosition = new HashMap<>();
        final Map<Long, List<Guard>> guardsByChunk = new HashMap<>();
        final Set<Long> completedChunks = new HashSet<>();
        int chunks;

        void index() {
            editsByPosition.clear(); guardsByChunk.clear();
            Set<Long> seenGuards = new HashSet<>();
            Set<BlockState> palette = new HashSet<>();
            for (Edit edit : edits) {
                requirePosition(bounds, edit.pos);
                if (edit.before == edit.after || edit.before.hasBlockEntity() || edit.after.hasBlockEntity()
                        || !edit.before.getFluidState().isEmpty() || !edit.after.getFluidState().isEmpty()
                        || editsByPosition.put(edit.pos.asLong(), edit) != null)
                    throw new IllegalArgumentException("INVALID_OR_DUPLICATE_EDIT");
                palette.add(edit.before); palette.add(edit.after);
            }
            for (Guard guard : guards) {
                requirePosition(bounds, guard.pos);
                if (!seenGuards.add(guard.pos.asLong()) || guard.state.hasBlockEntity())
                    throw new IllegalArgumentException("INVALID_OR_DUPLICATE_GUARD");
                Edit edit = editsByPosition.get(guard.pos.asLong());
                if (edit != null && edit.before != guard.state)
                    throw new IllegalArgumentException("GUARD_BEFORE_MISMATCH");
                guardsByChunk.computeIfAbsent(chunk(guard.pos), k -> new ArrayList<>()).add(guard);
                palette.add(guard.state);
            }
            if (!seenGuards.containsAll(editsByPosition.keySet()))
                throw new IllegalArgumentException("EDIT_MISSING_SNAPSHOT_GUARD");
            Set<Long> editedChunks = new HashSet<>();
            for (Edit edit : edits) editedChunks.add(chunk(edit.pos));
            chunks = editedChunks.size();
            if (chunks > 512 || guardsByChunk.size() > 512)
                throw new IllegalArgumentException("CHUNK_BUDGET_EXCEEDED");
            if (palette.size() > MAX_PALETTE) throw new IllegalArgumentException("SNAPSHOT_PALETTE_BUDGET_EXCEEDED");
        }

        StatusView view() {
            return new StatusView(id, state, phase, editCursor, edits.size(), checkCursor, guards.size(),
                    completedChunks.size(), chunks, created, expires, bounds, reason, writeStarted);
        }
    }

    private RoadConstructionJobs() {}

    private static RoadConstructionJobs get(ServerLevel level) {
        requireServerThread(level);
        if (!level.dimension().equals(Level.OVERWORLD)) throw new IllegalArgumentException("OVERWORLD_REQUIRED");
        RoadConstructionJobs data = level.getDataStorage().computeIfAbsent(
                RoadConstructionJobs::load, () -> {
                    RoadConstructionJobs fresh = new RoadConstructionJobs();
                    Path file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve(DATA_ID + ".dat");
                    // DimensionDataStorage catches disk decoding errors and otherwise creates an
                    // empty ledger. Existing unreadable data must never authorize a fresh rebuild.
                    if (Files.exists(file)) fresh.loadFailure = "EXISTING_LEDGER_COULD_NOT_BE_READ";
                    return fresh;
                }, DATA_ID);
        if (!data.loadFailure.isEmpty())
            throw new IllegalStateException("CONSTRUCTION_SAVED_DATA_INVALID: " + data.loadFailure);
        ACTIVE.put(level, data);
        return data;
    }

    public static StatusView prepare(ServerLevel level, UUID owner, RoadConstructionPlan plan,
                                     RoadConstructionConfig config) {
        RoadConstructionJobs data = get(level);
        if (plan.status() != RoadConstructionPlan.Status.PREVIEW_READY)
            throw new IllegalArgumentException("PREFLIGHT_NOT_READY");
        if (!plan.dimension().equals(level.dimension().location()))
            throw new IllegalArgumentException("PLAN_DIMENSION_MISMATCH");
        if (!plan.version().equals(RoadConstructionPlanner.VERSION))
            throw new IllegalArgumentException("PLAN_VERSION_MISMATCH");
        if (data.jobs.containsKey(plan.planId())) throw new IllegalArgumentException("PLAN_ID_ALREADY_RECORDED");
        if (data.jobs.size() >= MAX_PLANS) throw new IllegalArgumentException("SAVED_PLAN_CAPACITY_REACHED");
        if (plan.edits().isEmpty() || plan.edits().size() > Math.min(config.maxEdits(), MAX_PLAN_EDITS)
                || plan.guards().isEmpty() || plan.guards().size() > Math.min(config.maxGuards(), MAX_PLAN_GUARDS))
            throw new IllegalArgumentException("SNAPSHOT_BUDGET_EXCEEDED");
        if (data.jobs.values().stream().mapToInt(j -> j.edits.size()).sum() + plan.edits().size() > MAX_TOTAL_EDITS
                || data.jobs.values().stream().mapToInt(j -> j.guards.size()).sum() + plan.guards().size() > MAX_TOTAL_GUARDS)
            throw new IllegalArgumentException("TOTAL_SNAPSHOT_BUDGET_EXCEEDED");
        requireBounds(plan.bounds());
        for (Job old : data.jobs.values()) {
            if (old.bounds.intersects(plan.bounds()))
                throw new IllegalArgumentException("RECORDED_CONSTRUCTION_REGION_CONFLICT: " + old.id);
        }
        checkProtection(level, plan.bounds());
        Job job = new Job();
        job.id = checkedText(plan.planId(), 160); job.sourceId = checkedText(plan.sourcePlanId(), 240);
        job.version = checkedText(plan.version(), 160); job.dimension = plan.dimension().toString();
        job.owner = owner; job.bounds = plan.bounds(); job.created = level.getGameTime();
        job.expires = Math.addExact(job.created, config.planLifetimeTicks());
        job.checksPerTick = config.maxChecksPerTick(); job.editsPerTick = config.maxEditsPerTick();
        requireBudgets(job);
        job.edits = plan.edits().stream().map(e -> new Edit(e.pos().immutable(), e.before(), e.after()))
                .sorted(EDIT_ORDER).toList();
        job.guards = plan.guards().stream().map(g -> new Guard(g.pos().immutable(), g.state())).toList();
        job.index();
        for (Guard guard : job.guards) {
            if (level.isOutsideBuildHeight(guard.pos) || !level.getWorldBorder().isWithinBounds(guard.pos))
                throw new IllegalArgumentException("SNAPSHOT_OUTSIDE_WORLD_BOUNDS");
        }
        data.jobs.put(job.id, job);
        data.setDirty();
        return job.view();
    }

    public static StatusView start(ServerLevel level, UUID owner, String id) {
        return activate(level, owner, id, false);
    }

    public static StatusView resume(ServerLevel level, UUID owner, String id) {
        return activate(level, owner, id, true);
    }

    private static StatusView activate(ServerLevel level, UUID owner, String id, boolean resume) {
        RoadConstructionJobs data = get(level);
        Job job = data.owned(level, owner, id);
        if (job.state != (resume ? State.PAUSED : State.PREPARED))
            throw new IllegalArgumentException("INVALID_CONSTRUCTION_STATE: " + job.state);
        if (!job.version.equals(RoadConstructionPlanner.VERSION))
            throw new IllegalArgumentException("PLAN_VERSION_MISMATCH");
        if (!job.writeStarted && level.getGameTime() >= job.expires) {
            data.stop(job, State.FAILED, "PLAN_EXPIRED");
            throw new IllegalArgumentException("PLAN_EXPIRED");
        }
        if (data.jobs.values().stream().anyMatch(j -> j.state == State.RUNNING))
            throw new IllegalArgumentException("ANOTHER_CONSTRUCTION_IS_RUNNING");
        checkProtection(level, job.bounds);
        job.checkCursor = 0; job.editCursor = 0; job.completedChunks.clear();
        job.currentChunk = Long.MIN_VALUE; job.chunkCheckCursor = 0; job.chunkChecked = false;
        job.state = State.RUNNING; job.phase = Phase.VALIDATING;
        job.reason = resume ? "RECONCILING_BEFORE_AFTER_SNAPSHOT" : "VALIDATING_FULL_WORLD_SNAPSHOT";
        data.setDirty();
        // Persist the approved exact plan before any END-tick can change world blocks.
        if (!data.checkpoint(level, job)) throw new IllegalStateException(job.reason);
        return job.view();
    }

    public static StatusView pause(ServerLevel level, UUID owner, String id) {
        RoadConstructionJobs data = get(level);
        Job job = data.owned(level, owner, id);
        if (job.state != State.RUNNING) throw new IllegalArgumentException("PLAN_NOT_RUNNING");
        data.stop(job, State.PAUSED, "PAUSED_BY_OWNER");
        return job.view();
    }

    public static StatusView status(ServerLevel level, UUID owner, String id) {
        return get(level).owned(level, owner, id).view();
    }

    public static List<StatusView> list(ServerLevel level, UUID owner) {
        return get(level).jobs.values().stream().filter(j -> j.owner.equals(owner)).map(Job::view).toList();
    }

    private Job owned(ServerLevel level, UUID owner, String id) {
        Job job = jobs.get(id);
        if (job == null) throw new IllegalArgumentException("UNKNOWN_CONSTRUCTION_PLAN");
        if (!job.owner.equals(owner)) throw new IllegalArgumentException("PLAN_OWNER_MISMATCH");
        if (!job.dimension.equals(level.dimension().location().toString()))
            throw new IllegalArgumentException("PLAN_DIMENSION_MISMATCH");
        return job;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        RoadConstructionJobs data = ACTIVE.get(level);
        if (data == null) return; // Loading a dimension or chunk alone never activates this service.
        for (Job job : data.jobs.values()) {
            if (job.state != State.RUNNING) continue;
            try {
                data.tick(level, job);
            } catch (RuntimeException failure) {
                data.stop(job, State.FAILED, "EXECUTOR_EXCEPTION: " + failure.getClass().getSimpleName()
                        + ": " + String.valueOf(failure.getMessage()));
            }
            break; // Exactly one running job, hence one shared budget per level.
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        RoadConstructionJobs data = ACTIVE.remove(event.getLevel());
        if (data == null) return;
        for (Job job : data.jobs.values()) if (job.state == State.RUNNING)
            data.stop(job, State.PAUSED, "LEVEL_UNLOADED_EXPLICIT_RESUME_REQUIRED");
    }

    @SubscribeEvent
    public static void onPlayerBreak(BlockEvent.BreakEvent event) {
        pauseForExternalEdit(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        pauseForExternalEdit(event.getLevel(), event.getPos());
    }

    private static void pauseForExternalEdit(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        RoadConstructionJobs data = ACTIVE.get(server);
        if (data == null) return;
        for (Job job : data.jobs.values()) {
            if (job.state == State.RUNNING && job.bounds.contains(pos.getX(), pos.getZ()))
                data.stop(job, State.PAUSED, "EXTERNAL_BLOCK_EDIT_REQUIRES_REVALIDATION");
        }
    }

    private void tick(ServerLevel level, Job job) {
        if(job.sourceId.startsWith("segment:") && level.players().stream().anyMatch(p ->
                job.bounds.expand(2).contains(p.blockPosition().getX(),p.blockPosition().getZ()))) {
            stop(job,State.PAUSED,"PLAYER_INSIDE_SEGMENT;STEP_OUTSIDE_AND_RESUME");return;
        }
        if (!lastSaveFailure.isEmpty()) { stop(job, State.FAILED, "LEDGER_SAVE_FAILED: " + lastSaveFailure); return; }
        if (!job.writeStarted && level.getGameTime() >= job.expires) {
            stop(job, State.FAILED, "PLAN_EXPIRED_DURING_VALIDATION"); return;
        }
        int checks = job.checksPerTick;
        if (job.phase == Phase.VALIDATING) {
            while (checks-- > 0 && job.checkCursor < job.guards.size()) {
                if (!checkGuard(level, job, job.guards.get(job.checkCursor))) return;
                job.checkCursor++;
            }
            if (job.checkCursor < job.guards.size()) { setDirty(); return; }
            checkProtection(level, job.bounds);
            job.phase = Phase.WRITING;
            job.reason = "CONSTRUCTING_APPROVED_SNAPSHOT";
            job.writeStarted = true;
            setDirty();
            // Save the reconciliation permission before the first world edit. This is not an
            // atomic world/data transaction; normal world save and user backups remain required.
            checkpoint(level, job);
            return;
        }
        if (job.editCursor >= job.edits.size()) { stop(job, State.COMPLETED, "ALL_APPROVED_EDITS_APPLIED"); return; }
        long chunk = chunk(job.edits.get(job.editCursor).pos);
        if (job.currentChunk != chunk) {
            if (!loaded(level, job, job.edits.get(job.editCursor).pos)) return;
            job.currentChunk = chunk; job.chunkCheckCursor = 0; job.chunkChecked = false;
        }
        List<Guard> local = job.guardsByChunk.get(chunk);
        if (!job.chunkChecked) {
            while (checks-- > 0 && job.chunkCheckCursor < local.size()) {
                if (!checkGuard(level, job, local.get(job.chunkCheckCursor))) return;
                job.chunkCheckCursor++;
            }
            if (job.chunkCheckCursor < local.size()) return;
            job.chunkChecked = true;
        }
        ChunkPos cp = new ChunkPos(chunk);
        var actualChunk = level.getChunkSource().getChunkNow(cp.x, cp.z);
        if (actualChunk == null) { stop(job, State.PAUSED, "CHUNK_NOT_LOADED"); return; }
        if (actualChunk.getAllStarts().values().stream().anyMatch(start -> start.isValid())
                || actualChunk.getAllReferences().values().stream().anyMatch(ref -> !ref.isEmpty())) {
            stop(job, State.FAILED, "KNOWN_STRUCTURE_CHUNK_CONFLICT"); return;
        }
        checkProtection(level, new BoundsXZ(cp.getMinBlockX(), cp.getMinBlockZ(),
                cp.getMaxBlockX() + 1, cp.getMaxBlockZ() + 1));
        // Never cross into another chunk within one write batch. No chunk tickets or force loads.
        int writes = job.editsPerTick;
        while (writes-- > 0 && job.editCursor < job.edits.size()
                && chunk(job.edits.get(job.editCursor).pos) == chunk) {
            Edit edit = job.edits.get(job.editCursor);
            if (!loaded(level, job, edit.pos)) return;
            if (level.getBlockEntity(edit.pos) != null || !level.getFluidState(edit.pos).isEmpty()) {
                stop(job, State.FAILED, "BLOCK_ENTITY_OR_FLUID_APPEARED: " + edit.pos.toShortString()); return;
            }
            BlockState actual = level.getBlockState(edit.pos);
            if (actual != edit.after) {
                if (actual != edit.before) {
                    stop(job, State.FAILED, "WORLD_CHANGED_SINCE_PREFLIGHT: " + edit.pos.toShortString()); return;
                }
                boolean applied = level.setBlock(edit.pos, edit.after,
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
                if (!applied || level.getBlockState(edit.pos) != edit.after) {
                    stop(job, State.FAILED, "BLOCK_WRITE_FAILED: " + edit.pos.toShortString()); return;
                }
            }
            job.editCursor++;
        }
        job.chunkChecked = false; job.chunkCheckCursor = 0; // Recheck support/headroom before the next batch too.
        setDirty();
        if (job.editCursor == job.edits.size() || chunk(job.edits.get(job.editCursor).pos) != chunk)
            job.completedChunks.add(chunk);
        if (job.editCursor == job.edits.size()) stop(job, State.COMPLETED, "ALL_APPROVED_EDITS_APPLIED");
    }

    private boolean checkGuard(ServerLevel level, Job job, Guard guard) {
        if (!loaded(level, job, guard.pos)) return false;
        if (level.getBlockEntity(guard.pos) != null) {
            stop(job, State.FAILED, "BLOCK_ENTITY_APPEARED: " + guard.pos.toShortString()); return false;
        }
        BlockState actual = level.getBlockState(guard.pos);
        Edit edit = job.editsByPosition.get(guard.pos.asLong());
        if (actual == guard.state || (job.writeStarted && edit != null && actual == edit.after)) return true;
        stop(job, State.FAILED, "WORLD_SNAPSHOT_MISMATCH: " + guard.pos.toShortString());
        return false;
    }

    private boolean loaded(ServerLevel level, Job job, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            stop(job, State.FAILED, "WORLD_BOUNDS_CHANGED"); return false;
        }
        if (level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return true;
        stop(job, State.PAUSED, "CHUNK_NOT_LOADED: " + (pos.getX() >> 4) + "," + (pos.getZ() >> 4));
        return false;
    }

    private void stop(Job job, State state, String reason) {
        job.state = state; job.reason = reason.length() > 512 ? reason.substring(0, 512) : reason;
        setDirty();
    }

    private boolean checkpoint(ServerLevel level, Job job) {
        try {
            level.getDataStorage().save();
        } catch (RuntimeException failure) {
            stop(job, State.FAILED, "LEDGER_CHECKPOINT_EXCEPTION: " + failure.getClass().getSimpleName());
            return false;
        }
        if (lastSaveFailure.isEmpty() && !isDirty()) return true;
        stop(job, State.FAILED, "LEDGER_CHECKPOINT_FAILED: " + lastSaveFailure);
        return false;
    }

    private static void checkProtection(ServerLevel level, BoundsXZ bounds) {
        for (var claim : RoadConstructionProtection.query(level, bounds)) {
            if (claim.dimension().equals(level.dimension())
                    && claim.boundsXZ().expand(claim.exclusionMargin()).intersects(bounds))
                throw new IllegalArgumentException("PROTECTED_STRUCTURE_CONFLICT: " + claim.id());
        }
    }

    private static long chunk(BlockPos pos) { return ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4); }
    private static final Comparator<Edit> EDIT_ORDER = Comparator
            .comparingInt((Edit e) -> e.pos.getX() >> 4).thenComparingInt(e -> e.pos.getZ() >> 4)
            .thenComparingInt(e -> e.after.isAir() ? 0 : 1)
            .thenComparingInt(e -> e.after.isAir() ? -e.pos.getY() : e.pos.getY())
            .thenComparingInt(e -> e.pos.getX()).thenComparingInt(e -> e.pos.getZ());

    private static void requireServerThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("SERVER_THREAD_REQUIRED");
    }
    private static void requireBounds(BoundsXZ bounds) {
        if (bounds.isEmpty() || bounds.width() > 1024 || bounds.depth() > 1024
                || Math.abs((long) bounds.minX()) > 30_000_000 || Math.abs((long) bounds.maxXExclusive()) > 30_000_000
                || Math.abs((long) bounds.minZ()) > 30_000_000 || Math.abs((long) bounds.maxZExclusive()) > 30_000_000)
            throw new IllegalArgumentException("INVALID_SNAPSHOT_BOUNDS");
    }
    private static void requirePosition(BoundsXZ bounds, BlockPos pos) {
        if (!bounds.contains(pos.getX(), pos.getZ()) || pos.getY() < -2048 || pos.getY() > 2047)
            throw new IllegalArgumentException("SNAPSHOT_POSITION_OUTSIDE_BOUNDS");
    }
    private static void requireBudgets(Job job) {
        if (job.checksPerTick < 1 || job.checksPerTick > 8192 || job.editsPerTick < 1 || job.editsPerTick > 1024
                || job.created < 0 || job.expires <= job.created || job.expires - job.created > 72_000)
            throw new IllegalArgumentException("INVALID_EXECUTION_BUDGET");
    }
    private static String checkedText(String text, int limit) {
        if (text == null || text.isBlank() || text.length() > limit) throw new IllegalArgumentException("INVALID_SNAPSHOT_ID");
        return text;
    }

    private static RoadConstructionJobs load(CompoundTag root) {
        RoadConstructionJobs data = new RoadConstructionJobs();
        try {
            if (root.getInt("schema") != 1) throw new IllegalArgumentException("UNSUPPORTED_SCHEMA");
            if (!root.contains("jobs", Tag.TAG_LIST)) throw new IllegalArgumentException("MISSING_SAVED_JOB_LIST");
            ListTag list = root.getList("jobs", Tag.TAG_COMPOUND);
            if (list.size() > MAX_PLANS) throw new IllegalArgumentException("TOO_MANY_SAVED_PLANS");
            int edits = 0, guards = 0;
            for (int i = 0; i < list.size(); i++) {
                Job job = readJob(list.getCompound(i));
                edits += job.edits.size(); guards += job.guards.size();
                if (edits > MAX_TOTAL_EDITS || guards > MAX_TOTAL_GUARDS || data.jobs.put(job.id, job) != null)
                    throw new IllegalArgumentException("SAVED_PLAN_LIMIT_OR_DUPLICATE");
            }
        } catch (RuntimeException failure) {
            // Fail closed: never replace an unreadable ledger with a fresh, empty construction log.
            data.jobs.clear(); data.loadFailure = String.valueOf(failure.getMessage());
        }
        return data;
    }

    private static Job readJob(CompoundTag tag) {
        Job job = new Job();
        job.id = checkedText(tag.getString("id"), 160); job.sourceId = checkedText(tag.getString("source"), 240);
        job.version = checkedText(tag.getString("version"), 160); job.dimension = checkedText(tag.getString("dimension"), 240);
        if (ResourceLocation.tryParse(job.dimension) == null || !tag.hasUUID("owner"))
            throw new IllegalArgumentException("INVALID_OWNER_OR_DIMENSION");
        job.owner = tag.getUUID("owner");
        int[] box = tag.getIntArray("bounds");
        if (box.length != 4) throw new IllegalArgumentException("INVALID_SAVED_BOUNDS");
        job.bounds = new BoundsXZ(box[0], box[1], box[2], box[3]); requireBounds(job.bounds);
        job.created = tag.getLong("created"); job.expires = tag.getLong("expires");
        job.checksPerTick = tag.getInt("checks_per_tick"); job.editsPerTick = tag.getInt("edits_per_tick"); requireBudgets(job);
        job.state = State.valueOf(tag.getString("state")); job.phase = Phase.valueOf(tag.getString("phase"));
        job.reason = tag.getString("reason");
        if (job.reason.length() > 512) throw new IllegalArgumentException("INVALID_SAVED_REASON");
        job.writeStarted = tag.getBoolean("write_started");
        ListTag paletteTags = tag.getList("palette", Tag.TAG_COMPOUND);
        if (paletteTags.isEmpty() || paletteTags.size() > MAX_PALETTE) throw new IllegalArgumentException("INVALID_PALETTE_SIZE");
        List<BlockState> palette = new ArrayList<>(paletteTags.size());
        for (int i = 0; i < paletteTags.size(); i++) palette.add(readState(paletteTags.getCompound(i)));
        long[] positions = tag.getLongArray("edit_pos"), guards = tag.getLongArray("guard_pos");
        int[] before = tag.getIntArray("edit_before"), after = tag.getIntArray("edit_after"), states = tag.getIntArray("guard_state");
        if (positions.length == 0 || positions.length > MAX_PLAN_EDITS || guards.length == 0 || guards.length > MAX_PLAN_GUARDS
                || before.length != positions.length || after.length != positions.length || states.length != guards.length)
            throw new IllegalArgumentException("INVALID_SNAPSHOT_ARRAYS");
        List<Edit> edits = new ArrayList<>(positions.length);
        for (int i = 0; i < positions.length; i++) edits.add(new Edit(BlockPos.of(positions[i]), palette.get(before[i]), palette.get(after[i])));
        job.edits = edits.stream().sorted(EDIT_ORDER).toList();
        List<Guard> snapshot = new ArrayList<>(guards.length);
        for (int i = 0; i < guards.length; i++) snapshot.add(new Guard(BlockPos.of(guards[i]), palette.get(states[i])));
        job.guards = List.copyOf(snapshot); job.index();
        job.editCursor = tag.getInt("edit_cursor"); job.checkCursor = tag.getInt("check_cursor");
        if (job.editCursor < 0 || job.editCursor > job.edits.size() || job.checkCursor < 0 || job.checkCursor > job.guards.size())
            throw new IllegalArgumentException("INVALID_SAVED_PROGRESS");
        for (int i = 0; i < job.editCursor; i++) {
            if (i + 1 == job.edits.size() || chunk(job.edits.get(i).pos) != chunk(job.edits.get(i + 1).pos))
                job.completedChunks.add(chunk(job.edits.get(i).pos));
        }
        if (job.state == State.RUNNING) {
            job.state = State.PAUSED; job.reason = "SAVED_RUNNING_EXPLICIT_RESUME_REQUIRED";
        }
        if (!job.version.equals(RoadConstructionPlanner.VERSION)) {
            job.state = State.FAILED; job.reason = "PLAN_VERSION_MISMATCH";
        }
        return job;
    }

    /** SavedData's vanilla implementation swallows IO errors and clears dirty even on failure. */
    @Override
    public void save(File file) {
        if (!isDirty()) return;
        Path destination = file.toPath();
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        try {
            CompoundTag root = new CompoundTag();
            root.put("data", save(new CompoundTag()));
            NbtUtils.addCurrentDataVersion(root);
            Files.createDirectories(destination.getParent());
            NbtIo.writeCompressed(root, temporary.toFile());
            try (RandomAccessFile stream = new RandomAccessFile(temporary.toFile(), "rw")) {
                stream.getChannel().force(true);
            }
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            lastSaveFailure = "";
            setDirty(false);
        } catch (IOException | RuntimeException failure) {
            lastSaveFailure = failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
            ApocalypseFirstLight.LOGGER.error("Road construction ledger was not saved; construction is blocked", failure);
            // Retain both the previous committed ledger and dirty state. Do not silently discard work.
        }
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        if (!loadFailure.isEmpty()) throw new IllegalStateException("REFUSING_TO_OVERWRITE_INVALID_CONSTRUCTION_LEDGER");
        root.putInt("schema", 1);
        ListTag list = new ListTag();
        for (Job job : jobs.values()) list.add(writeJob(job));
        root.put("jobs", list);
        return root;
    }

    private static CompoundTag writeJob(Job job) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", job.id); tag.putString("source", job.sourceId); tag.putString("version", job.version);
        tag.putString("dimension", job.dimension); tag.putUUID("owner", job.owner);
        tag.putIntArray("bounds", new int[]{job.bounds.minX(), job.bounds.minZ(), job.bounds.maxXExclusive(), job.bounds.maxZExclusive()});
        tag.putLong("created", job.created); tag.putLong("expires", job.expires);
        tag.putInt("checks_per_tick", job.checksPerTick); tag.putInt("edits_per_tick", job.editsPerTick);
        tag.putString("state", job.state.name()); tag.putString("phase", job.phase.name()); tag.putString("reason", job.reason);
        tag.putBoolean("write_started", job.writeStarted); tag.putInt("edit_cursor", job.editCursor); tag.putInt("check_cursor", job.checkCursor);
        Map<BlockState, Integer> paletteIndex = new LinkedHashMap<>();
        long[] positions = new long[job.edits.size()], guards = new long[job.guards.size()];
        int[] before = new int[positions.length], after = new int[positions.length], states = new int[guards.length];
        for (int i = 0; i < positions.length; i++) {
            Edit e = job.edits.get(i); positions[i] = e.pos.asLong();
            before[i] = paletteIndex.computeIfAbsent(e.before, s -> paletteIndex.size());
            after[i] = paletteIndex.computeIfAbsent(e.after, s -> paletteIndex.size());
        }
        for (int i = 0; i < guards.length; i++) {
            Guard guard = job.guards.get(i); guards[i] = guard.pos.asLong();
            states[i] = paletteIndex.computeIfAbsent(guard.state, s -> paletteIndex.size());
        }
        if (paletteIndex.size() > MAX_PALETTE) throw new IllegalStateException("SNAPSHOT_PALETTE_BUDGET_EXCEEDED");
        ListTag palette = new ListTag();
        for (BlockState state : paletteIndex.keySet()) palette.add(NbtUtils.writeBlockState(state));
        tag.put("palette", palette); tag.putLongArray("edit_pos", positions); tag.putIntArray("edit_before", before);
        tag.putIntArray("edit_after", after); tag.putLongArray("guard_pos", guards); tag.putIntArray("guard_state", states);
        return tag;
    }

    /** Reject unknown blocks/properties; never silently deserialize a missing block as air. */
    private static BlockState readState(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Name"));
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalArgumentException("MISSING_SNAPSHOT_BLOCK");
        BlockState state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        CompoundTag properties = tag.getCompound("Properties");
        if (properties.size() != state.getProperties().size()) throw new IllegalArgumentException("BLOCK_STATE_SCHEMA_CHANGED");
        for (String name : properties.getAllKeys()) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
            if (property == null) throw new IllegalArgumentException("UNKNOWN_SNAPSHOT_PROPERTY");
            state = applyProperty(state, property, properties.getString(name));
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> property, String value) {
        return state.setValue(property, property.getValue(value)
                .orElseThrow(() -> new IllegalArgumentException("UNKNOWN_SNAPSHOT_PROPERTY_VALUE")));
    }
}
