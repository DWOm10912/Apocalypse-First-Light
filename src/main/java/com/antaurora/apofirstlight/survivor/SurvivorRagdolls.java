package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.skeleton.HumanoidPoser;
import com.antaurora.apofirstlight.skeleton.SkeletonClips;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollBody;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollDefinition;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Ragdoll V1, server side (docs/dev/ragdoll_v1_plan.md 3, 5): whether a death falls as a ragdoll, the body's start (the
 * dead survivor's last stance and motion plus the death's push), the block world it falls in, and where it ends up.
 * SurvivorCorpse runs the simulation; this class holds no per-body state except the count of bodies still falling.
 */
public final class SurvivorRagdolls {
    /** Bodies falling at once, server-wide; more deaths meanwhile lie down at once (the V0 lying pose). */
    static final int MAX_ACTIVE = 8;
    /** The start motion is capped (blocks per second): a teleport the tick before the death is not a throw. */
    private static final double MAX_START_SPEED = 30;
    /** The world is read at most this many blocks along any axis per tick. */
    private static final int MAX_SPAN = 12;
    /** Death pushes (kg m/s, a 75 kg body; section 5). */
    private static final double PUSH_HIT = 25, PUSH_SHOT = 20, PUSH_BLAST_NEAR = 140, PUSH_BLAST_FAR = 40, BLAST_RANGE = 8;
    /** The body's mass for a vehicle's throw (kg). */
    static final double BODY_MASS = 75;

    private static final Set<UUID> ACTIVE = new HashSet<>();
    private static HumanoidPoser poser;

    private SurvivorRagdolls() {
    }

    /**
     * Whether this death falls as a ragdoll. Not: below the world (void), riding (a vehicle's own shape is not part of the
     * block world yet), in water or lava (no buoyancy in V1), stuck in a block, or with MAX_ACTIVE bodies falling already.
     */
    static boolean canFall(ServerPlayer player) {
        return player.getY() >= player.serverLevel().getMinBuildHeight() && !player.isPassenger() && !player.isInWater()
                && !player.isInLava() && !player.isInWall() && ACTIVE.size() < MAX_ACTIVE;
    }

    /** The falling body of a death: the survivor's last stance at its feet, its motion, the death's push. */
    static RagdollBody start(ServerPlayer player, @Nullable DamageSource source) {
        RagdollBody body = stance(player);
        if (source != null) push(body, source, com.antaurora.apofirstlight.vehicle.VehicleImpact.thrown(player));
        return body;
    }

    /** A body standing as the player stands now, at its feet, moving as it moved the last tick (no push). */
    static RagdollBody stance(ServerPlayer player) {
        SkeletonPose pose = new SkeletonPose(SkeletonRig.get());
        HumanoidPoser.Input in = new HumanoidPoser.Input();
        in.crouch = player.isCrouching() ? 1F : 0F;
        in.viewYaw = Mth.wrapDegrees(player.yHeadRot - player.yBodyRot);
        in.viewPitch = player.getXRot();
        in.chestPitch = HumanoidPoser.chestPitch(in.viewPitch, false);
        poser().solve(in, pose);
        // the motion of the last tick (a server player's delta movement is not kept up to date: the client moves it)
        Vec3 moved = player.position().subtract(player.xo, player.yo, player.zo).scale(20);
        if (moved.length() > MAX_START_SPEED) moved = moved.normalize().scale(MAX_START_SPEED);
        RagdollBody body = new RagdollBody(RagdollDefinition.get());
        body.start(pose, vec(player.position()), player.yBodyRot, vec(moved));
        return body;
    }

