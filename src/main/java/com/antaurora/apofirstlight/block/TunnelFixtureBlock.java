package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.TunnelFixtureBlockEntity;
import com.antaurora.apofirstlight.blockentity.TunnelFixtureBlockEntity.Entry;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.worldgen.network.road.GuideSigns;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelMesh;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A tunnel's equipment parts and handrail segments as standalone fixtures (Tunnel parts removable V1.1, 2026-10-16;
 * docs/worldgen/federal_tunnels_v1.md 7): one block per kind, a cell for every block a part's boxes reach
 * (worldgen/network/road/TunnelFixtures), its block entity holding each part that reaches it (a cell may hold several:
 * TunnelFixtureBlockEntity) and the road_structure layers it took over; the block is the cell's first part's kind.
 * Invisible: the client draws the parts in its own GPU cached pass (client/road/TunnelFixtureClient).
 * <p>
 * Taken down with the crowbar only, part by part (the part the look aims at in the cell) and over time (Kind#seconds;
 * with anything else it does not break): the part leaves every cell it holds (a cell with no part left gets back what
 * it took over); the small parts drop themselves (their variant kept, placeable again: item/TunnelFixtureItem), the
 * large ones (jet fan, gantry, portal signal, closure gate, fire door) drop materials. No vanilla tool tier: the crowbar
 * gates it (the mining rules are reworked later). Constant light behaviour (no light of its own, skylight through), so
 * the light engine never asks for the block entity driven shape off the server thread.
 * <p>
 * C3.3b (docs/worldgen/federal_junctions_c3_3b.md): the road signs of the state road junctions and town corners are the
 * same kind of fixture (road_&lt;mesh&gt;, worldgen/network/road/RoadSigns): STOP (R1-1), side road (W2-2) and curve
 * (W1-2 with a 30 MPH plaque) warnings on their posts, taken down with the crowbar and placeable again; group 2's
 * interchange guide signs (road_guide_sign, GuideSigns: the legend in the bone) the same, their posts level again.
 * <p>
 * The crosshair and the selection outline follow the parts' drawn meshes (meshhit/TunnelFixtureHits: picked where a
 * face or post is seen, the aimed part's whole outline), not the cell's 1/8 block columns; collision stays on those.
 */
public final class TunnelFixtureBlock extends Block implements EntityBlock {
    /** How a player places the part's item: on a wall (facing out of it), under a ceiling, on a floor; NONE: no item. */
    public enum Mount { WALL, CEILING, FLOOR, NONE }

    /** The kinds: the Vextris mesh road/tunnel/&lt;mesh&gt; (handrail: built in code), how it mounts, seconds to take it down. */
    public enum Kind {
        JET_FAN("jet_fan", Mount.NONE, 15), PORTAL_SIGNAL("portal_signal", Mount.NONE, 12), CMS_GANTRY("cms_gantry", Mount.NONE, 15),
        CLOSURE_GATE("closure_gate", Mount.NONE, 8), FIRE_DOOR("fire_door", Mount.NONE, 8),
        LUMINAIRE("luminaire", Mount.WALL, 3), CCTV("cctv", Mount.WALL, 2), LANE_SIGNAL("lane_signal", Mount.CEILING, 4),
        HEADLIGHT_SIGN("headlight_sign", Mount.FLOOR, 4), CLEARANCE_PLATE("clearance_plate", Mount.WALL, 2),
        EGRESS_SIGN("egress_sign", Mount.WALL, 2), NAME_PLATE("name_plate", Mount.WALL, 4),
        EMERGENCY_CABINET("emergency_cabinet", Mount.WALL, 4), HANDRAIL("handrail", Mount.FLOOR, 2),
        STOP_SIGN("stop_sign", Mount.FLOOR, 3), SIDE_ROAD_SIGN("side_road_sign", Mount.FLOOR, 3), CURVE_SIGN("curve_sign", Mount.FLOOR, 4),
        DO_NOT_ENTER_SIGN("do_not_enter_sign", Mount.FLOOR, 3), WRONG_WAY_SIGN("wrong_way_sign", Mount.FLOOR, 3),
        GUIDE_SIGN("guide_sign", Mount.FLOOR, 8), SPEED_LIMIT_SIGN("speed_limit_sign", Mount.FLOOR, 3);

        public final String mesh;
        public final Mount mount;
        public final int seconds;

        Kind(String mesh, Mount mount, int seconds) {
            this.mesh = mesh;
            this.mount = mount;
            this.seconds = seconds;
        }

        /** The registry id: tunnel_&lt;mesh&gt;, a road sign's road_&lt;mesh&gt;. */
        public String id() { return (roadSign() ? "road_" : "tunnel_") + mesh; }

