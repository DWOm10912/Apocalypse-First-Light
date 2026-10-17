package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.RoadStructureBlock;
import com.antaurora.apofirstlight.block.TunnelFixtureBlock;
import com.antaurora.apofirstlight.meshhit.TunnelFixtureHits;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelFixtures;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelMesh;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * One cell of the tunnel fixtures (block/TunnelFixtureBlock; Tunnel parts removable V1.1, 2026-10-16). A cell holds every
 * part whose boxes reach it (Entry: kind and the whole placement, its variant, origin and axes: parts on a curve are
 * not grid aligned): neighbouring handrail segments meet inside a cell, an egress sign hangs over a rail, luminaires in
 * an entrance zone stand closer than a block. Each part builds its own collision and finds its other cells
 * (TunnelFixtures.Part#cells) from its entry; the block is the first entry's kind. Also the layers of the road_structure
 * cell it took over (0: it was open), restored when its last part is taken down. Never ticks; drawn by the client's
 * fixture pass (client/road/TunnelFixtureClient), not a block entity renderer.
 */
public final class TunnelFixtureBlockEntity extends BlockEntity {
    /** A part in this cell: its kind and placement; its geometry built on first use. */
    public static final class Entry {
        /** How close two placements of a kind are to be one part: origin, m; axes, cosine. */
        static final double NEAR = 0.1, ALIGNED = 0.999;

        public final TunnelFixtureBlock.Kind kind;
        public final TunnelMesh.Placement placement;
        @Nullable private volatile TunnelFixtures.Part part;

        public Entry(TunnelFixtureBlock.Kind kind, TunnelMesh.Placement placement) {
            this.kind = kind;
            this.placement = placement;
        }

        public TunnelFixtures.Part part() {
            TunnelFixtures.Part p = part;
            if (p == null) part = p = TunnelFixtures.Part.of(placement);
            return p;
        }

        /**
         * The same part: its kind, origin within NEAR and axes within ALIGNED. Not exact: the road network read back from its
         * cache (RoadNetworkJson, 2 decimals) places a part a little off from the one built fresh, so a part across chunks
         * generated in different sessions comes as two near copies (a handrail's length bone differs too); no two real parts
         * of a kind stand that close.
         */
        public boolean same(Entry o) {
            TunnelMesh.Placement a = placement, b = o.placement;
            return kind == o.kind && Math.abs(a.x() - b.x()) < NEAR && Math.abs(a.y() - b.y()) < NEAR && Math.abs(a.z() - b.z()) < NEAR
                    && dot(a.ex(), b.ex()) > ALIGNED && dot(a.ez(), b.ez()) > ALIGNED;
        }

        private static double dot(double[] u, double[] v) { return u[0] * v[0] + u[1] * v[1] + u[2] * v[2]; }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            TunnelMesh.Placement p = placement;
            t.putString("Kind", kind.mesh);
            if (p.bone() != null) t.putString("Bone", p.bone());
            t.putDouble("X", p.x());
            t.putDouble("Y", p.y());
            t.putDouble("Z", p.z());
            for (int i = 0; i < 3; i++) {
                t.putDouble("Ex" + i, p.ex()[i]);
                t.putDouble("Ey" + i, p.ey()[i]);
                t.putDouble("Ez" + i, p.ez()[i]);
            }
            return t;
        }

        @Nullable static Entry load(CompoundTag t) {
            TunnelFixtureBlock.Kind kind = TunnelFixtureBlock.Kind.of(t.getString("Kind"));
            if (kind == null) return null;
            double[] ex = new double[3], ey = new double[3], ez = new double[3];
            for (int i = 0; i < 3; i++) {
                ex[i] = t.getDouble("Ex" + i);
                ey[i] = t.getDouble("Ey" + i);
                ez[i] = t.getDouble("Ez" + i);
            }
            return new Entry(kind, new TunnelMesh.Placement(kind.mesh, t.contains("Bone") ? t.getString("Bone") : null,
                    t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"), ex, ey, ez));
        }
    }

    private List<Entry> entries = List.of();
    private int structure;
    @Nullable private transient VoxelShape shape, collision;

    public TunnelFixtureBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.TUNNEL_FIXTURE.get(), pos, state);
    }

    /** Whether a part was set (a cell without one, e.g. from /setblock, holds nothing). */
    public boolean placed() { return !entries.isEmpty(); }

    public List<Entry> entries() { return entries; }

    public int structure() { return structure; }

    /** Set the parts this cell holds and the road_structure layers it took over (0: none). */
    public void set(List<Entry> parts, int structureLayers) {
        entries = List.copyOf(parts);
        structure = structureLayers;
        shape = collision = null;
        setChanged();
    }

    /** Add a part (not when the cell holds it already). */
    public void add(Entry e) {
        if (holds(e)) return;
        List<Entry> out = new ArrayList<>(entries);
        out.add(e);
        set(out, structure);
    }

    /** Whether the cell holds this part. */
    public boolean holds(Entry e) {
        for (Entry o : entries) if (o.same(e)) return true;
        return false;
    }

    /** The cell's parts without e. */
    public List<Entry> without(Entry e) {
        List<Entry> out = new ArrayList<>();
        for (Entry o : entries) if (!o.same(e)) out.add(o);
        return out;
    }

    /**
     * The part a look aims at: the first drawn mesh the ray from eye along dir meets (meshhit/TunnelFixtureHits, as the
     * crosshair picks it and the outline shows it), else the first box it enters, else the part nearest to the cell's
     * centre; null: no parts.
     */
    @Nullable public Entry aimed(Vec3 eye, Vec3 dir) {
        if (entries.isEmpty()) return null;
        if (entries.size() == 1) return entries.get(0);
        Entry best = null;
        double bt = Double.POSITIVE_INFINITY;
        for (Entry e : entries) {
            double t = TunnelFixtureHits.ray(e.part(), eye, dir);
            if (t < bt) { bt = t; best = e; }
        }
        if (best != null) return best;
        for (Entry e : entries) {
            double t = e.part().ray(eye.x, eye.y, eye.z, dir.x, dir.y, dir.z);
            if (t < bt) { bt = t; best = e; }
        }
        if (best != null) return best;
        return nearest(Vec3.atCenterOf(getBlockPos()));
    }

    /** The part whose boxes lie nearest to a point (a hit location); null: no parts. */
    @Nullable public Entry nearest(Vec3 at) {
        Entry best = null;
        double bd = Double.POSITIVE_INFINITY;
        for (Entry e : entries) {
            double d = e.part().distance(at.x, at.y, at.z);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    /**
     * This cell's shape (aimed at, taken down): its parts' boxes on 1/8 block columns, with the road_structure it took over.
     * The crosshair and the outline use the parts' drawn meshes instead (meshhit/TunnelFixtureHits); these boxes only
     * while a mesh is not known.
     */
    public VoxelShape shape() {
        VoxelShape s = shape;
        return s != null ? s : (shape = build(false));
    }

    /** This cell's collision: as #shape without the parts that only have an outline (TunnelFixtures#collides). */
    public VoxelShape collision() {
        VoxelShape s = collision;
        return s != null ? s : (collision = build(true));
    }

    private VoxelShape build(boolean colliding) {
        VoxelShape out = Shapes.empty();
        if (!entries.isEmpty()) {
            BlockPos p = getBlockPos();
            List<double[]> boxes = new ArrayList<>();
            for (Entry e : entries)
                if (!colliding || TunnelFixtures.collides(e.placement.mesh())) boxes.addAll(e.part().cellBoxes(p.getX(), p.getY(), p.getZ()));
            for (double[] b : RoadStructureBlock.mergeAlongZ(boxes)) out = Shapes.joinUnoptimized(out, Shapes.box(b[0], b[1], b[2], b[3], b[4], b[5]), BooleanOp.OR);
            if (structure > 0) out = Shapes.joinUnoptimized(out, RoadStructureBlock.structureShape(p, structure), BooleanOp.OR);
            out = out.optimize();
        }
        return out;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (Entry e : entries) list.add(e.save());
        tag.put("Parts", list);
        if (structure > 0) tag.putInt("Structure", structure);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        List<Entry> out = new ArrayList<>();
        for (Tag t : tag.getList("Parts", Tag.TAG_COMPOUND)) {
            Entry e = Entry.load((CompoundTag) t);
            if (e != null) out.add(e);
        }
        entries = List.copyOf(out);
        structure = tag.getInt("Structure");
        shape = collision = null;
        changedOnClient();
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void onLoad() {
        super.onLoad();
        changedOnClient();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        changedOnClient();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        changedOnClient();
    }

    /** The client's fixture pass looks at this chunk again (and its neighbours: a part's cells may lie across an edge). */
    private void changedOnClient() {
        if (level == null || !level.isClientSide) return;
        BlockPos p = getBlockPos();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.antaurora.apofirstlight.client.road.TunnelFixtureClient.changed(p));
    }
}