    /**
     * Section 5: explosions push the whole body away from the blast, shots the box they hit, blows the chest; a vehicle
     * (vehicle/VehicleImpact) throws it as it throws the living, a 75 kg body at the throw, the hips and the chest.
     */
    static void push(RagdollBody body, DamageSource source, @Nullable Vec3 thrown) {
        RagdollDefinition def = body.def;
        Vector3d chest = new Vector3d(body.position(def.chest));
        if (thrown != null) {
            Vector3d j = vec(thrown).mul(BODY_MASS * 0.5);
            body.impulse(def.pelvis, body.position(def.pelvis), new Vector3d(j));
            body.impulse(def.chest, chest, j);
            return;
        }
        Vec3 from = source.getSourcePosition();
        if (source.is(DamageTypeTags.IS_EXPLOSION) && from != null) {
            Vector3d dir = new Vector3d(chest).sub(from.x, from.y, from.z);
            double distance = dir.length();
            dir = distance < 1e-3 ? new Vector3d(0, 1, 0) : dir.div(distance);
            dir.y = Math.max(dir.y, 0) + 0.35;
            dir.normalize();
            double j = Mth.lerp(Mth.clamp(distance / BLAST_RANGE, 0, 1), PUSH_BLAST_NEAR, PUSH_BLAST_FAR);
            body.impulse(def.chest, chest, new Vector3d(dir).mul(j * 0.6));
            body.impulse(def.pelvis, body.position(def.pelvis), new Vector3d(dir).mul(j * 0.4));
            return;
        }
        Entity direct = source.getDirectEntity();
        if (source.is(DamageTypeTags.IS_PROJECTILE) && direct != null) {
            // the projectile sits where it hit: the box nearest it takes the push, along its flight
            Vec3 motion = direct.getDeltaMovement();
            Vector3d dir = motion.lengthSqr() > 1e-4 ? vec(motion).normalize() : away(chest, from);
            if (dir == null) return;
            Vector3d at = vec(direct.position());
            int hit = body.nearest(at);
            Vector3d centre = body.position(hit);
            Vector3d offset = new Vector3d(at).sub(centre);
            if (offset.length() > 0.15) offset.normalize(0.15);
            body.impulse(hit, offset.add(centre), dir.mul(PUSH_SHOT));
            return;
        }
        Vector3d dir = away(chest, from != null ? from : source.getEntity() != null ? source.getEntity().position() : null);
        if (dir == null) return;
        dir.y = 0;
        if (dir.lengthSquared() < 1e-6) return;
        dir.normalize();
        dir.y = 0.2;
        // a blow at the upper chest: the body tips over its feet
        Vector3d at = new Vector3d(chest).add(0, 0.15, 0);
        body.impulse(def.chest, at, dir.normalize().mul(PUSH_HIT));
    }

    @Nullable
    private static Vector3d away(Vector3d target, @Nullable Vec3 from) {
        if (from == null) return null;
        Vector3d d = new Vector3d(target).sub(from.x, from.y, from.z);
        return d.lengthSquared() < 1e-6 ? null : d.normalize();
    }

