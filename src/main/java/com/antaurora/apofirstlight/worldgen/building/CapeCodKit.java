package com.antaurora.apofirstlight.worldgen.building;

import com.antaurora.apofirstlight.townhouse.TownHouseBlocks;
import com.antaurora.apofirstlight.townhouse.TownHouseBlocks.SteepShingleRoof;
import com.antaurora.apofirstlight.worldgen.network.town.TownBuildings;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.SplittableRandom;

/**
 * Town House B V1 (2026-10-21, docs/worldgen/town_buildings_concept_v1.md, town_buildings_blocks_v1.md 9): the small Cape
 * Cod of 1930-1950 on a house_small site, Town House A's neighbour. A symmetrical side-gabled block 13 x 8 under a 1:1
 * asphalt shingle roof ({@link SteepShingleRoof}, eaves at y 4 over the walls, the ridge at y 8), the front door in the
 * middle with a Colonial Revival surround, two double-hung windows with louvred shutters either side, a concrete stoop
 * with iron railings on both sides; a brick chimney on the left gable end (sites 14 wide), two attic windows in each gable
 * (the attic unfinished). One storey as A: floor y 0, walls y 1 .. 3, the plaster ceiling in the top of y 3. Rooms: the
 * living room across the front left, the kitchen behind it through a cased opening, a hall from the living room to the
 * back door, the bedroom (with a closet alcove) on the right and the bathroom behind it.
 * <p>Plan frame as {@link HousePlan}: the front wall at w F, the left gable at u 0, the chimney at u -1; mirrored for half
 * the sites. Per site seed: the siding (5), the roof (3), the shutters (green, black, wine), the front door (half the
 * shutters' paint, else any of four), the lights (6-over-6, a third 1-over-1), the back door's screen door, the mirror.
 * A site narrower than W or shallower than the plan and its stoops keeps the white box ({@link SmallHouseKit} gives such
 * sites to Town House A).
 */
public final class CapeCodKit implements BuildingKit {
    /** The canvas height (the chimney's coping at y 9), the block's width and depth, its front wall's w. */
    static final int H = 10, W = 13, D = 8, F = 3;
    private static final String[] SIDINGS = {"lap_siding_white", "lap_siding_cream", "lap_siding_sage", "lap_siding_blue_gray", "lap_siding_tan"};
    private static final String[] ROOFS = {"steep_asphalt_shingle_roof_charcoal", "steep_asphalt_shingle_roof_weathered_brown", "steep_asphalt_shingle_roof_slate"};
    private static final TownHouseBlocks.Paint[] SHUTTER_PAINTS = {TownHouseBlocks.Paint.GREEN, TownHouseBlocks.Paint.BLACK, TownHouseBlocks.Paint.WINE};
    private static final Direction RIGHT = HousePlan.RIGHT, LEFT = HousePlan.LEFT, BACK = HousePlan.BACK, FRONT = HousePlan.FRONT;

    private final WhiteBoxKit fallback = new WhiteBoxKit("grey_board_and_batten");

    static boolean fits(TownBuildings.Site s) { return s.width() >= W && s.depth() >= F + D + 3; }

    @Override
    public int height(TownBuildings.Site site) { return fits(site) ? H : fallback.height(site); }

    @Override
    public void build(TownBuildings.Site site, BuildingCanvas c) {
        if (!fits(site)) { fallback.build(site, c); return; }
        SplittableRandom rnd = new SplittableRandom(site.seed());
        String siding = SIDINGS[rnd.nextInt(SIDINGS.length)], roof = ROOFS[rnd.nextInt(ROOFS.length)];
        TownHouseBlocks.Paint shutters = SHUTTER_PAINTS[rnd.nextInt(SHUTTER_PAINTS.length)];
        TownHouseBlocks.Paint paint = rnd.nextBoolean() ? shutters : TownHouseBlocks.Paint.values()[rnd.nextInt(TownHouseBlocks.Paint.values().length)];
        TownHouseBlocks.Lites lites = rnd.nextInt(3) == 0 ? TownHouseBlocks.Lites.ONE_OVER_ONE : TownHouseBlocks.Lites.SIX_OVER_SIX;
        boolean screen = rnd.nextBoolean(), mirror = rnd.nextBoolean();
        boolean chimney = c.width >= W + 1;
        Plan p = new Plan(c.depth);
        p.draw(siding, roof, shutters, paint, lites, screen, chimney);
        p.lay(c, chimney, mirror);
    }

    /** The house in the plan frame (HousePlan), u -1 .. W, y -2 .. H - 1, w 0 .. depth - 1. */
    private static final class Plan extends HousePlan {
        Plan(int depth) { super(CapeCodKit.W, CapeCodKit.H, depth); }

        @Override
        boolean inside(int u, int w) { return u >= 0 && u < W && w >= F && w < F + D; }

        /** The roof's row over w: the front slope's cells up from y 4 at the front wall, the back slope's from the back wall. */
        static int roofY(int w) {
            int k = w - F;
            return 4 + Math.min(k, D - 1 - k);
        }

