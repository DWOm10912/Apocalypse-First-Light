package com.antaurora.apofirstlight.creature.deer;

import com.antaurora.apofirstlight.registry.AflEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

/**
 * A white-tailed deer's carcass (docs/gameplay/afl_whitetail_deer_d0_design.md 11; the corpse rule of
 * docs/gameplay/afl_fauna_plan_v0.md 1): it takes the deer's place at the death and stays, saved with its chunk. The fall is
 * the export's keyframed death clip (client/creature/DeerCorpseRenderer, played by the time since the death), not a ragdoll
 * yet. It does nothing per tick. Butchering it (skin, venison) is D3, rotting later; until then it cannot be clicked, hurt
 * or pushed and is walked through.
 */
public final class DeerCorpse extends Entity implements IEntityAdditionalSpawnData {
    private boolean doe;
    private float bodyYaw;
    private long diedAt;

    public DeerCorpse(EntityType<? extends DeerCorpse> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Server, at a deer's death: its body where it stood, put down on the ground below if it died in a bound. */
    static void spawn(ServerLevel level, DeerEntity dead) {
        DeerCorpse corpse = AflEntities.DEER_CORPSE.get().create(level);
        if (corpse == null) return;
        corpse.doe = dead.doe();
        corpse.bodyYaw = dead.yBodyRot;
        corpse.diedAt = level.getGameTime();
        corpse.moveTo(dead.getX(), ground(level, dead), dead.getZ(), dead.yBodyRot, 0.0F);
        level.addFreshEntity(corpse);
    }

    /** The top of the first solid shape under its feet, at most 8 blocks down. */
    private static double ground(ServerLevel level, DeerEntity dead) {
        if (dead.onGround()) return dead.getY();
        BlockPos.MutableBlockPos p = dead.blockPosition().mutable();
        for (int i = 0; i < 8; i++, p.move(0, -1, 0)) {
            BlockState s = level.getBlockState(p);
            VoxelShape shape = s.getCollisionShape(level, p);
            if (!shape.isEmpty() && p.getY() + shape.max(net.minecraft.core.Direction.Axis.Y) <= dead.getY() + 1e-3) {
                return p.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
            }
        }
        return dead.getY();
    }

    public boolean doe() {
        return doe;
    }

    public float bodyYaw() {
        return bodyYaw;
    }

    /** Game time of the death: the fall plays from it (and the rot clock, later). */
    public long diedAt() {
        return diedAt;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(1.5);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    // ---------------------------------------------------------------- save and spawn data

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("Doe", doe);
        tag.putFloat("BodyYaw", bodyYaw);
        tag.putLong("DiedAt", diedAt);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        doe = tag.getBoolean("Doe");
        bodyYaw = tag.getFloat("BodyYaw");
        diedAt = tag.getLong("DiedAt");
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf b) {
        b.writeBoolean(doe);
        b.writeFloat(bodyYaw);
        b.writeLong(diedAt);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf b) {
        doe = b.readBoolean();
        bodyYaw = b.readFloat();
        diedAt = b.readLong();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
