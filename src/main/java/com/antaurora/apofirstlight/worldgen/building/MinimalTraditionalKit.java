package com.antaurora.apofirstlight.worldgen.building;

import com.antaurora.apofirstlight.cabin.CabinBlocks;
import com.antaurora.apofirstlight.cabin.CabinBlocks.RoofShape;
import com.antaurora.apofirstlight.townhouse.TownHouseBlocks;
import com.antaurora.apofirstlight.townhouse.TownHouseBlocks.ShingleRoof;
import com.antaurora.apofirstlight.townhouse.TownHouseBlocks.Valley;
import com.antaurora.apofirstlight.worldgen.network.town.TownBuildings;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.SplittableRandom;

/**
 * Town House A V1 (2026-10-21, docs/worldgen/town_buildings_concept_v1.md, town_buildings_blocks_v1.md 8): the small town
 * house, a minimal traditional cottage of 1935-1950 on a house_small site. A side-gabled main block W x 11 (W 13, or 12 on a
 * narrow site) under a 1:2 asphalt shingle roof (ridge at y 6), a 7 x 2 front gable on its left with the picture window, a
 * concrete stoop with iron railing at the front door beside it, a brick chimney on the left gable end, a back door with a
 * stoop of its own (each a landing and a two-cell flight of the concrete stoop steps, six risers to the yard). One storey: floor y 0 (a metre over the yard, the parged foundation showing), walls y 1 .. 3, a plaster
 * ceiling in the top of y 3, the attic over it. Rooms (thin plaster partitions): the living room with the gable's bay, the
 * kitchen behind it through a cased opening, a hall to two bedrooms and the bathroom.
 * <p>Plan frame: as the canvas, facing north (u to the right seen from the street = west, w into the lot = south), the main
 * block's front wall at w F, its left wall at u 0; the chimney at u -1. The plan is mirrored (u flipped, states mirrored) for
 * half the sites (the gable on the right), then laid onto the canvas. Per site seed: the siding colour (5), the roof (3), the
 * front door's paint (4), the windows' lights (1-over-1, a third 6-over-6), the back door's screen door, the mirror.
 * <p>Worldgen sets no neighbour updates, so every state a block would work out from its neighbours when placed is written here:
 * the walls' outside faces, the partitions' joins, skirting and ceiling, the coping's ends and flue; the roofs, windows and
 * railings look at their neighbours when drawn. A site too small for the plan (narrower than MIN_W or shallower than the plan
 * and its stoops) keeps the white box.
 */
public final class MinimalTraditionalKit implements BuildingKit {
    /** The canvas height (the chimney's coping at y 8), the main block's depth, its front wall's w, the front gable's width. */
    static final int H = 9, D = 11, F = 3, PW = 7;
    /** The main block's width: MAX_W, or the site's width down to MIN_W. */
    static final int MIN_W = 12, MAX_W = 13;
    private static final String[] SIDINGS = {"lap_siding_white", "lap_siding_cream", "lap_siding_sage", "lap_siding_blue_gray", "lap_siding_tan"};
    private static final String[] ROOFS = {"asphalt_shingle_roof_charcoal", "asphalt_shingle_roof_weathered_brown", "asphalt_shingle_roof_slate"};
    private static final Direction RIGHT = HousePlan.RIGHT, LEFT = HousePlan.LEFT, BACK = HousePlan.BACK, FRONT = HousePlan.FRONT;

    private final WhiteBoxKit fallback = new WhiteBoxKit("grey_board_and_batten");

    static boolean fits(TownBuildings.Site s) { return s.width() >= MIN_W && s.depth() >= F + D + 3; }

    @Override
    public int height(TownBuildings.Site site) { return fits(site) ? H : fallback.height(site); }