    /**
     * The block world a body falls in: every block's collision shape (Terrain Smooth slopes included). A chunk that is
     * not loaded counts as solid, so a body never falls through the edge of the loaded world.
     */
    static RagdollWorld world(ServerLevel level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return (minX, minY, minZ, maxX, maxY, maxZ, out) -> {
            int x0 = Mth.floor(minX), y0 = Mth.floor(minY), z0 = Mth.floor(minZ);
            int x1 = Math.min(Mth.floor(maxX), x0 + MAX_SPAN), y1 = Math.min(Mth.floor(maxY), y0 + MAX_SPAN),
                    z1 = Math.min(Mth.floor(maxZ), z0 + MAX_SPAN);
            y0 = Math.max(y0, level.getMinBuildHeight());
            y1 = Math.min(y1, level.getMaxBuildHeight() - 1);
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) {
                    if (y0 <= y1) out.add(new double[]{x, y0, z, x + 1, y1 + 1, z + 1});
                    continue;
                }
                for (int y = y0; y <= y1; y++) {
                    pos.set(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.isAir()) continue;
                    VoxelShape shape = state.getCollisionShape(level, pos);
                    if (shape.isEmpty()) continue;
                    for (AABB b : shape.toAabbs()) out.add(new double[]{x + b.minX, y + b.minY, z + b.minZ, x + b.maxX, y + b.maxY, z + b.maxZ});
                }
            }
        };
    }

    // ---------------------------------------------------------------- stand-ins (/afl survivor ragdoll)

    /** Owner of the stand-in bodies: no account, no lineage; {@code /afl survivor ragdoll clear} finds them by it. */
    static final UUID STAND_IN_OWNER = new UUID(0x41464C5241474430L, 0x5354414E44494EL);
    /** Blocks in front of the viewer; metres above the ground for a drop. */
    private static final double STAND_IN_DISTANCE = 2.5, STAND_IN_DROP = 6.0;

    /** How a stand-in falls: the same pushes as a real death (section 5), from the viewer. */
    public enum StandIn {
        /** A blow from the viewer (a melee death). */
        HIT,
        /** An arrow from the viewer into the chest. */
        SHOT,
        /** An explosion where the viewer stands. */
        BLAST,
        /** Let go STAND_IN_DROP m above the ground, no push. */
        DROP,
        /** Just collapses where it stands, knees bent. */
        COLLAPSE
    }

    /**
     * Testing (op, {@code /afl survivor ragdoll}): a body standing {@link #STAND_IN_DISTANCE} blocks in front of the viewer,
     * facing it, dressed as the viewer's current survivor, falling the way a real death of that kind would. It is an
     * ordinary corpse record (searchable, empty, kept through saves) of no lineage, so another player's corpse can be
     * watched from outside in single player. Null when MAX_ACTIVE bodies are falling already.
     */
    @Nullable
    static SurvivorCorpse standIn(ServerPlayer viewer, StandIn kind) {
        SurvivorLineage lineage = SurvivorWorldData.get(viewer.server).lineage(viewer.getUUID());
        return standIn(viewer.serverLevel(), viewer, kind, lineage != null ? lineage.current() : null);
    }

    /** The same for any player (GameTests use a mock one), dressed as {@code dressed} when given. */
    @Nullable
    public static SurvivorCorpse standIn(ServerLevel level, net.minecraft.world.entity.player.Player viewer, StandIn kind,
                                         @Nullable SurvivorRecord dressed) {
        if (ACTIVE.size() >= MAX_ACTIVE) return null;
        Vec3 look = viewer.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-6 ? Vec3.directionFromRotation(0, viewer.getYRot()) : flat.normalize();
        Vec3 at = SurvivorCorpses.ground(level, viewer.position().add(flat.scale(STAND_IN_DISTANCE)).add(0, 1.5, 0));
        if (kind == StandIn.DROP) at = at.add(0, STAND_IN_DROP, 0);
        // facing the viewer: Minecraft's forward (-sin yaw, cos yaw) is -flat
        float yaw = (float) Math.toDegrees(Math.atan2(flat.x, -flat.z));
        SurvivorWorldData data = SurvivorWorldData.get(level.getServer());
        SurvivorCorpseRecord record = new SurvivorCorpseRecord(UUID.randomUUID(), STAND_IN_OWNER, UUID.randomUUID(),
                "Stand-in", 0, level.dimension().location().toString(), at.x, at.y, at.z, yaw, level.getGameTime(),
                level.getDayTime());
        if (dressed != null) {
            record.skin = dressed.skin;
            record.arms = dressed.arms;
        }
        data.addCorpse(record);
        SurvivorCorpse entity = SurvivorCorpses.spawnForTest(level, record);
        if (entity == null) {
            data.corpses.remove(record.id);
            data.setDirty();
            return null;
        }
        SkeletonPose pose = new SkeletonPose(SkeletonRig.get());
        HumanoidPoser.Input in = new HumanoidPoser.Input();
        if (kind == StandIn.COLLAPSE) in.crouch = 1F;
        poser().solve(in, pose);
        RagdollBody body = new RagdollBody(RagdollDefinition.get());
        body.start(pose, vec(at), yaw, new Vector3d());
        DamageSource source = switch (kind) {
            case HIT -> level.damageSources().playerAttack(viewer);
            case BLAST -> level.damageSources().explosion(viewer, viewer);
            case SHOT -> {
                // an arrow at the chest, flying away from the viewer (never added to the world)
                net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(level, viewer);
                Vector3d chest = body.position(body.def.chest);
                arrow.setPos(chest.x - flat.x * 0.12, chest.y + 0.05, chest.z - flat.z * 0.12);
                arrow.setDeltaMovement(flat.scale(3.0));
                yield level.damageSources().arrow(arrow, viewer);
            }
            case DROP, COLLAPSE -> null;
        };
        if (source != null) push(body, source, null);
        entity.fall(body);
        return entity;
    }

    /** Removes every stand-in body (records and entities); returns how many. */
    public static int clearStandIns(net.minecraft.server.MinecraftServer server) {
        SurvivorWorldData data = SurvivorWorldData.get(server);
        java.util.List<SurvivorCorpseRecord> gone = data.corpses.values().stream()
                .filter(c -> STAND_IN_OWNER.equals(c.playerId)).toList();
        for (SurvivorCorpseRecord corpse : gone) {
            // the record first: the entity's removal then has no record to write into
            data.corpses.remove(corpse.id);
            ServerLevel level = SurvivorCorpses.level(server, corpse.dimension);
            if (level != null && level.getEntity(corpse.id) instanceof SurvivorCorpse entity) entity.discard();
            stopped(corpse.id);
        }
        if (!gone.isEmpty()) {
            data.setDirty();
            Survivors.markFlush();
        }
        return gone.size();
    }

    // ---------------------------------------------------------------- GameTests (src/dev RagdollCorpseGameTests)

    /**
     * GameTests: a corpse with no items of a nameless survivor standing at {@code at} facing {@code yaw}, falling with a
     * push (kg m/s) at its upper chest. Returns the entity.
     */
    @Nullable
    public static SurvivorCorpse testFall(ServerLevel level, Vec3 at, float yaw, Vec3 push) {
        SurvivorCorpseRecord record = new SurvivorCorpseRecord(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Test Body", 1, level.dimension().location().toString(), at.x, at.y, at.z, yaw, level.getGameTime(),
                level.getDayTime());
        SurvivorWorldData.get(level.getServer()).addCorpse(record);
        SurvivorCorpse entity = SurvivorCorpses.spawnForTest(level, record);
        if (entity == null) return null;
        SkeletonPose pose = new SkeletonPose(SkeletonRig.get());
        poser().solve(new HumanoidPoser.Input(), pose);
        RagdollBody body = new RagdollBody(RagdollDefinition.get());
        body.start(pose, vec(at), yaw, new Vector3d());
        Vector3d chest = body.position(body.def.chest);
        body.impulse(body.def.chest, new Vector3d(chest).add(0, 0.15, 0), vec(push));
        entity.fall(body);
        return entity;
    }

    /** GameTests: the corpse's record written and read back: {x, y, z, then its pose}, or null without a record. */
    @Nullable
    public static double[] testSavedRecord(net.minecraft.server.MinecraftServer server, UUID corpse) {
        SurvivorCorpseRecord record = SurvivorWorldData.get(server).corpse(corpse);
        if (record == null) return null;
        SurvivorCorpseRecord back = SurvivorCorpseRecord.load(record.save());
        if (back == null) return null;
        float[] pose = back.pose == null ? new float[0] : back.pose;
        double[] out = new double[3 + pose.length];
        out[0] = back.x;
        out[1] = back.y;
        out[2] = back.z;
        for (int i = 0; i < pose.length; i++) out[3 + i] = pose[i];
        return out;
    }

    /** GameTests: whether the corpse's record, written and read back, still waits to be put on the ground. */
    public static boolean testUnsettled(net.minecraft.server.MinecraftServer server, UUID corpse) {
        SurvivorCorpseRecord record = SurvivorWorldData.get(server).corpse(corpse);
        SurvivorCorpseRecord back = record == null ? null : SurvivorCorpseRecord.load(record.save());
        return back != null && back.unsettled;
    }

    /** GameTests: removes a test corpse's record and entity. */
    public static void testForget(ServerLevel level, UUID corpse) {
        SurvivorWorldData data = SurvivorWorldData.get(level.getServer());
        data.corpses.remove(corpse);
        data.setDirty();
        if (level.getEntity(corpse) instanceof SurvivorCorpse entity) entity.discard();
        stopped(corpse);
    }

    /** Where a body that came to rest lies: under its pelvis, at its lowest point. */
    static Vec3 restingPoint(RagdollBody body) {
        Vector3d pelvis = body.position(body.def.pelvis);
        return new Vec3(pelvis.x, body.lowest(), pelvis.z);
    }

    /**
     * The server is stopping (before the world is saved): every falling body stops where it is, so its record is saved
     * with it. Without this, the levels close after the save and the bodies' last places are lost.
     */
    static void stopAll(net.minecraft.server.MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        for (UUID id : java.util.List.copyOf(ACTIVE)) {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(id) instanceof SurvivorCorpse corpse) {
                    corpse.stopWhereItIs();
                    break;
                }
            }
        }
        ACTIVE.clear();
    }

    static void started(UUID corpse) {
        ACTIVE.add(corpse);
    }

    static void stopped(UUID corpse) {
        ACTIVE.remove(corpse);
    }

    /** For the status command: bodies falling right now. */
    static int active() {
        return ACTIVE.size();
    }

    // the server cost of falling bodies, per server tick (all bodies of a tick summed), since the start or the last reset
    private static long costTick = Long.MIN_VALUE, costNow, costTotal, costMax;
    private static int costTicks;

    /** SurvivorCorpse#tick: one body's step took {@code nanos} in server tick {@code gameTime}. */
    static void measured(long gameTime, long nanos) {
        if (gameTime != costTick) {
            closeCostTick();
            costTick = gameTime;
        }
        costNow += nanos;
    }

    private static void closeCostTick() {
        if (costNow == 0) return;
        costTotal += costNow;
        costMax = Math.max(costMax, costNow);
        costTicks++;
        costNow = 0;
    }

    /** For the status command and tests: {ticks with falling bodies, mean ms per such tick, worst ms}. */
    static double[] cost() {
        long total = costTotal + costNow, max = Math.max(costMax, costNow);
        int ticks = costTicks + (costNow > 0 ? 1 : 0);
        return new double[]{ticks, ticks == 0 ? 0 : total / 1e6 / ticks, max / 1e6};
    }

    /** GameTests: starts counting again. */
    public static void testResetCost() {
        costTick = Long.MIN_VALUE;
        costNow = costTotal = costMax = 0;
        costTicks = 0;
    }

    /** GameTests: {@link #cost()}. */
    public static double[] testCost() {
        return cost();
    }

    private static HumanoidPoser poser() {
        if (poser == null) poser = new HumanoidPoser(SkeletonRig.get(), SkeletonClips.locomotion());
        return poser;
    }

    private static Vector3d vec(Vec3 v) {
        return new Vector3d(v.x, v.y, v.z);
    }
}
