package com.antaurora.apofirstlight.tree;

import com.antaurora.apofirstlight.tree.TreeForms.Species;
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
import net.minecraft.world.phys.AABB;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

/**
 * Mesh Trees P2 (docs/rendering/mesh_trees_p2_felling.md, H/I/K/M): one felled tree while it falls, the only moving
 * object of the felling; standing trees stay static chunk geometry. Created by the server at the last chop, before the
 * tree's cells go; it carries everything the fall needs (root, species, variant, facing, direction, rest angle, cut
 * height, start tick), so nothing is read back from blocks that are gone.
 * <ul>
 * <li>Every side computes the same fall curve from those values (FellingRules#curve); clients only draw it
 * (FallingTreeRenderer). No collision, no physics, not pushable, cannot be hurt.</li>
 * <li>Server, until the contact: each tick's sweep hurts the living bodies in its way (FellingServer#sweep,
 * FellingRules#hit; 2026-10-12), each once per kind of hit; a fall that went on while nobody was here hits nothing.</li>
 * <li>Server: the impact (sound, particles) at the contact tick, then after lying a second the drops, once per fall
 * (the ledger's settle), and the entity goes. Saved with its chunk like any entity: after a reload, a chunk unload or a
 * server stop it goes on from its start tick, so a fall that should be over settles at once; a fall already settled
 * (restored twice) is removed without drops.</li>
 * </ul>
 */
public final class FallingTree extends Entity implements IEntityAdditionalSpawnData {
    BlockPos root = BlockPos.ZERO;
    Species species = Species.WHITE_OAK;
    int variant, turns;
    /** Fall direction (radians, atan2(dz, dx)), rest angle (degrees), cut height above the root's floor. */
    float direction, rest = 90, cut = 0.6F;
    long start;
    boolean impact;
    private FellingRules.Curve curve;
    private TreeForms.Form form;
    /** Server: the angle swept up to and its tick (NaN after a load); the bodies hit, kinds of hit as bits. */
    private float sweptAngle = Float.NaN;
    private long sweptTick;
    final java.util.Map<java.util.UUID, Integer> struck = new java.util.HashMap<>();
    /** Client: the frozen geometry and level of this fall (FallingTreeRenderer), dropped with the entity. */
    Object mesh;

    public FallingTree(EntityType<? extends FallingTree> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    static FallingTree create(ServerLevel level, BlockPos root, Species species, int variant, int turns, float direction, float rest, float cut, long start) {
        var e = new FallingTree(FellingRegistry.fallingTree, level);
        e.root = root.immutable();
        e.species = species; e.variant = variant; e.turns = turns;
        e.direction = direction; e.rest = rest; e.cut = cut; e.start = start;
        e.setPos(root.getX() + 0.5, root.getY() + cut, root.getZ() + 0.5);
        e.sweptAngle = 0;
        e.sweptTick = 0;
        return e;
    }

    TreeShape.Layout layout() { return TreeShape.layout(species, variant, turns); }

    TreeForms.Form form() {
        if (form == null) form = TreeForms.grow(species, variant);
        return form;
    }

    FellingRules.Curve curve() {
        if (curve == null) curve = FellingRules.curve(species, layout().height, cut, rest);
        return curve;
    }

    /** Degrees from upright at a partial tick (the pause first, then the curve). */
    float angle(float partial) {
        return curve().at(level().getGameTime() - start + partial);
    }

    @Override
    public void tick() {
        if (level().isClientSide() || !(level() instanceof ServerLevel server)) return;
        var journal = FellingServer.journal(server);
        if (journal.ledger.isSettled(getUUID())) { discard(); return; }
        long t = server.getGameTime() - start;
        var c = curve();
        if (!impact && t > 0 && t <= c.contact()) {
            float a = c.at(t);
            if (!Float.isNaN(sweptAngle) && t > sweptTick)
                FellingServer.sweep(server, this, sweptAngle, a, Math.toRadians(a - sweptAngle) * 20 / (t - sweptTick));
            sweptAngle = a;
            sweptTick = t;
        }
        if (!impact && t >= c.contact()) {
            impact = true;
            // a fall that ended while nobody was here (unloaded, server stopped) lands without its sound
            if (t <= c.contact() + 5) FellingServer.impact(server, this);
        }
        if (t >= c.contact() + FellingRules.LIE_TICKS) {
            if (journal.ledger.settle(getUUID(), server.getGameTime())) {
                journal.setDirty();
                FellingServer.drops(server, this);
            }
            discard();
        }
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        root = BlockPos.of(tag.getLong("root"));
        Species s = Species.byId(tag.getString("species"));
        species = s == null ? Species.WHITE_OAK : s;
        variant = Math.floorMod(tag.getInt("variant"), TreeShape.VARIANTS);
        turns = tag.getInt("turns") & 3;
        direction = tag.getFloat("direction");
        rest = tag.getFloat("rest");
        cut = tag.getFloat("cut");
        start = tag.getLong("start");
        impact = tag.getBoolean("impact");
        curve = null;
        form = null;
        sweptAngle = Float.NaN;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("root", root.asLong());
        tag.putString("species", species.id);
        tag.putInt("variant", variant);
        tag.putInt("turns", turns);
        tag.putFloat("direction", direction);
        tag.putFloat("rest", rest);
        tag.putFloat("cut", cut);
        tag.putLong("start", start);
        tag.putBoolean("impact", impact);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf b) {
        b.writeLong(root.asLong());
        b.writeByte(species.ordinal());
        b.writeByte(variant);
        b.writeByte(turns);
        b.writeFloat(direction);
        b.writeFloat(rest);
        b.writeFloat(cut);
        b.writeLong(start);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf b) {
        root = BlockPos.of(b.readLong());
        species = Species.values()[Math.floorMod(b.readByte(), Species.values().length)];
        variant = Math.floorMod(b.readByte(), TreeShape.VARIANTS);
        turns = b.readByte() & 3;
        direction = b.readFloat();
        rest = b.readFloat();
        cut = b.readFloat();
        start = b.readLong();
        curve = null;
        form = null;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean displayFireAnimation() { return false; }

    /** Seen from as far as the trees are drawn (the tracking range limits it further). */
    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return d < 256 * 256; }

    /** The whole tree in any pose round its pivot (the entity's own box is a small marker there). */
    @Override
    public AABB getBoundingBoxForCulling() {
        double h = layout().height + 2;
        return new AABB(getX() - h, getY() - 4, getZ() - h, getX() + h, getY() + h, getZ() + h);
    }
}