    @Override
    public void build(TownBuildings.Site site, BuildingCanvas c) {
        if (!fits(site)) { fallback.build(site, c); return; }
        SplittableRandom rnd = new SplittableRandom(site.seed());
        String siding = SIDINGS[rnd.nextInt(SIDINGS.length)], roof = ROOFS[rnd.nextInt(ROOFS.length)];
        TownHouseBlocks.Paint paint = TownHouseBlocks.Paint.values()[rnd.nextInt(TownHouseBlocks.Paint.values().length)];
        TownHouseBlocks.Lites lites = rnd.nextInt(3) == 0 ? TownHouseBlocks.Lites.SIX_OVER_SIX : TownHouseBlocks.Lites.ONE_OVER_ONE;
        boolean screen = rnd.nextBoolean(), mirror = rnd.nextBoolean();
        int w = Math.min(MAX_W, c.width);
        boolean chimney = c.width >= w + 1;
        Plan p = new Plan(w, c.depth);
        p.draw(siding, roof, paint, lites, screen, chimney);
        p.lay(c, chimney, mirror);
    }

    /** One roof cell: its state and the y it lies at. */
    private record Roof(BlockState state, int y) {}

    /** The house in the plan frame (HousePlan), u -1 .. W, y -2 .. H - 1, w 0 .. depth - 1. */
    private static final class Plan extends HousePlan {
        BlockState shingles;

        Plan(int width, int depth) { super(width, MinimalTraditionalKit.H, depth); }

        /** Inside the house's walls (their cells included): the main block and the front gable. */
        @Override
        boolean inside(int u, int w) {
            return u >= 0 && u < W && w >= F && w < F + D || u >= 0 && u < PW && w >= F - 2 && w < F;
        }

        static RoofShape shape(int i, int centre) { return i == centre ? RoofShape.RIDGE : (i & 1) == 0 ? RoofShape.LOWER : RoofShape.UPPER; }

        /** The roof's cell over column (u, w), or null: the main roof's rows, the front gable's columns, a valley where they meet. */
        Roof roof(int u, int w) {
            int k = w - F, j = u >= 0 && u < PW ? Math.min(u, PW - 1 - u) : -1;
            if (u < 0 || u >= W || k >= D || k < (j >= 0 ? -2 : 0)) return null;
            if (j >= 0 && k < j) {
                // the front gable's slope, its ridge along w; eaves over its side walls
                Direction f = u <= PW / 2 ? LEFT : RIGHT;
                return new Roof(cell(f, shape(j, PW / 2), Valley.NONE), 4 + j / 2);
            }
            int i = Math.min(k, D - 1 - k);
            Direction f = k <= D / 2 ? FRONT : BACK;
            Valley v = j != k ? Valley.NONE : u == PW / 2 ? Valley.RIDGE : u < PW / 2 ? Valley.POS : Valley.NEG;
            return new Roof(cell(f, shape(i, D / 2), v), 4 + i / 2);
        }

        BlockState cell(Direction f, RoofShape sh, Valley v) {
            return shingles.setValue(CabinBlocks.Roof.FACING, f).setValue(CabinBlocks.Roof.SHAPE, sh).setValue(ShingleRoof.VALLEY, v);
        }