        void draw(String siding, String roofId, TownHouseBlocks.Paint shutterPaint, TownHouseBlocks.Paint paint, TownHouseBlocks.Lites lites,
                  boolean screen, boolean chimney) {
            BlockState wallBlock = town(siding), foundation = town("parged_foundation"), planks = WhiteBoxKit.block("red_oak_planks"),
                    linoleum = town("linoleum_floor"), tile = WhiteBoxKit.block("restroom_floor_tile"), ceiling = town("plaster_ceiling"),
                    concrete = town("smooth_concrete"), air = Blocks.AIR.defaultBlockState(), shingles = town(roofId);
            int back = F + D - 1;
            // the shell: foundation, floor, walls up under the roof (the gables to the slope), the roof; the rooms' air and ceiling
            for (int w = F; w <= back; w++)
                for (int u = 0; u < W; u++) {
                    set(u, -2, w, foundation);
                    set(u, -1, w, foundation);
                    int ry = roofY(w);
                    if (wall(u, w)) {
                        set(u, 0, w, foundation);
                        for (int y = 1; y < ry; y++) set(u, y, w, wallBlock);
                    } else {
                        boolean kitchen = u <= 4 && w >= F + 5, bath = u >= 8 && u <= 9 && w >= F + 5;
                        set(u, 0, w, kitchen ? linoleum : bath ? tile : planks);
                        set(u, 1, w, air);
                        set(u, 2, w, air);
                        set(u, 3, w, ceiling);
                    }
                    set(u, ry, w, shingles.setValue(SteepShingleRoof.FACING, w - F < D / 2 ? FRONT : BACK));
                }
            // windows: the front's four (y 2 .. 3, shutters), the gables' (the living room, the kitchen, the bedroom), the back's
            BlockState window = town("white_double_hung_window").setValue(TownHouseBlocks.HouseWindow.LITES, lites);
            BlockState shuttered = window.setValue(TownHouseBlocks.HouseWindow.SHUTTERS, TownHouseBlocks.Shutters.valueOf(shutterPaint.name()));
            for (int u : new int[]{2, 4, 8, 10}) for (int y = 2; y <= 3; y++) set(u, y, F, shuttered.setValue(TownHouseBlocks.HouseWindow.FACING, FRONT));
            Object[][] windows = {{0, F + 1, LEFT}, {0, F + 6, LEFT}, {W - 1, F + 2, RIGHT}, {3, back, BACK}};
            for (Object[] at : windows)
                for (int y = 2; y <= 3; y++) set((int) at[0], y, (int) at[1], window.setValue(TownHouseBlocks.HouseWindow.FACING, (Direction) at[2]));
            // the bathroom's high window, the attic's (one block, y 5) two in each gable either side of the ridge
            set(8, 3, back, window.setValue(TownHouseBlocks.HouseWindow.FACING, BACK));
            for (int w : new int[]{F + 2, F + 5}) set(0, 5, w, window.setValue(TownHouseBlocks.HouseWindow.FACING, LEFT));
            for (int w : new int[]{F + 3, F + 4}) set(W - 1, 5, w, window.setValue(TownHouseBlocks.HouseWindow.FACING, RIGHT));
            // the front door in the middle (its surround), the stoop before it, railings on both sides
            BlockState front = town("house_front_door").setValue(TownHouseBlocks.FrontDoor.PAINT, paint).setValue(TownHouseBlocks.FrontDoor.SURROUND, true);
            door(6, F, front, BACK);
            for (int u = 5; u <= 7; u++) stoop(u, F - 1, FRONT, concrete);
            BlockState railing = town("wrought_iron_railing");
            for (int w = F - 3; w <= F - 1; w++) {
                set(5, 1, w, railing.setValue(TownHouseBlocks.IronRailing.FACING, LEFT));
                set(7, 1, w, railing.setValue(TownHouseBlocks.IronRailing.FACING, RIGHT));
            }
            // the back door at the hall's end and its stoop
            door(6, back, town("house_back_door").setValue(TownHouseBlocks.BackDoor.SCREEN, screen), FRONT);
            stoop(6, back + 1, BACK, concrete);
            // the chimney on the left gable end, two wide astride the ridge, its coping (the flue in the back cell)
            if (chimney) {
                BlockState brick = town("red_brick_common"), coping = town("limestone_coping").setValue(TownHouseBlocks.Coping.AXIS, Direction.Axis.Z);
                for (int w = F + 3; w <= F + 4; w++) for (int y = -2; y < H - 1; y++) set(-1, y, w, brick);
                set(-1, H - 1, F + 3, coping.setValue(TownHouseBlocks.Coping.RIGHT, false));
                set(-1, H - 1, F + 4, coping.setValue(TownHouseBlocks.Coping.LEFT, false).setValue(TownHouseBlocks.Coping.FLUE, true));
            }
            rooms();
            faces();
            joins();
        }

        /**
         * The partitions: the living room (u 1 .. 6, w F + 1 .. F + 3) from the kitchen along w F + 4 (a cased opening at
         * u 2 .. 3), the kitchen (u 1 .. 4) from the hall (u 6) along u 5; the bedroom side along u 7: the bedroom
         * (u 8 .. 11, its closet alcove u 11 behind) before the bathroom (u 8 .. 9), along w F + 4 and u 10.
         */
        void rooms() {
            BlockState part = town("plaster_partition"), inner = town("interior_panel_door"), air = Blocks.AIR.defaultBlockState();
            for (int u = 1; u <= 5; u++) partition(u, F + 4, part);
            for (int w = F + 5; w <= F + 6; w++) partition(5, w, part);
            for (int w = F + 1; w <= F + 6; w++) partition(7, w, part);
            for (int u = 8; u <= 10; u++) partition(u, F + 4, part);
            for (int w = F + 5; w <= F + 6; w++) partition(10, w, part);
            // the kitchen's opening: the header only
            for (int u = 2; u <= 3; u++) { set(u, 1, F + 4, air); set(u, 2, F + 4, air); }
            // the doors swing into the rooms
            door(5, F + 5, inner, LEFT);
            door(7, F + 3, inner, RIGHT);
            door(7, F + 5, inner, RIGHT);
        }
    }
}