        /** A junction, town corner or ramp terminal sign (C3.3b), not a tunnel's part. */
        public boolean roadSign() {
            return this == STOP_SIGN || this == SIDE_ROAD_SIGN || this == CURVE_SIGN || this == DO_NOT_ENTER_SIGN || this == WRONG_WAY_SIGN
                    || this == GUIDE_SIGN || this == SPEED_LIMIT_SIGN;
        }

        /** Whether it drops itself as an item (else materials). */
        public boolean item() { return mount != Mount.NONE; }

        /** The variant an item without one places. */
        @Nullable public String defaultBone() {
            return switch (this) {
                case EGRESS_SIGN -> "r25";
                case NAME_PLATE -> TunnelMesh.NAMES[0];
                case HANDRAIL -> String.format(java.util.Locale.ROOT, "%.4f", TunnelMesh.SPAN);
                case PORTAL_SIGNAL, CMS_GANTRY -> "r4";
                case SIDE_ROAD_SIGN, CURVE_SIGN -> "r";
                case GUIDE_SIGN -> GuideSigns.DEFAULT;
                default -> null;
            };
        }

        @Nullable public static Kind of(String mesh) {
            for (Kind k : values()) if (k.mesh.equals(mesh)) return k;
            return null;
        }
    }

    /** Small box a cell without its block entity data (not synced yet) can be aimed at. */
    private static final VoxelShape FALLBACK = Block.box(4, 4, 4, 12, 12, 12);
    /** Set while parts are being taken out of cells (their onRemove must not start it again). */
    private static final ThreadLocal<Boolean> CASCADE = ThreadLocal.withInitial(() -> false);

    private final Kind kind;

