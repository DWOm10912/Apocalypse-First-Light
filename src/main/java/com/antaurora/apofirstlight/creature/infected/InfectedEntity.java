package com.antaurora.apofirstlight.creature.infected;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Infected Z1a (docs/gameplay/afl_infected_z0_design.md 12): the common infected, the r6 shambler. AFL's own mob, not a
 * vanilla zombie: its look is the r6 skinned mesh (client/creature/InfectedRenderer), its voice the infected sounds through
 * RangedSound (InfectedSounds). Z1a's behaviour is vanilla's plain melee mob, slowed down: it wanders, sees a player within
 * its follow range and shambles after them, and strikes with both arms; the blow lands {@link #HIT_TICK} ticks into the
 * swing (the clip's strike), and only if the target is still in reach. Hearing, sight rules, doors and grabs are Z1b.
 *
 * <p>No loot, no experience, no red hurt tint and no death smoke (creature/AflCreatures). It does not lie down for a second
 * as vanilla's dying mobs do: on death it is replaced at once by its body (InfectedCorpse), which falls as a ragdoll and
 * stays, searchable.
 */
public class InfectedEntity extends Monster {
    /** Ticks from the start of a swing to the blow (shambler_clips.json attack_hit 0.3 s); a swing lasts {@link #SWING_TICKS}. */
    public static final int HIT_TICK = 6, SWING_TICKS = 12;
    /** At most one swing per this many ticks. */
    private static final int SWING_INTERVAL = 30;
    /** A blow reaches this much further than the swing's start reach (it lunges into it). */
    private static final double HIT_REACH_FACTOR = 1.5;
    /** Entity event: a swing starts (the client plays the attack clip). */
    static final byte EVENT_SWING = 4;

    // server
    private int nextSwing, hitAt = -1;
    @Nullable private LivingEntity hitTarget;
    private int nextBreath;
    // client
    private int swingStart = Integer.MIN_VALUE / 2;
    private float walkDistance, walkDistanceO, walkSpeed, walkSpeedO;

    public InfectedEntity(EntityType<? extends InfectedEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        // A mob's ground speed goes with the square of its speed (Mob.setSpeed also sets zza, and travel scales the
        // input by the speed again): about 2.16 * s^2 blocks per tick. 0.165: about 1.18 m/s chasing, 0.58 m/s
        // wandering (x 0.7); with the 0.8-block steps that is about 88 steps a minute. (0.19 was 1.56 m/s, 155 a minute.)
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.165)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 28.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.1);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new Swing(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Vanilla's melee chase; the hit itself is the entity's, {@link #HIT_TICK} ticks after the swing starts. */
    private static final class Swing extends MeleeAttackGoal {
        private final InfectedEntity infected;

        Swing(InfectedEntity infected) {
            super(infected, 1.0, false);
            this.infected = infected;
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target, double distanceSqr) {
            if (distanceSqr <= getAttackReachSqr(target) && isTimeToAttack() && infected.canSwing()) {
                resetAttackCooldown();
                infected.startSwing(target);
            }
        }
    }

    boolean canSwing() {
        return tickCount >= nextSwing && hitAt < 0;
    }

    void startSwing(LivingEntity target) {
        nextSwing = tickCount + SWING_INTERVAL;
        hitAt = tickCount + HIT_TICK;
        hitTarget = target;
        level().broadcastEntityEvent(this, EVENT_SWING);
        InfectedSounds.attack(this);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (hitAt >= 0 && tickCount >= hitAt) {
            LivingEntity target = hitTarget;
            hitAt = -1;
            hitTarget = null;
            if (target != null && target.isAlive() && hasLineOfSight(target)) {
                double reach = (getBbWidth() * 2.0 * getBbWidth() * 2.0 + target.getBbWidth()) * HIT_REACH_FACTOR * HIT_REACH_FACTOR;
                if (distanceToSqr(target) <= reach) doHurtTarget(target);
            }
        }
        if (getTarget() != null && isAlive() && tickCount >= nextBreath) {
            if (nextBreath > 0) InfectedSounds.chase(this);
            nextBreath = tickCount + 30 + random.nextInt(21);
        }
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity before = getTarget();
        super.setTarget(target);
        if (!level().isClientSide && target != null && before == null && getTarget() == target) {
            InfectedSounds.alert(this);
            nextBreath = tickCount + 40 + random.nextInt(21);
        }
    }

    // ---------------------------------------------------------------- sounds (all through RangedSound, InfectedSounds)

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    @Override
    public void playAmbientSound() {
        if (getTarget() == null && level() instanceof ServerLevel) InfectedSounds.idle(this);
    }

    @Override
    protected void playHurtSound(DamageSource source) {
        if (level() instanceof ServerLevel) InfectedSounds.hurt(this);
    }

    @Override
    @Nullable
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // the feet scrape: the block's own step, quieter (no drag sound yet)
        net.minecraft.world.level.block.SoundType type = state.getSoundType(level(), pos, this);
        playSound(type.getStepSound(), type.getVolume() * 0.1F, type.getPitch() * 0.9F);
    }

    // ---------------------------------------------------------------- death: the body takes its place

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!dead || level().isClientSide || isRemoved()) return;
        InfectedSounds.death(this);
        InfectedCorpse.spawn((ServerLevel) level(), this, source);
        // no dying second on the ground, no smoke: the body is already there
        remove(RemovalReason.KILLED);
    }

    @Override
    protected void tickDeath() {
        if (!level().isClientSide && !isRemoved()) remove(RemovalReason.KILLED);
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

    // ---------------------------------------------------------------- client: what the animation reads

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_SWING) {
            swingStart = tickCount;
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
            if (moved > 1.0F) moved = 0.0F;   // a teleport or the first position: no steps
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

    /** Client: the smoothed walking speed (blocks per tick) at the partial tick. */
    public float walkSpeed(float partial) {
        return Mth.lerp(partial, walkSpeedO, walkSpeed);
    }

    /** Client: seconds into the current swing (negative or past its length when not swinging). */
    public float swingTime(float partial) {
        return (tickCount - swingStart + partial) / 20.0F;
    }
}