        void draw(String siding, String roofId, TownHouseBlocks.Paint paint, TownHouseBlocks.Lites lites, boolean screen, boolean chimney) {
            shingles = town(roofId);
            BlockState wallBlock = town(siding), foundation = town("parged_foundation"), planks = WhiteBoxKit.block("red_oak_planks"),
                    linoleum = town("linoleum_floor"), tile = WhiteBoxKit.block("restroom_floor_tile"), ceiling = town("plaster_ceiling"),
                    concrete = town("smooth_concrete"), air = Blocks.AIR.defaultBlockState();
            int back = F + D - 1;
            // the shell: foundation, floor, walls up under the roof, the roof; the rooms' air and ceiling
            for (int w = 0; w < depth; w++)
                for (int u = 0; u < W; u++) {
                    if (!inside(u, w)) continue;
                    set(u, -2, w, foundation);
                    set(u, -1, w, foundation);
                    Roof r = roof(u, w);
                    if (wall(u, w)) {
                        set(u, 0, w, foundation);
                        for (int y = 1; y < r.y(); y++) set(u, y, w, wallBlock);
                    } else {
                        boolean kitchen = u >= 1 && u <= 4 && w >= F + 7, bath = u >= W - 3 && w >= F + 7;
                        set(u, 0, w, kitchen ? linoleum : bath ? tile : planks);
                        set(u, 1, w, air);
                        set(u, 2, w, air);
                        set(u, 3, w, ceiling);
                    }
                    set(u, r.y(), w, r.state());
                }
            // windows (y 2 .. 3) and the picture window in the gable
            BlockState window = town("white_double_hung_window").setValue(TownHouseBlocks.HouseWindow.LITES, lites);
            // u, w and the outside: the gable's sides, the front bedroom's pair, the living room either side of the chimney,
            // the kitchen, the front bedroom's and the bathroom's ends, the kitchen and the back bedroom at the back
            Object[][] windows = {{0, F - 1, LEFT}, {PW - 1, F - 1, RIGHT}, {W - 3, F, FRONT}, {W - 2, F, FRONT}, {0, F + 2, LEFT},
                    {0, F + 4, LEFT}, {0, F + 8, LEFT}, {W - 1, F + 2, RIGHT}, {W - 1, F + 8, RIGHT}, {3, back, BACK}, {7, back, BACK}};
            for (Object[] at : windows)
                for (int y = 2; y <= 3; y++) set((int) at[0], y, (int) at[1], window.setValue(TownHouseBlocks.HouseWindow.FACING, (Direction) at[2]));
            BlockState picture = town("white_picture_window").setValue(TownHouseBlocks.PictureWindow.FACING, FRONT);
            for (int u = 2; u <= 4; u++) for (int y = 2; y <= 3; y++) set(u, y, F - 2, picture);
            // the front door (its outside to the street), its stoop, the railing on its open side
            int door = PW + 1;
            BlockState front = town("house_front_door").setValue(TownHouseBlocks.FrontDoor.PAINT, paint);
            door(door, F, front, BACK);
            for (int u = PW; u <= PW + 2; u++) stoop(u, F - 1, FRONT, concrete);
            BlockState railing = town("wrought_iron_railing").setValue(TownHouseBlocks.IronRailing.FACING, RIGHT);
            for (int w = F - 3; w <= F - 1; w++) set(PW + 2, 1, w, railing);
            // the back door to the kitchen and its stoop
            door(1, back, town("house_back_door").setValue(TownHouseBlocks.BackDoor.SCREEN, screen), FRONT);
            stoop(1, back + 1, BACK, concrete);
            // the chimney on the left gable end, by the living room
            if (chimney) {
                BlockState brick = town("red_brick_common");
                for (int y = -2; y < H - 1; y++) set(-1, y, F + 3, brick);
                set(-1, H - 1, F + 3, town("limestone_coping").setValue(TownHouseBlocks.Coping.FLUE, true));
            }
            rooms();
            faces();
            joins();
        }

        /**
         * The partitions: the living room and kitchen (u 1 .. 4) from the bedrooms' side along u 5 (a return at the bay's
         * corner, w F), the kitchen through a cased opening at w F + 6, a hall at F + 5 from u 6 to the right wall: the
         * front bedroom before it, the back bedroom and the bathroom (the right two cells) behind.
         */
        void rooms() {
            BlockState part = town("plaster_partition"), inner = town("interior_panel_door"), air = Blocks.AIR.defaultBlockState();
            for (int w = F; w <= F + 9; w++) partition(5, w, part);
            for (int u = 6; u <= W - 2; u++) partition(u, F + 4, part);
            for (int u = 1; u <= W - 2; u++) partition(u, F + 6, part);
            for (int w = F + 7; w <= F + 9; w++) partition(W - 4, w, part);
            // the kitchen's opening: the header only
            for (int u = 2; u <= 3; u++) { set(u, 1, F + 6, air); set(u, 2, F + 6, air); }
            // the doors swing into the rooms (the hall's into the hall)
            door(7, F + 4, inner, BACK);
            door(5, F + 5, inner, LEFT);
            door(7, F + 6, inner, FRONT);
            door(W - 3, F + 6, inner, FRONT);
        }
    }
}
