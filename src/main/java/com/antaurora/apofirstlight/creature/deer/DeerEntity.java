package com.antaurora.apofirstlight.creature.deer;

import com.antaurora.apofirstlight.creature.infected.InfectedEntity;
import com.antaurora.apofirstlight.noise.NoiseType;
import com.antaurora.apofirstlight.weather.AflWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * White-tailed deer D1 + the first of D2 (docs/gameplay/afl_whitetail_deer_d0_design.md 3, 11): AFL's first animal. A buck
 * or a doe (the doe 0.92 of the buck); its look is the D0 concept's skinned mesh (client/creature/DeerRenderer).
 *
 * <p>Its own small brain instead of vanilla goals (only {@link FloatGoal}), run every tick in {@link #customServerAiStep}:
 * <ul>
 *   <li><b>Calm:</b> grazes 15–45 s (head down and up by {@link DeerSchedule}), stands looking round 3–10 s, walks 6–16
 *   blocks to a new spot (to the herd's leader when it is more than 10 blocks off); the doe bleats now and then, the buck
 *   grunts.</li>
 *   <li><b>Alert</b> (it saw, heard or half-smelled something): stops, faces it, ears forward; may stamp a forefoot, may
 *   duck its head as if grazing and snap it up again; after 2–15 s with nothing more it goes back to grazing.</li>
 *   <li><b>Flee</b> (too close, coming closer, smelled, a shot, hurt): snorts, flags its tail, bounds away at 9 m/s for
 *   3–6 s and trots on for 5–10 s more, then stops and looks back; the deer within 32 blocks run with it.</li>
 * </ul>
 * Senses, every 4 ticks: <b>sight</b> by how the player moves (sprinting 100 blocks, walking 60, sneaking 15, standing 8;
 * half at night and with the head down; blind 60° behind; line of sight); <b>scent</b> on the local wind (AflWeather): a
 * player up to 60 blocks upwind within 30° is smelled and it runs; <b>hearing</b> through noise/NoiseSystem
 * ({@link #heard}). An infected within 30 blocks in sight, or 12 anyway, makes it run.
 *
 * <p>No loot or experience yet (D3 butchers it), no vanilla step or ambient sounds (DeerSounds), never despawns. On death it
 * is replaced at once by its carcass (DeerCorpse), which plays the fall and stays. The world places deer by DeerPopulation
 * (D2a), which counts them per 512 m cell: a deer remembers its home cell, and its death takes it off that count.
 */
public class DeerEntity extends PathfinderMob {
    public static final byte STAND = 0, GRAZE = 1, ALERT = 2, FLEE = 3;
    public static final byte EVENT_STOMP = 90;
    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(DeerEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(DeerEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> STATE_AT = SynchedEntityData.defineId(DeerEntity.class, EntityDataSerializers.INT);
    /** Speed modifiers on a 0.25 base: ground speed is about 2.16 s^2 blocks a tick (InfectedEntity's note), so walking
     *  1.3 m/s, trotting 3.5 m/s, bounding 9 m/s (13 m/s, a deer's top speed, was much too fast in game: user 2026-10-28,
     *  "现在鹿跑的飞快"). */
    static final double WALK = 0.69, TROT = 1.14, RUN = 1.82;
    /** A step or two while grazing, head down: about 0.45 m/s. */
    static final double GRAZE_STEP = 0.41;
    /** Ground covered per gait cycle (deer_export.py, the buck; the doe's x 0.92), blocks: two hoof sounds a walk or trot
     *  cycle, one landing a bound. */
    static final double WALK_CYCLE = 0.922, TROT_CYCLE = 1.862, BOUND_CYCLE = 5.2;
    /** {@code /afl dev deer calm true}: the deer notice nothing (to watch them up close); not saved. */
    static boolean devCalm;

    private enum Mode { CALM, ALERT, FLEE }

    private enum Calm { GRAZE, STAND, WALK }

    // server
    private Mode mode = Mode.CALM;
    private Calm calm = Calm.GRAZE;
    private long until, stompAt = -1, stompSoundAt = -1, fakeGrazeAt = -1, fakeGrazeEnd = -1, sprintUntil, nextPath, nextVoice, nextLook,
            pendingFleeAt = -1, nextGrazeStep, fleeGo;
    private boolean lookBack;
    @Nullable private Vec3 threatPos, pendingFleePos;
    @Nullable private Entity threat;
    private double alertDistance;
    private int seenTicks;
    @Nullable private UUID leaderId;
    /** The 512 m cell whose count it is in (DeerPopulation), or NO_CELL (placed by a command). */
    private long homeCell = DeerPopulation.NO_CELL;
    @Nullable private Entity leader;
    private final Map<Integer, Vec3> playerLast = new HashMap<>();
    private double stepDistance, boundDistance;
    private long brushAt;
    // client
    private float walkDistance, walkDistanceO, walkSpeed, walkSpeedO;
    private int stompStart = Integer.MIN_VALUE / 2;

    public DeerEntity(EntityType<? extends DeerEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.moveControl = new Move(this);
        setMaxUpStep(1.0F);
    }

    /**
     * Vanilla MoveControl turns a mob up to 90° a tick toward its next path point, so the deer swung round like a turret
     * (user 2026-10-28: "这鹿的AI怎么有点呆呆的"). It turns at most 7° a tick walking, 12 trotting, 16 bounding, and slows
     * down into a sharp turn: it walks arcs, and a turn on the spot is a few slow steps round.
     */
    private static final class Move extends MoveControl {
        Move(DeerEntity deer) {
            super(deer);
        }

        @Override
        public void tick() {
            float before = mob.getYRot();
            boolean moving = operation == Operation.MOVE_TO;
            super.tick();
            if (!moving) return;
            float wanted = Mth.wrapDegrees(mob.getYRot() - before);
            float max = speedModifier >= RUN * 0.9 ? 16F : speedModifier >= TROT * 0.9 ? 12F : 7F;
            if (Math.abs(wanted) <= max) return;
            mob.setYRot(before + Math.copySign(max, wanted));
            // still far off its heading: slow down rather than circle the point
            float left = Math.abs(wanted) - max;
            mob.setSpeed(mob.getSpeed() * Mth.clamp(1F - left / 100F, 0.3F, 1F));
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(KIND, (byte) 0);
        entityData.define(STATE, GRAZE);
        entityData.define(STATE_AT, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    // ---------------------------------------------------------------- kind and size

    public boolean doe() {
        return entityData.get(KIND) == 1;
    }

    public void setDoe(boolean doe) {
        entityData.set(KIND, (byte) (doe ? 1 : 0));
        refreshDimensions();
    }

    /** The doe is drawn and sized at 0.92 of the buck. */
    public float kindScale() {
        return doe() ? 0.92F : 1.0F;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(kindScale());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (KIND.equals(key)) refreshDimensions();
    }

    public byte state() {
        return entityData.get(STATE);
    }

    /** Game time (as an int) the current state began: DeerSchedule's grazing beat. */
    public int stateAt() {
        return entityData.get(STATE_AT);
    }

    private void setState(byte s) {
        if (entityData.get(STATE) == s) return;
        entityData.set(STATE, s);
        entityData.set(STATE_AT, (int) level().getGameTime());
    }

    /** DeerPopulation placed it for this cell: its death takes one off the cell's count. */
    void setHomeCell(long key) {
        homeCell = key;
    }

    public void setLeader(@Nullable DeerEntity lead) {
        leader = lead;
        leaderId = lead == null ? null : lead.getUUID();
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data, tag);
        enterCalm(Calm.GRAZE, level.getLevel().getGameTime());
        return out;
    }

    // ---------------------------------------------------------------- the brain

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        long now = level().getGameTime();
        if (pendingFleeAt >= 0 && now >= pendingFleeAt) {
            pendingFleeAt = -1;
            if (pendingFleePos != null) startFlee(pendingFleePos, null, now, false);
        }
        if ((tickCount + getId()) % 4 == 0) perceive(now);
        switch (mode) {
            case CALM -> calm(now);
            case ALERT -> alert(now);
            case FLEE -> flee(now);
        }
        if (stompSoundAt >= 0 && now >= stompSoundAt) {
            stompSoundAt = -1;
            DeerSounds.stomp(this);
        }
        steps(now);
    }

    private void perceive(long now) {
        if (devCalm || !(level() instanceof ServerLevel level)) return;
        for (Player p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            double d = distanceTo(p);
            Vec3 last = playerLast.put(p.getId(), p.position());
            if (d > 110) continue;
            double moved = last == null ? 0 : Math.hypot(p.getX() - last.x, p.getZ() - last.z) / 4.0;
            if (smells(p, d)) {
                startFlee(p.position(), p, now, true);
                return;
            }
            double sight = p.isSprinting() ? 100 : moved > 0.03 ? (p.isCrouching() ? 15 : 60) : 8;
            if (level.isNight()) sight *= 0.5;
            if (headDown(now)) sight *= 0.5;
            if (d < sight && sees(p)) {
                if (d < Math.max(10, sight * 0.35)) {
                    startFlee(p.position(), p, now, true);
                    return;
                }
                noticed(p.position(), p, d, now);
            }
        }
        for (InfectedEntity i : level.getEntitiesOfClass(InfectedEntity.class, getBoundingBox().inflate(30))) {
            double d = distanceTo(i);
            if (d < 12 || (d < 30 && sees(i))) {
                startFlee(i.position(), i, now, true);
                return;
            }
        }
    }

    private boolean headDown(long now) {
        return state() == GRAZE && DeerSchedule.headDown(getId(), stateAt(), now, new int[1]);
    }

    /** In front or to the side (not the 60° behind) and nothing solid between. */
    private boolean sees(Entity e) {
        Vec3 to = e.position().subtract(position());
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
        if (Math.abs(Mth.wrapDegrees(yaw - yHeadRot)) > 150.0F) return false;
        return hasLineOfSight(e);
    }

    /** Upwind within 30°, 60 blocks; with no wind to speak of only 15. */
    private boolean smells(Player p, double d) {
        if (d > 60) return false;
        float[] w = AflWeather.at(level(), getX(), getZ());
        double wx = w == null ? 0 : w[AflWeather.WIND_X], wz = w == null ? 0 : w[AflWeather.WIND_Z];
        double wind = Math.hypot(wx, wz);
        if (wind < 0.5) return d < 15;
        double fx = getX() - p.getX(), fz = getZ() - p.getZ(), f = Math.hypot(fx, fz);
        return f > 1e-3 && (fx * wx + fz * wz) / (f * wind) > 0.866;
    }

    /** NoiseSystem: a shot or a blast in the near half of its range sends it running; anything else makes it look. */
    void heard(NoiseType type, Vec3 at, double fraction, @Nullable Entity source) {
        if (devCalm) return;
        long now = level().getGameTime();
        if ((type == NoiseType.GUNSHOT || type == NoiseType.EXPLOSION) && fraction < 0.5) {
            startFlee(at, source, now, true);
        } else if (type != NoiseType.MACHINE || fraction < 0.3) {
            noticed(at, source, distanceToSqr(at) > 0 ? Math.sqrt(distanceToSqr(at)) : 0, now);
        }
    }

    private void noticed(Vec3 at, @Nullable Entity source, double d, long now) {
        if (mode == Mode.FLEE) return;
        if (mode == Mode.CALM) {
            enterAlert(at, source, d, now, false);
            return;
        }
        // already alert: coming closer, or still in sight after 4 s, and it runs
        threatPos = at;
        if (source != null) threat = source;
        seenTicks += 4;
        if (d < alertDistance - 3 || seenTicks > 80) startFlee(at, source, now, true);
        else until = Math.max(until, now + 40);
    }

    private void enterAlert(Vec3 at, @Nullable Entity source, double d, long now, boolean back) {
        mode = Mode.ALERT;
        threatPos = at;
        threat = source;
        alertDistance = d;
        seenTicks = 0;
        lookBack = back;
        getNavigation().stop();
        setState(ALERT);
        until = now + (back ? 100 + random.nextInt(100) : 40 + random.nextInt(260));
        stompAt = !back && random.nextFloat() < 0.5F ? now + 20 + random.nextInt(30) : -1;
        fakeGrazeAt = !back && random.nextFloat() < 0.3F ? now + 60 + random.nextInt(80) : -1;
        fakeGrazeEnd = -1;
    }

    private void alert(long now) {
        Vec3 at = threat != null && threat.isAlive() && !lookBack ? threat.getEyePosition() : threatPos;
        if (at != null) getLookControl().setLookAt(at.x, at.y, at.z, 30.0F, 30.0F);
        if (stompAt >= 0 && now >= stompAt) {
            stompAt = -1;
            level().broadcastEntityEvent(this, EVENT_STOMP);
            stompSoundAt = now + 6;   // the hoof comes down 0.3 s in (clips.json stomp_hit)
        }
        if (fakeGrazeAt >= 0 && now >= fakeGrazeAt) {
            // ducks its head as if grazing, then snaps it up to catch whatever moves
            fakeGrazeAt = -1;
            fakeGrazeEnd = now + 30;
            setState(GRAZE);
        }
        if (fakeGrazeEnd >= 0 && now >= fakeGrazeEnd) {
            fakeGrazeEnd = -1;
            setState(ALERT);
        }
        if (now >= until) enterCalm(Calm.GRAZE, now);
    }

    private void startFlee(Vec3 from, @Nullable Entity source, long now, boolean spread) {
        threatPos = from;
        if (source != null) threat = source;
        if (mode == Mode.FLEE) return;
        mode = Mode.FLEE;
        // a startle first: it freezes, head up, for 0.3–0.7 s, then wheels and goes
        fleeGo = now + 6 + random.nextInt(8);
        getNavigation().stop();
        sprintUntil = fleeGo + 60 + random.nextInt(60);
        until = sprintUntil + 100 + random.nextInt(100);
        nextPath = 0;
        setState(FLEE);
        if (spread || random.nextFloat() < 0.4F) DeerSounds.snort(this);
        if (spread && level() instanceof ServerLevel level) {
            for (DeerEntity d : level.getEntitiesOfClass(DeerEntity.class, getBoundingBox().inflate(32))) {
                if (d != this && d.mode != Mode.FLEE && d.pendingFleeAt < 0) {
                    d.pendingFleeAt = now + 3 + d.random.nextInt(8);
                    d.pendingFleePos = from;
                }
            }
        }
    }

    private void flee(long now) {
        if (now < fleeGo) {
            if (threatPos != null) getLookControl().setLookAt(threatPos.x, threatPos.y + 1.0, threatPos.z, 40F, 40F);
            return;
        }
        double speed = now < sprintUntil ? RUN : TROT;
        if (now >= nextPath || (getNavigation().isDone() && now >= nextPath - 30)) {
            Vec3 to = threatPos == null ? null : DefaultRandomPos.getPosAway(this, 32, 7, threatPos);
            if (to == null) to = LandRandomPos.getPos(this, 24, 7);
            if (to != null) getNavigation().moveTo(to.x, to.y, to.z, speed);
            nextPath = now + 40;
        } else {
            getNavigation().setSpeedModifier(speed);
        }
        if (now >= until) {
            // far enough: stops and looks back where it came from
            Vec3 at = threatPos;
            enterAlert(at != null ? at : position(), null, 1000, now, true);
        }
    }

    private void enterCalm(Calm c, long now) {
        mode = Mode.CALM;
        calm = c;
        switch (c) {
            case GRAZE -> {
                getNavigation().stop();
                setState(GRAZE);
                until = now + 300 + random.nextInt(600);
                nextGrazeStep = now + 60 + random.nextInt(140);
            }
            case STAND -> {
                getNavigation().stop();
                setState(STAND);
                until = now + 60 + random.nextInt(140);
                nextLook = now + 10;
                // now and then it turns where it stands: a couple of slow steps round to a new heading
                if (random.nextFloat() < 0.4F) stepToward(yBodyRot + (random.nextBoolean() ? 1 : -1) * (60 + random.nextInt(80)), 1.2, WALK * 0.8);
            }
            case WALK -> {
                setState(STAND);
                Vec3 to = null;
                Entity lead = leader();
                if (lead != null && distanceTo(lead) > 10) to = lead.position().add(random.nextGaussian() * 3, 0, random.nextGaussian() * 3);
                if (to == null) to = LandRandomPos.getPos(this, 16, 7);
                if (to != null) getNavigation().moveTo(to.x, to.y, to.z, WALK);
                until = now + 400;
            }
        }
        if (nextVoice == 0) nextVoice = now + 200 + random.nextInt(600);
    }

    private void calm(long now) {
        switch (calm) {
            case GRAZE -> {
                if (DeerSchedule.biteNow(getId(), stateAt(), now)) DeerSounds.graze(this);
                // a deer grazes on the move: a step or two forward every few seconds, head still down
                if (now >= nextGrazeStep) {
                    nextGrazeStep = now + 80 + random.nextInt(160);
                    stepToward(yBodyRot + (random.nextFloat() - 0.5F) * 80F, 0.8 + random.nextDouble() * 0.9, GRAZE_STEP);
                }
                if (now >= until) enterCalm(Calm.STAND, now);
            }
            case STAND -> {
                if (now >= nextLook) {
                    nextLook = now + 40 + random.nextInt(60);
                    getLookControl().setLookAt(getX() + random.nextGaussian() * 8, getEyeY() + random.nextGaussian(), getZ() + random.nextGaussian() * 8);
                }
                if (now >= until) enterCalm(Calm.WALK, now);
            }
            case WALK -> {
                if (getNavigation().isDone() || now >= until) enterCalm(Calm.GRAZE, now);
            }
        }
        // the herd keeps together: a follower too far from its leader walks back to her
        if (calm != Calm.WALK && (tickCount + getId()) % 40 == 0) {
            Entity lead = leader();
            if (lead != null && distanceTo(lead) > 10) enterCalm(Calm.WALK, now);
        }
        if (now >= nextVoice) {
            nextVoice = now + (doe() ? 600 + random.nextInt(1200) : 800 + random.nextInt(1600));
            if (doe()) DeerSounds.bleat(this);
            else DeerSounds.grunt(this);
        }
    }

    /** A short walk along {@code yaw} (degrees, as yBodyRot), {@code distance} blocks, if the ground there is walkable. */
    private void stepToward(float yaw, double distance, double speed) {
        double r = yaw * Mth.DEG_TO_RAD;
        double x = getX() - Math.sin(r) * distance, z = getZ() + Math.cos(r) * distance;
        getNavigation().moveTo(x, getY(), z, speed);
    }

    @Nullable
    private Entity leader() {
        if (leaderId == null) return null;
        if ((leader == null || leader.isRemoved()) && level() instanceof ServerLevel level) leader = level.getEntity(leaderId);
        return leader != null && leader.isAlive() ? leader : null;
    }

    /** Hooves: two steps a walk or trot cycle, one landing a bound (and brush when it bounds through plants). */
    private void steps(long now) {
        double moved = Math.hypot(getX() - xo, getZ() - zo);
        if (moved > 1.0 || !onGround()) return;
        float s = kindScale();
        if (moved > 0.3) {
            boundDistance += moved;
            if (boundDistance >= BOUND_CYCLE * s) {
                boundDistance = 0;
                DeerSounds.bound(this);
            }
            BlockState feet = level().getBlockState(blockPosition());
            if (now >= brushAt && !feet.isAir() && feet.getFluidState().isEmpty() && feet.getCollisionShape(level(), blockPosition()).isEmpty()) {
                brushAt = now + 16;
                DeerSounds.brush(this);
            }
        } else if (moved > 0.01) {
            boolean trot = moved > 0.12;
            stepDistance += moved;
            if (stepDistance >= (trot ? TROT_CYCLE : WALK_CYCLE) * s / 2) {
                stepDistance = 0;
                DeerSounds.step(this, trot ? 12 : 8);
            }
        }
    }

    // ---------------------------------------------------------------- damage and death

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && isAlive()) {
            Entity by = source.getEntity();
            Vec3 from = by != null ? by.position() : source.getSourcePosition() != null ? source.getSourcePosition()
                    : position().add(random.nextGaussian(), 0, random.nextGaussian());
            startFlee(from, by, level().getGameTime(), true);
        }
        return hit;
    }

    @Override
    protected void playHurtSound(DamageSource source) {
        if (level() instanceof ServerLevel) DeerSounds.hurt(this);
    }

    @Override
    @Nullable
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    @Override
    public void playAmbientSound() {
    }

    @Override
    protected int calculateFallDamage(float distance, float multiplier) {
        return super.calculateFallDamage(distance - 2.0F, multiplier);   // it takes a 4-block drop in its stride
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!dead || level().isClientSide || isRemoved()) return;
        DeerCorpse.spawn((ServerLevel) level(), this);
        remove(RemovalReason.KILLED);
    }

    @Override
    protected void tickDeath() {
        if (!level().isClientSide && !isRemoved()) remove(RemovalReason.KILLED);
    }

    /** Killed or removed for good (not unloaded with its chunk): one fewer in its cell. */
    @Override
    public void remove(RemovalReason reason) {
        if (!isRemoved() && homeCell != DeerPopulation.NO_CELL && reason.shouldDestroy() && level() instanceof ServerLevel level) {
            DeerPopulation.get(level).died(homeCell);
            homeCell = DeerPopulation.NO_CELL;
        }
        super.remove(reason);
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    // ---------------------------------------------------------------- save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Doe", doe());
        if (leaderId != null) tag.putUUID("Leader", leaderId);
        if (homeCell != DeerPopulation.NO_CELL) tag.putLong("HomeCell", homeCell);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setDoe(tag.getBoolean("Doe"));
        leaderId = tag.hasUUID("Leader") ? tag.getUUID("Leader") : null;
        homeCell = tag.contains("HomeCell") ? tag.getLong("HomeCell") : DeerPopulation.NO_CELL;
        enterCalm(Calm.GRAZE, level().getGameTime());
    }

    // ---------------------------------------------------------------- client: what the animation reads

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_STOMP) {
            stompStart = tickCount;
            return;
        }
        super.handleEntityEvent(id);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            double dx = getX() - xo, dz = getZ() - zo;
            float moved = (float) Math.sqrt(dx * dx + dz * dz);
            if (moved > 1.0F) moved = 0.0F;
            walkDistanceO = walkDistance;
            walkDistance += moved;
            walkSpeedO = walkSpeed;
            walkSpeed += (moved - walkSpeed) * 0.35F;
        }
    }

    /** Client: blocks walked so far (horizontal), at the partial tick. */
    public float walkDistance(float partial) {
        return Mth.lerp(partial, walkDistanceO, walkDistance);
    }

    /** Client: the smoothed ground speed (blocks per tick) at the partial tick. */
    public float walkSpeed(float partial) {
        return Mth.lerp(partial, walkSpeedO, walkSpeed);
    }

    /** Client: seconds into the latest stamp (negative or past its length when not stamping). */
    public float stompTime(float partial) {
        return (tickCount - stompStart + partial) / 20.0F;
    }
}