    public TunnelFixtureBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() { return kind; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TunnelFixtureBlockEntity(pos, state); }

    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }

    @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return l.getBlockEntity(p) instanceof TunnelFixtureBlockEntity be && be.placed() ? be.shape() : FALLBACK;
    }

    @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return l.getBlockEntity(p) instanceof TunnelFixtureBlockEntity be && be.placed() ? be.collision() : Shapes.empty();
    }

    @Override public int getLightBlock(BlockState s, BlockGetter l, BlockPos p) { return 0; }

    @Override public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) { return true; }

    @Override public boolean useShapeForLightOcclusion(BlockState s) { return false; }

    @Override public float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) { return 1.0F; }

    /** The crowbar takes the aimed part down in its Kind#seconds; anything else does not move it. */
    @Override public float getDestroyProgress(BlockState s, Player player, BlockGetter l, BlockPos p) {
        if (!tool(player.getMainHandItem())) return 0.0F;
        Kind k = kind;
        if (l.getBlockEntity(p) instanceof TunnelFixtureBlockEntity be) {
            Entry e = be.aimed(player.getEyePosition(), player.getViewVector(1.0F));
            if (e != null) k = e.kind;
        }
        return 1.0F / (k.seconds * 20.0F);
    }

    /** Whether a stack takes fixtures down (the crowbar; the wiring wrench stays a wiring tool). */
    public static boolean tool(ItemStack stack) { return stack.is(AflItems.CROWBAR.get()); }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        playerWillDestroy(level, pos, state, player);
        // no prediction: a cell may keep other parts; the server sends every cell as it ends up
        if (level.isClientSide) return false;
        if (level.getBlockEntity(pos) instanceof TunnelFixtureBlockEntity be && be.placed()) {
            Entry e = be.aimed(player.getEyePosition(), player.getViewVector(1.0F));
            if (e != null) {
                takeDown(level, pos, e, !player.isCreative());
                return true;
            }
        }
        return level.setBlock(pos, fluid.createLegacyBlock(), 3);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        // removed some other way (a command, an explosion): its parts leave their other cells too, nothing drops
        if (!level.isClientSide && !CASCADE.get() && !(next.getBlock() instanceof TunnelFixtureBlock)
                && level.getBlockEntity(pos) instanceof TunnelFixtureBlockEntity be && be.placed()) {
            CASCADE.set(true);
            try {
                for (Entry e : be.entries())
                    for (long c : e.part().cells()) {
                        BlockPos q = BlockPos.of(c);
                        if (!q.equals(pos) && level.isLoaded(q)) removeFrom(level, q, e);
                    }
            } finally {
                CASCADE.set(false);
            }
        }
        super.onRemove(state, level, pos, next, moved);
    }

    /** Take part e down from every loaded cell it holds (hit: the cell broken); its drops (drop) at its first cell. */
    public static void takeDown(Level level, BlockPos hit, Entry e, boolean drop) {
        BlockPos at = hit;
        for (long c : e.part().cells()) {
            BlockPos q = BlockPos.of(c);
            if (level.isLoaded(q) && level.getBlockEntity(q) instanceof TunnelFixtureBlockEntity be && be.holds(e)) { at = q; break; }
        }
        CASCADE.set(true);
        try {
            boolean hitDone = false;
            for (long c : e.part().cells()) {
                BlockPos q = BlockPos.of(c);
                hitDone |= q.equals(hit);
                if (level.isLoaded(q)) removeFrom(level, q, e);
            }
            if (!hitDone) removeFrom(level, hit, e);
        } finally {
            CASCADE.set(false);
        }
        if (drop) for (ItemStack s : drops(e)) popResource(level, at, s);
    }

    /**
     * Take part e out of cell q (if it holds it): the cell keeps its other parts (the block turned to the new first one's
     * kind), else gets back what it took over. Sends the cell to the clients either way.
     */
    private static void removeFrom(Level level, BlockPos q, Entry e) {
        if (!(level.getBlockEntity(q) instanceof TunnelFixtureBlockEntity be) || !be.holds(e)) return;
        List<Entry> rest = be.without(e);
        int structure = be.structure();
        if (rest.isEmpty()) {
            level.setBlock(q, restored(structure), 3);
            return;
        }
        BlockState want = AflBlocks.TUNNEL_FIXTURES.get(rest.get(0).kind).get().defaultBlockState();
        if (!level.getBlockState(q).is(want.getBlock())) {
            level.setBlock(q, want, 3);
            if (!(level.getBlockEntity(q) instanceof TunnelFixtureBlockEntity fresh)) return;
            be = fresh;
        }
        be.set(rest, structure);
        level.sendBlockUpdated(q, want, want, 3);
    }

    private static BlockState restored(int structure) {
        return structure > 0 ? AflBlocks.ROAD_STRUCTURE.get().defaultBlockState().setValue(RoadStructureBlock.LAYERS, structure)
                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }

    /** What a part drops: the small ones themselves (with their variant), the large ones their materials. */
    public static List<ItemStack> drops(Entry e) {
        Kind k = e.kind;
        if (k.item()) return List.of(stack(k, e.placement.bone()));
        List<ItemStack> out = new ArrayList<>();
        switch (k) {
            // ~1 t: casing and silencer sheet, the motor's copper, its feed, the hangers
            case JET_FAN -> add(out, AflItems.STEEL_PLATE.get(), 8, AflItems.STEEL_SCRAP.get(), 12, AflItems.INSULATED_COPPER_WIRE.get(), 6,
                    AflItems.POWER_CABLE.get(), 2, AflItems.STEEL_CABLE.get(), 2);
            // pole and 6 - 11 m arm, the signal heads
            case PORTAL_SIGNAL -> add(out, AflItems.STEEL_BEAM.get(), 4, AflItems.STEEL_SCRAP.get(), 8, AflItems.POWER_CABLE.get(), 2,
                    AflItems.PLASTIC_SCRAP.get(), 3);
            // pole, truss and the message sign's housing
            case CMS_GANTRY -> add(out, AflItems.STEEL_BEAM.get(), 10, AflItems.STEEL_PLATE.get(), 4, AflItems.STEEL_SCRAP.get(), 12,
                    AflItems.POWER_CABLE.get(), 3, AflItems.PLASTIC_SCRAP.get(), 4);
            // the barrier's cabinet and drive, the boom
            case CLOSURE_GATE -> add(out, AflItems.STEEL_SCRAP.get(), 4, AflItems.PLASTIC_SCRAP.get(), 3, AflItems.POWER_CABLE.get(), 1,
                    AflItems.INSULATED_COPPER_WIRE.get(), 1);
            // two leaves and the frame
            case FIRE_DOOR -> add(out, AflItems.STEEL_PLATE.get(), 4, AflItems.STEEL_SCRAP.get(), 4);
            default -> { }
        }
        return out;
    }

    private static void add(List<ItemStack> out, Object... itemsAndCounts) {
        for (int i = 0; i < itemsAndCounts.length; i += 2) out.add(new ItemStack((Item) itemsAndCounts[i], (Integer) itemsAndCounts[i + 1]));
    }

    /** A kind's item with its variant (bone; null: the default); empty for the large kinds. */
    public static ItemStack stack(Kind k, @Nullable String bone) {
        var item = AflItems.TUNNEL_FIXTURE_ITEMS.get(k);
        if (item == null) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(item.get());
        String b = bone != null ? bone : k.defaultBone();
        // a guide sign keeps its legend, its posts level again (it was cut to its slope)
        if (k == Kind.GUIDE_SIGN) b = GuideSigns.itemBone(b);
        if (b != null && k != Kind.HANDRAIL) s.getOrCreateTag().putString("Bone", b);
        return s;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof TunnelFixtureBlockEntity be) {
            Entry e = be.nearest(target.getLocation());
            if (e != null) return stack(e.kind, e.placement.bone());
        }
        return stack(kind, null);
    }
}
