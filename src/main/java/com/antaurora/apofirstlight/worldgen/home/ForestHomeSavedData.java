package com.antaurora.apofirstlight.worldgen.home;

import com.antaurora.apofirstlight.worldgen.network.contract.StructureReservation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The forest start home's reservation in the save (M4; contract section 8): written once when the world is created, read
 * on every load. Only the placement is stored (template, origin, rotation, state, version); the claims and the node H are
 * derived from it by {@link ForestHomeLayout#reservation}, so they never drift from the template frame. A world with no
 * such data never gets the house (old worlds, worlds created without the start_area gate). Since start_area rules 2
 * (2026-10-19) also the driveway's farm gate and mailbox as the world placed them (origin cell and facing, from the county
 * road G3's anchors), absent in older saves (none placed).
 */
public final class ForestHomeSavedData extends SavedData {
    public static final String NAME = "apocalypse_firstlight_forest_home";
    public static final int VERSION = 1;

    /** A multiblock fixture: its first column's bottom cell (RectMultiblockBlock#origin) and its facing. */
    public record Fixture(BlockPos origin, Direction facing) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("X", origin.getX());
            t.putInt("Y", origin.getY());
            t.putInt("Z", origin.getZ());
            t.putString("Facing", facing.getName());
            return t;
        }

        static @Nullable Fixture load(CompoundTag tag, String key) {
            if (!tag.contains(key)) return null;
            CompoundTag t = tag.getCompound(key);
            Direction d = Direction.byName(t.getString("Facing"));
            if (d == null || d.getAxis().isVertical()) return null;
            return new Fixture(new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z")), d);
        }
    }

    private @Nullable StructureReservation reservation;
    private @Nullable Fixture gate, mailbox;
    /** The planner's report (candidates, timing) for /afl dev forest_home info. */
    private String report = "";

    public @Nullable StructureReservation reservation() { return reservation; }

    public String report() { return report; }

    public @Nullable Fixture gate() { return gate; }

    public @Nullable Fixture mailbox() { return mailbox; }

    public void set(@Nullable StructureReservation r, String report) { set(r, null, null, report); }

    public void set(@Nullable StructureReservation r, @Nullable Fixture gate, @Nullable Fixture mailbox, String report) {
        this.reservation = r;
        this.gate = r == null ? null : gate;
        this.mailbox = r == null ? null : mailbox;
        this.report = report;
        setDirty();
    }

    public static ForestHomeSavedData load(CompoundTag tag) {
        ForestHomeSavedData d = new ForestHomeSavedData();
        if (tag.contains("Reservation")) {
            CompoundTag r = tag.getCompound("Reservation");
            if (r.getInt("Version") == VERSION && ForestHomeLayout.TEMPLATE.equals(r.getString("Template"))) {
                StructureReservation base = ForestHomeLayout.reservation(r.getInt("X"), r.getInt("Y") + ForestHomeLayout.GROUND, r.getInt("Z"), r.getInt("Rotation"));
                StructureReservation.State state = StructureReservation.State.valueOf(r.getString("State"));
                d.reservation = new StructureReservation(base.id(), base.template(), base.x(), base.y(), base.z(), base.rotation(), base.mirror(),
                        base.claims(), base.nodes(), base.terrain(), state, base.version());
                d.gate = Fixture.load(tag, "Gate");
                d.mailbox = Fixture.load(tag, "Mailbox");
            }
        }
        d.report = tag.getString("Report");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (reservation != null) {
            CompoundTag r = new CompoundTag();
            r.putInt("Version", VERSION);
            r.putString("Template", reservation.template());
            r.putInt("X", reservation.x());
            r.putInt("Y", reservation.y());
            r.putInt("Z", reservation.z());
            r.putInt("Rotation", reservation.rotation());
            r.putString("State", reservation.state().name());
            tag.put("Reservation", r);
            if (gate != null) tag.put("Gate", gate.save());
            if (mailbox != null) tag.put("Mailbox", mailbox.save());
        }
        tag.putString("Report", report);
        return tag;
    }
}
