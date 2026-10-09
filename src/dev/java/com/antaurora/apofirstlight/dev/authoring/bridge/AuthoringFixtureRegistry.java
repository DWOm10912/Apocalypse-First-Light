package com.antaurora.apofirstlight.dev.authoring.bridge;

import com.antaurora.apofirstlight.block.*;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative, development-only authoring contract for block-entity fixtures and multiblocks.
 * Compatibility wrapper only: part geometry comes from each block's own public helpers and all
 * support rules are finally decided by the block's own canSurvive. No block class is changed.
 */
final class AuthoringFixtureRegistry {
    enum AuthoringClass { SAFE_FIXTURE, STORAGE_WITH_INVENTORY, MACHINE, UNSAFE }
    enum Support {
        FLOOR("Every lowest part needs a block with a sturdy top face directly below it."),
        FLOOR_OR_DESK("Sturdy top face below, or a modern_office_desk part below (the block then reports lowered=true)."),
        FLOOR_CLEAR_ABOVE("Sturdy top face below and no collision in the cell directly above."),
        FLOOR_OR_COLUMN("Sturdy top face below, a canopy column below (stacked segments), or a power cable below (the base's feed; 2026-10-08)."),
        ATTACHED_OPPOSITE_FACING("The block on the side opposite `facing` needs a sturdy face toward the fixture; facing=down hangs under a ceiling."),
        NONE("No engine support requirement."),
        BLOCK_RULE("Checked with the block's own canSurvive after placement; no simplified rule is published.");
        final String text;
        Support(String text){this.text=text;}
    }
    /** SELF_AND_NEIGHBORS: the fixture's own connection properties are computed from its neighbours. */
    enum ShapePolicy { NEIGHBORS, SELF_AND_NEIGHBORS }
    interface PartLocator { BlockPos locate(BlockPos anchor, Direction facing, String part); }
    /**
     * A multiblock whose cells are not one 'part' property (2026-10-08: the underground fuel tank, cells by axis, flipped,
     * along, across and level). {@code facing} is the structure's own orientation (for the tank: toward its fill end).
     */
    interface CellLayout {
        LinkedHashMap<BlockPos,BlockState> cells(Block block,BlockPos anchor,Direction facing);
        BlockPos anchorOf(BlockPos pos,BlockState state);
        Direction facingOf(BlockState state);
        /** Width x height x depth for facing=north. */
        int[] size();
    }
    /** Parts are listed in placement order; the first entry is the master/anchor part. */
    record Multiblock(String partProperty, List<String> parts, PartLocator locator) {
        String master(){return parts.get(0);}
        BlockPos offset(Direction facing,String part){return locator.locate(BlockPos.ZERO,facing,part);}
        BlockPos anchorOf(BlockPos pos,Direction facing,String part){return pos.subtract(offset(facing,part));}
    }
    record Fixture(String id, AuthoringClass authoringClass, String category, String facingProperty, Set<Direction> facings,
                   Map<String,Set<String>> variants, Map<String,String> fixed, Set<String> connections, boolean hasInventory,
                   Support support, ShapePolicy shapePolicy, Multiblock multiblock, CellLayout layout, String notes) {
        boolean allowed(){return authoringClass==AuthoringClass.SAFE_FIXTURE||authoringClass==AuthoringClass.STORAGE_WITH_INVENTORY;}
        boolean structured(){return multiblock!=null||layout!=null;}
        String placementTool(){return !allowed()?"NONE":structured()?"place_multiblock":"place_fixture";}
    }

    private static final String A="apocalypse_firstlight:";
    private static final Set<Direction> H4=EnumSet.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST);
    private static final Set<Direction> ATTACH5=EnumSet.of(Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST);
    private static final Set<Direction> ALL6=EnumSet.allOf(Direction.class);
    private static String constant(String part){return part.toUpperCase(Locale.ROOT);}
    private static final Multiblock TWO_TALL=new Multiblock("half",List.of("lower","upper"),(a,f,p)->p.equals("upper")?a.above():a);
    private static final Multiblock COOLER=new Multiblock("part",List.of("lower_left","lower_right","upper_left","upper_right"),
            (a,f,p)->BeverageCoolerBlock.partPosition(a,f,BeverageCoolerBlock.Part.valueOf(constant(p))));
    private static final Multiblock FREEZER=new Multiblock("part",List.of("left","right"),
            (a,f,p)->ChestFreezerBlock.partPosition(a,f,ChestFreezerBlock.Part.valueOf(constant(p))));
    // CommercialGlassDoubleDoorBlock keeps its helper private; this mirrors it: second leaf at facing.getClockWise().
    private static final Multiblock GLASS_DOOR=new Multiblock("part",List.of("lower_left","lower_right","upper_left","upper_right"),
            (a,f,p)->{BlockPos column=p.endsWith("right")?a.relative(f.getClockWise()):a;return p.startsWith("upper")?column.above():column;});
    private static final Multiblock DUMPSTER=new Multiblock("part",List.of("master","secondary"),
            (a,f,p)->CommercialDumpsterBlock.partPosition(a,f,CommercialDumpsterBlock.Part.valueOf(constant(p))));
    private static final Multiblock FUEL_DISPENSER=new Multiblock("cell",List.of("a0","b0","a1","b1","a2","b2"),
            (a,f,p)->com.antaurora.apofirstlight.block.FuelDispenserBlock.cellPosition(a,f,com.antaurora.apofirstlight.block.FuelDispenserBlock.Cell.valueOf(constant(p))));
    private static final Multiblock FUEL_SUMP=new Multiblock("cell",List.of("a0","b0","a1","b1"),
            (a,f,p)->com.antaurora.apofirstlight.block.FuelDispenserSumpBlock.cellPosition(a,f,com.antaurora.apofirstlight.block.FuelDispenserSumpBlock.Cell.valueOf(constant(p))));
    /** Anchor = the bottom centre cell (along 3, across 1, level 0); facing = toward the fill end (along 6). */
    private static final CellLayout FUEL_TANK=new CellLayout(){
        @Override public LinkedHashMap<BlockPos,BlockState> cells(Block block,BlockPos anchor,Direction facing){
            return new LinkedHashMap<>(((com.antaurora.apofirstlight.block.UndergroundFuelTankBlock)block).cells(anchor,facing));
        }
        @Override public BlockPos anchorOf(BlockPos pos,BlockState state){return com.antaurora.apofirstlight.block.UndergroundFuelTankBlock.rootPosition(pos,state);}
        @Override public Direction facingOf(BlockState state){return com.antaurora.apofirstlight.block.UndergroundFuelTankBlock.alongDir(state);}
        @Override public int[] size(){return new int[]{3,3,7};}
    };
    private static final Multiblock DESK=new Multiblock("part",List.of("center","left","right"),
            (a,f,p)->ModernOfficeDeskBlock.partPosition(a,f,ModernOfficeDeskBlock.Part.valueOf(constant(p))));
    private static final Multiblock WORKSTATION=new Multiblock("part",List.of("base","side","upper","upper_side"),
            (a,f,p)->StaticWorkstationBlock.partPosition(a,f,StaticWorkstationBlock.Part.valueOf(constant(p))));

    private static final Map<String,Fixture> FIXTURES=build();
    private static final Map<BlockState,Boolean> OWNS_BLOCK_ENTITY=new ConcurrentHashMap<>();

    private static final class Def {
        final String id;final AuthoringClass c;final String category;String facing;Set<Direction> facings=Set.of();
        final Map<String,Set<String>> variants=new LinkedHashMap<>();final Map<String,String> fixed=new LinkedHashMap<>();
        final Set<String> connections=new LinkedHashSet<>();boolean inventory;Support support=Support.BLOCK_RULE;
        ShapePolicy shape=ShapePolicy.NEIGHBORS;Multiblock multi;CellLayout layout;String notes="";
        Def(String id,AuthoringClass c,String category){this.id=id;this.c=c;this.category=category;}
        Def facing(Set<Direction> f){facing="facing";facings=f;return this;}
        Def multi(Multiblock m){multi=m;return this;}
        Def layout(CellLayout l,Set<Direction> f){layout=l;facings=f;return this;}
        Def variant(String p,String... v){variants.put(p,Set.of(v));return this;}
        Def fixed(String... pairs){for(int i=0;i<pairs.length;i+=2)fixed.put(pairs[i],pairs[i+1]);return this;}
        Def connect(String... p){connections.addAll(List.of(p));shape=ShapePolicy.SELF_AND_NEIGHBORS;return this;}
        Def inventory(){inventory=true;return this;}
        Def support(Support s){support=s;return this;}
        Def notes(String n){notes=n;return this;}
        Fixture build(){return new Fixture(id,c,category,facing,Set.copyOf(facings),Map.copyOf(variants),Map.copyOf(fixed),Set.copyOf(connections),inventory,support,shape,multi,layout,notes);}
    }
    /** Commercial Dumpster V2, one block per colour: 18-slot searchable container, two lids, starts shut and empty. */
    private static Def dumpster(String id){
        return new Def(A+id,AuthoringClass.STORAGE_WITH_INVENTORY,"utility").facing(H4).multi(DUMPSTER).fixed("left_open","false","right_open","false")
                .inventory().support(Support.FLOOR).notes("2 wide. Anchor = master; secondary at facing.getClockWise(). V2: 18-slot searchable container, two lids, starts shut and empty.");
    }
    private static Map<String,Fixture> build(){
        var safe=AuthoringClass.SAFE_FIXTURE;var storage=AuthoringClass.STORAGE_WITH_INVENTORY;var machine=AuthoringClass.MACHINE;var unsafe=AuthoringClass.UNSAFE;
        var defs=List.of(
            new Def(A+"retail_shelf_single",storage,"retail").facing(H4).multi(TWO_TALL).inventory().support(Support.FLOOR)
                    .notes("Two-tall display shelf. The lower half owns the display BlockEntity, which always starts empty."),
            new Def(A+"beverage_cooler",storage,"retail").facing(H4).multi(COOLER).fixed("left_open","false","right_open","false").inventory().support(Support.FLOOR)
                    .notes("2 wide x 2 tall. Anchor = lower_left master (BlockEntity with the 60-slot display, starts empty); the right column is at facing.getCounterClockWise(). Doors start closed."),
            new Def(A+"chest_freezer",safe,"retail").facing(H4).multi(FREEZER).fixed("lid","closed").support(Support.FLOOR)
                    .notes("2 wide x 1 tall. Anchor = left master (BlockEntity); right part at facing.getCounterClockWise(). Lid starts closed."),
            new Def(A+"vending_machine",storage,"retail").facing(H4).multi(TWO_TALL).fixed("broken","false").inventory().support(Support.FLOOR)
                    .notes("Intact source state only (broken=false). Display slots start empty; damage belongs to the later world pass."),
            new Def(A+"checkout_counter",storage,"retail").facing(H4).connect("shape","north","east","south","west").inventory().support(Support.NONE)
                    .notes("Checkout Counter V1; facing = the customer side. shape (straight / outer / inner corners) and the side flags are computed from neighbours like stairs: place the line, the corners follow. 9-slot searchable container, starts empty."),
            new Def(A+"checkout_counter_display",storage,"retail").facing(H4).connect("shape","north","east","south","west").inventory().support(Support.NONE)
                    .notes("As checkout_counter, with impulse trays on the customer face of straight pieces."),
            new Def(A+"checkout_counter_gate",safe,"retail").facing(H4).variant("hinge","left","right").fixed("open","false").support(Support.NONE)
                    .notes("Pass-through gate in a counter line; facing = the customer side, hinge left = facing.getCounterClockWise(), where the flap folds onto the neighbouring counter. Starts closed."),
            new Def(A+"back_bar_shelf",storage,"retail").facing(H4).multi(TWO_TALL).inventory().support(Support.NONE)
                    .notes("Two-tall wall unit behind a checkout; facing = the front. The lower half owns the 12-slot searchable container, starts empty."),
            new Def(A+"storage_rack",storage,"retail").facing(H4).multi(TWO_TALL).connect("left","right").inventory().support(Support.NONE)
                    .notes("Two-tall boltless steel rack; facing = the front. left/right are computed from same-facing neighbours (shared uprights). The lower half owns the 12-slot searchable container, starts empty."),
            new Def(A+"cash_register",storage,"retail").facing(H4).fixed("open","false").inventory().support(Support.FLOOR)
                    .notes("Countertop POS (V2); facing = the operator side. 9-slot cash drawer, starts closed and empty; it slides 0.5 block out toward the operator when opened."),
            new Def(A+"commercial_glass_double_door",safe,"doors").facing(H4).multi(GLASS_DOOR).fixed("open","false").support(Support.FLOOR)
                    .notes("2 wide x 2 tall. Anchor = lower_left master (BlockEntity); the second leaf is at facing.getClockWise(), the opposite side from beverage_cooler. V2 (2026-10-07): the leaves swing out toward facing, about 0.72 block into the cells in front; for a storefront give facing = the outside, the frame then lines up with storefront_glazing of the same facing."),
            new Def(A+"commercial_glass_double_door_black",safe,"doors").facing(H4).multi(GLASS_DOOR).fixed("open","false").support(Support.FLOOR)
                    .notes("Black-anodised frame variant (Storefront Glazing V1); same block class, parts and BlockEntity as commercial_glass_double_door (V2: swings out toward facing). 2 wide x 2 tall, anchor = lower_left, second leaf at facing.getClockWise()."),
            new Def(A+"restroom_stall_door",safe,"doors").facing(H4).multi(TWO_TALL).variant("hinge","left","right").fixed("open","false").support(Support.FLOOR)
                    .notes("Lower half owns the BlockEntity. Adjacent restroom_partition door_support bits are reconciled automatically."),
            new Def(A+"steel_door",safe,"doors").facing(H4).multi(TWO_TALL).variant("hinge","left","right").fixed("open","false","powered","false").support(Support.FLOOR)
                    .notes("Steel-frame doors V1 (2026-10-07): the lower half owns a render-only BlockEntity. facing = the way the placer looked; the leaf hangs at the face toward the placer and swings out toward the placer, about 0.9 block into the cell in front."),
            new Def(A+"commercial_wood_door",safe,"doors").facing(H4).multi(TWO_TALL).variant("hinge","left","right").variant("style","plain","restroom","vision").fixed("open","false","powered","false").support(Support.FLOOR)
                    .notes("Steel-frame doors V1 (2026-10-07): steel frame, maple veneer leaf; the lower half owns a render-only BlockEntity. facing = the way the placer looked; the leaf hangs at the face toward the placer and swings away from it inside its own cell. style is appearance only (restroom: indicator and plaque; vision: a narrow lite)."),
            new Def(A+"industrial_locker",storage,"storage").facing(H4).multi(TWO_TALL).inventory().support(Support.FLOOR)
                    .notes("27-slot container on the lower half; must start and stay empty (no loot table)."),
            new Def(A+"lead_chest",storage,"storage").facing(H4).fixed("open","false").inventory().support(Support.NONE)
                    .notes("27-slot shielded box (V3); lid starts closed; must start and stay empty (no loot table)."),
            new Def("minecraft:chest",storage,"storage").facing(H4).fixed("type","single","waterlogged","false").inventory().support(Support.NONE)
                    .notes("Single chest only; double-chest pairing is not authored. Generic WorldEdit writes of chests stay forbidden."),
            new Def("minecraft:barrel",storage,"storage").facing(ALL6).fixed("open","false").inventory().support(Support.NONE),
            new Def(A+"modern_office_desk",safe,"office").facing(H4).multi(DESK).support(Support.FLOOR)
                    .notes("3 wide. Anchor = center; left at facing.getCounterClockWise(), right at facing.getClockWise()."),
            new Def(A+"modern_office_chair",safe,"office").facing(H4),
            new Def(A+"modern_lcd_monitor",safe,"office").facing(H4).connect("lowered").support(Support.FLOOR_OR_DESK),
            new Def(A+"office_computer_station",safe,"office").facing(H4).connect("lowered").support(Support.FLOOR_OR_DESK),
            new Def(A+"office_keyboard",safe,"office").facing(H4).connect("lowered").support(Support.FLOOR_OR_DESK),
            new Def(A+"office_mouse",safe,"office").facing(H4).connect("lowered").support(Support.FLOOR_OR_DESK),
            new Def(A+"low_filing_cabinet",safe,"office").facing(H4),
            new Def(A+"tall_filing_cabinet",safe,"office").facing(H4).multi(TWO_TALL).support(Support.FLOOR),
            new Def(A+"office_multifunction_printer",safe,"office").facing(H4).multi(TWO_TALL).support(Support.FLOOR),
            new Def(A+"office_cubicle_partition",safe,"office").connect("north","south","east","west").support(Support.FLOOR_CLEAR_ABOVE),
            new Def(A+"restroom_partition",safe,"restroom").connect("north","south","east","west","door_support").support(Support.FLOOR_CLEAR_ABOVE)
                    .notes("Connections and door_support are computed from neighbours; never author them by hand."),
            new Def(A+"commercial_flushometer_toilet",safe,"restroom").facing(H4),
            new Def(A+"commercial_wall_mounted_sink",safe,"restroom").facing(H4).support(Support.NONE)
                    .notes("Restroom Fixtures V2 (2026-10-08): one cell, rim 0.84 m. Engine needs no support; place it against a wall for the intended look."),
            new Def(A+"wall_mirror",safe,"restroom").facing(H4).support(Support.ATTACHED_OPPOSITE_FACING)
                    .notes("Restroom Fixtures V2 (2026-10-08): wall only (facing = away from the wall); hangs from its cell floor, so in the cell above a lavatory its bottom edge is 1.0 m up. Its BlockEntity holds no data (Mirror Reflection V1: it lists loaded mirrors on the client)."),
            new Def(A+"metal_trash_can",storage,"utility").facing(H4).fixed("open","false").inventory()
                    .notes("V2 (2026-10-02): 9-slot searchable container with a hinged lid; starts shut and empty."),
            dumpster("commercial_dumpster"),
            dumpster("commercial_dumpster_blue"),
            dumpster("commercial_dumpster_brown"),
            dumpster("commercial_dumpster_gray"),
            new Def(A+"fuel_island_curb",safe,"utility").facing(H4).support(Support.FLOOR)
                    .notes("Fuel island straight curb, 3 px high. Facing as the fuel dispenser's: the island runs along facing.getClockWise()."),
            new Def(A+"fuel_island_end",safe,"utility").facing(H4).support(Support.FLOOR)
                    .notes("Half-round island end; joining side facing.getCounterClockWise(), the round side facing.getClockWise()."),
            new Def(A+"fuel_island_bollard",safe,"utility").connect("on_curb").support(Support.BLOCK_RULE)
                    .notes("Steel bollard. On the ground, or in the cell above an island curb / end: on_curb is computed from the block below (2026-10-08; it was fixed false) and sinks it onto the curb top."),
            new Def(A+"fuel_canopy_column",safe,"utility").facing(H4).connect("segment","top").variant("island","false","true").support(Support.FLOOR_OR_COLUMN)
                    .notes("Canopy column segment; stack 5 from the ground (the canopy's soffit is 5 blocks up): a segment may stand on another (2026-10-08). segment (base: the lowest, power port on its bottom face) and top (head plate under a canopy piece) are computed. island=true: a base set in an island line in place of a straight curb (facing as that curb; it draws the curb itself), 2026-10-08."),
            new Def(A+"fuel_canopy_ceiling",safe,"utility").support(Support.NONE)
                    .notes("Plain canopy block (flat soffit below, roof above), one block thick. Carries the canopy wiring."),
            new Def(A+"fuel_canopy_light",safe,"utility").fixed("lit","false").support(Support.NONE)
                    .notes("Canopy block with a flush square LED light; lit is set by its network's powered column base."),
            new Def(A+"fuel_canopy_fascia",safe,"utility").facing(H4).connect("shape").support(Support.NONE)
                    .notes("Canopy edge, faded red; facing = outward. shape (straight / outer_left / outer_right) is computed like stairs: an outer corner when the fascia behind it faces one of its sides."),
            new Def(A+"fuel_dispenser",safe,"utility").facing(H4).multi(FUEL_DISPENSER)
                    .fixed("front_gasoline","true","front_diesel","true","back_gasoline","true","back_diesel","true","island","false","lit","false").support(Support.FLOOR)
                    .notes("2 wide x 3 tall, with its own island curb segment. Anchor = a0 (master, bottom); b column at facing.getClockWise(). Facing = the front customer face; both long faces have two nozzles."),
            new Def(A+"water_dispenser",safe,"utility").facing(H4).multi(TWO_TALL).support(Support.FLOOR),
            new Def(A+"industrial_utility_light",safe,"utility").facing(ATTACH5).fixed("lit","false").support(Support.ATTACHED_OPPOSITE_FACING)
                    .notes("Building Lights V1 (2026-10-08): the square LED panel light; ceiling (down), walls only for old buildings. lit is runtime (the building's lighting circuit)."),
            new Def(A+"linear_light",safe,"utility").facing(ATTACH5).variant("axis","x","z").connect("joined_neg","joined_pos").fixed("lit","false").support(Support.ATTACHED_OPPOSITE_FACING)
                    .notes("Building Lights V1 (2026-10-08): 1 m linear light; ceiling (down, the row along axis) or wall (along the wall). Neighbours of the same mounting join into a row; lit is runtime."),
            new Def(A+"emergency_light",safe,"utility").facing(H4).fixed("mode","off").support(Support.ATTACHED_OPPOSITE_FACING)
                    .notes("Building Lights V1 (2026-10-08): two-head battery unit, wall only (facing = away from the wall), 2.45 m up in its cell; a BlockEntity keeps the battery (starts flat)."),
            new Def(A+"distribution_panel",safe,"utility").facing(H4).fixed("open","false").support(Support.ATTACHED_OPPOSITE_FACING)
                    .notes("Building Power V1 (2026-10-07): hangs on the wall behind it; a BlockEntity keeps the main / branch breakers (a new panel starts with the main off). Ports: bottom face in, top face out."),
            new Def(A+"service_meter_box",safe,"utility").facing(H4).variant("on","true","false").support(Support.ATTACHED_OPPOSITE_FACING)
                    .notes("Building Power V1 (2026-10-07): the service entry on an outside wall; on = the disconnect. Bottom-face port; forwards to the panel of the building whose wall it hangs on."),
            new Def(A+"wall_outlet",safe,"utility").facing(H4).fixed("upper","false","lower","false").support(Support.ATTACHED_OPPOSITE_FACING)
                    .notes("Power Outlets V1 (2026-10-08): wall only, facing = away from the wall; plate 0.35 m up in its cell (in the cell above a counter: 1.35 m). upper / lower record plugs at runtime."),
            new Def(A+"power_strip_3",safe,"utility").facing(H4).connect("lowered").fixed("on","true","lit","false").support(Support.FLOOR_OR_DESK)
                    .notes("Power Outlets V1 (2026-10-08): floor or office desk; a BlockEntity keeps its plug, a placed strip lies unplugged beside its cord."),
            new Def(A+"power_strip_6",safe,"utility").facing(H4).connect("lowered").fixed("on","true","lit","false").support(Support.FLOOR_OR_DESK)
                    .notes("Power Outlets V1 (2026-10-08): the 2x3 strip; as power_strip_3."),
            new Def(A+"alloy_furnace",machine,"machine").notes("Processing machine with runtime state; not an authoring fixture."),
            new Def(A+"chemical_reactor",machine,"machine").notes("Processing machine with runtime state; not an authoring fixture."),
            new Def(A+"compressor",machine,"machine").notes("Processing machine with runtime state; not an authoring fixture."),
            new Def(A+"crusher",machine,"machine").notes("Processing machine with runtime state; not an authoring fixture."),
            new Def(A+"industrial_furnace",machine,"machine").notes("Processing machine with runtime state; not an authoring fixture."),
            new Def(A+"thermal_generator",machine,"machine").notes("Energy producer with fuel/fluid state; not an authoring fixture."),
            new Def(A+"submersible_fuel_pump",safe,"fuel").facing(H4).support(Support.NONE)
                    .notes("Fuel court (2026-10-08; was MACHINE): on the tank's port cell top (along 3, across 1, level 2 + 1). facing = the product outlet (AFL fluid port); the power port is the opposite face. Its fluid buffer and FE start empty."),
            new Def(A+"fuel_dispenser_sump",safe,"fuel").facing(H4).multi(FUEL_SUMP).support(Support.NONE)
                    .notes("Fuel court (2026-10-08; was MACHINE): 2 x 2 x 1 high under a fuel dispenser: anchor a0 = the dispenser's a0 two below, facing as the dispenser, b column at facing.getClockWise(). Fluid ports on the lower cells: gasoline on a0's facing.getCounterClockWise() face, diesel on b0's facing.getClockWise() face; power port on a0's back (facing.getOpposite())."),
            new Def(A+"underground_fuel_tank_gasoline",safe,"fuel").layout(FUEL_TANK,H4).support(Support.NONE)
                    .notes("Fuel court (2026-10-08): 3 x 3 x 7 tank, placed whole. anchor = bottom centre cell (along 3, across 1, level 0); facing = toward the fill end (along 6). Ports on top of the master (along 3, level 2) and of the fill cell (along 6, level 2). Starts empty."),
            new Def(A+"underground_fuel_tank_diesel",safe,"fuel").layout(FUEL_TANK,H4).support(Support.NONE)
                    .notes("As underground_fuel_tank_gasoline, holding diesel."),
            new Def(A+"pump_manhole_cover",safe,"fuel").facing(H4).fixed("open","false").support(Support.NONE)
                    .notes("Fuel court (2026-10-08): the surface-layer cover over a submersible pump; facing = hinge side (it opens away from facing). Starts shut."),
            new Def(A+"fuel_fill_cover_gasoline",safe,"fuel").facing(H4).fixed("open","false").support(Support.NONE)
                    .notes("Fuel court (2026-10-08): the surface-layer fill cover; its AFL fluid port is the bottom face (a pipe down to the tank's fill cell). Starts shut."),
            new Def(A+"fuel_fill_cover_diesel",safe,"fuel").facing(H4).fixed("open","false").support(Support.NONE)
                    .notes("As fuel_fill_cover_gasoline, for diesel."),
            new Def(A+"gun_maintenance_bench",machine,"workstation").facing(H4).multi(WORKSTATION).notes("Gameplay workstation holding a weapon slot."),
            new Def(A+"precision_fabrication_station",machine,"workstation").facing(H4).multi(WORKSTATION).notes("Gameplay crafting workstation."),
            new Def(A+"energy_cell",unsafe,"machine").notes("Energy-network storage."),
            new Def(A+"fluid_tank",unsafe,"machine").notes("Fluid contents and tank-stack connection state."));
        var map=new LinkedHashMap<String,Fixture>();for(var d:defs)map.put(d.id,d.build());return Collections.unmodifiableMap(map);
    }

    static Collection<Fixture> all(){return FIXTURES.values();}
    static String id(Block block){return BuiltInRegistries.BLOCK.getKey(block).toString();}
    static Fixture get(Block block){return FIXTURES.get(id(block));}
    static Block block(String id){
        var key=ResourceLocation.tryParse(id);
        if(key==null||!BuiltInRegistries.BLOCK.containsKey(key))throw new IllegalArgumentException("UNKNOWN_BLOCK: "+id);
        return BuiltInRegistries.BLOCK.get(key);
    }
    static Fixture require(String id){
        var f=FIXTURES.get(id);if(f!=null)return f;block(id);
        throw new IllegalArgumentException("FIXTURE_NOT_REGISTERED: "+id+" (ordinary blocks use we_set; see describe_block)");
    }
    static Multiblock multiblock(BlockState s){var f=get(s.getBlock());return f==null?null:f.multiblock();}
    /** A part of a multiblock or of a cell-layout structure: placed whole, never through WorldEdit materials. */
    static boolean structured(BlockState s){var f=get(s.getBlock());return f!=null&&f.structured();}
    /** Every part position of the multiblock that the given part belongs to, according to its own state. */
    static List<BlockPos> peers(BlockPos pos,BlockState s){
        var f=get(s.getBlock());
        if(f!=null&&f.layout()!=null)return List.copyOf(f.layout().cells(s.getBlock(),f.layout().anchorOf(pos,s),f.layout().facingOf(s)).keySet());
        if(f==null||f.multiblock()==null)return List.of(pos);var m=f.multiblock();
        var facing=Direction.byName(value(s,f.facingProperty()));var part=value(s,m.partProperty());
        if(facing==null||part==null)return List.of(pos);var anchor=m.anchorOf(pos,facing,part);
        return m.parts().stream().map(p->m.locator().locate(anchor,facing,p)).toList();
    }
    static boolean ownsBlockEntity(BlockState s){
        return OWNS_BLOCK_ENTITY.computeIfAbsent(s,state->state.getBlock() instanceof EntityBlock e&&e.newBlockEntity(BlockPos.ZERO,state)!=null);
    }
    /**
     * Only blocks whose updateShape is a pure connection function; others (doors, lights, AFL fixtures) may drop items or break.
     * Storage racks (2026-10-08): left / right follow the side neighbours and the BlockEntity stays (same block); an orphan half
     * would turn to air, which the callers report instead of writing. Without this a row placed one rack at a time kept the
     * earlier rack's side open (the Fuel Stop A1 stock room: right=false all along, a post and a shelf gap at every joint).
     * Curbs (2026-10-09): updateShape only adds an edge flag where a road surface is beside it, never removes or breaks.
     */
    static boolean shapeSafe(BlockState s){
        var b=s.getBlock();
        return b instanceof CrossCollisionBlock||b instanceof WallBlock||b instanceof StairBlock||b instanceof FenceGateBlock
                ||b instanceof OfficeCubiclePartitionBlock||b instanceof OfficeDesktopDecorationBlock||b instanceof ModernLcdMonitorBlock
                ||b instanceof CheckoutCounterBlock||b instanceof StorageRackBlock||b instanceof CurbBlock||facade(b);
    }
    /**
     * Fuel Stop A1 facade blocks (2026-10-07): their computed states only follow neighbours and never drop or break, so
     * reconcile_shapes may recompute them after WorldEdit writes. Glazing left/right/up/down (mullion and transom are kept);
     * masonry base cap/shape; cornice shape; wall panel cap and sides; jamb eyebrow; eyebrow canopy left/right (one pass
     * reads the old jamb state, so a jamb + canopy run needs a second reconcile_shapes).
     */
    private static boolean facade(Block b){
        return b instanceof StorefrontGlazingBlock||b instanceof MasonryBaseBlock||b instanceof AluminumCorniceBlock
                ||b instanceof MetalWallPanelBlock||b instanceof MetalPanelJambBlock||b instanceof MetalEyebrowCanopyBlock;
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    static List<String> valueNames(Property p){var r=new ArrayList<String>();for(Object v:p.getPossibleValues())r.add(p.getName((Comparable)v));return r;}
    @SuppressWarnings({"rawtypes","unchecked"})
    static String value(BlockState s,String name){
        if(name==null)return null;Property p=s.getBlock().getStateDefinition().getProperty(name);
        return p==null?null:p.getName(s.getValue(p));
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    static BlockState with(BlockState s,String name,String value){
        Property p=s.getBlock().getStateDefinition().getProperty(name);
        if(p==null)throw new IllegalArgumentException("INVALID_PROPERTY: "+name+" on "+id(s.getBlock()));
        Optional v=p.getValue(value);if(v.isEmpty())throw new IllegalArgumentException("INVALID_PROPERTY_VALUE: "+name+"="+value);
        return s.setValue(p,(Comparable)v.get());
    }
    /** Builds the exact target states from the fixture contract; no caller-supplied NBT or free properties. */
    static LinkedHashMap<BlockPos,BlockState> targets(Fixture f,BlockPos anchor,Direction facing,Map<String,String> variants){
        var block=block(f.id());var base=block.defaultBlockState();
        if(f.layout()!=null){
            if(facing==null)throw new IllegalArgumentException("FACING_REQUIRED: "+f.facings());
            if(!f.facings().contains(facing))throw new IllegalArgumentException("INVALID_FACING: "+facing.getName()+" allowed="+names(f.facings()));
            if(!variants.isEmpty())throw new IllegalArgumentException("PROPERTY_NOT_ALLOWED: "+variants.keySet()+" (cell-layout structures take only anchor and facing)");
            return f.layout().cells(block,anchor.immutable(),facing);
        }
        if(f.facingProperty()!=null){
            if(facing==null)throw new IllegalArgumentException("FACING_REQUIRED: "+f.facings());
            if(!f.facings().contains(facing))throw new IllegalArgumentException("INVALID_FACING: "+facing.getName()+" allowed="+names(f.facings()));
            base=with(base,f.facingProperty(),facing.getName());
        }else if(facing!=null)throw new IllegalArgumentException("INVALID_FACING: "+f.id()+" has no facing");
        for(var e:variants.entrySet()){
            var allowed=f.variants().get(e.getKey());
            if(allowed==null)throw new IllegalArgumentException("PROPERTY_NOT_ALLOWED: "+e.getKey()+" (variants="+f.variants().keySet()+")");
            if(!allowed.contains(e.getValue()))throw new IllegalArgumentException("INVALID_PROPERTY_VALUE: "+e.getKey()+"="+e.getValue()+" allowed="+allowed);
            base=with(base,e.getKey(),e.getValue());
        }
        for(var e:f.fixed().entrySet())base=with(base,e.getKey(),e.getValue());
        var result=new LinkedHashMap<BlockPos,BlockState>();
        if(f.multiblock()==null){result.put(anchor.immutable(),base);return result;}
        var m=f.multiblock();
        for(var part:m.parts())result.put(m.locator().locate(anchor,facing,part).immutable(),with(base,m.partProperty(),part));
        return result;
    }
    private static List<String> names(Collection<Direction> d){return d.stream().map(Direction::getName).sorted().toList();}

    // ---- Description / catalog -------------------------------------------------------------
    private static JsonArray array(Collection<String> values){var a=new JsonArray();values.forEach(a::add);return a;}
    @SuppressWarnings({"rawtypes","unchecked"})
    private static JsonArray properties(Block block,Fixture f){
        var result=new JsonArray();
        for(Property p:block.getStateDefinition().getProperties()){
            var o=new JsonObject();o.addProperty("name",p.getName());o.add("values",array(valueNames(p)));
            String role="FREE",value=null;Collection<String> allowed=null;
            if(f!=null){
                if(f.layout()!=null){role="LAYOUT_COMPUTED";}
                else if(p.getName().equals(f.facingProperty())){role="FACING";allowed=names(f.facings());}
                else if(f.multiblock()!=null&&p.getName().equals(f.multiblock().partProperty())){role="PART";allowed=f.multiblock().parts();}
                else if(f.variants().containsKey(p.getName())){role="VARIANT";allowed=new TreeSet<>(f.variants().get(p.getName()));}
                else if(f.fixed().containsKey(p.getName())){role="FIXED";value=f.fixed().get(p.getName());}
                else if(f.connections().contains(p.getName())){role="CONNECTION_COMPUTED";}
                else {role="DEFAULT";value=p.getName((Comparable)block.defaultBlockState().getValue(p));}
            }
            o.addProperty("role",role);if(allowed!=null)o.add("allowed",array(allowed));if(value!=null)o.addProperty("value",value);result.add(o);
        }
        return result;
    }
    private static JsonObject size(Multiblock m){
        int x0=0,x1=0,y0=0,y1=0,z0=0,z1=0;
        for(var part:m.parts()){var o=m.offset(Direction.NORTH,part);x0=Math.min(x0,o.getX());x1=Math.max(x1,o.getX());y0=Math.min(y0,o.getY());y1=Math.max(y1,o.getY());z0=Math.min(z0,o.getZ());z1=Math.max(z1,o.getZ());}
        return BridgeJson.object("width",x1-x0+1,"height",y1-y0+1,"depth",z1-z0+1,"frame","facing=north; width and depth swap for east/west");
    }
    static JsonObject describe(String id){
        var block=block(id);var f=get(block);boolean entity=block instanceof EntityBlock;
        String genericProblem=f!=null?null:AuthoringRegionGuard.materialProblem(block.defaultBlockState());
        var o=new JsonObject();o.addProperty("block_id",id(block));o.addProperty("has_block_entity",entity);o.addProperty("registered_fixture",f!=null);
        o.addProperty("authoring_class",f!=null?f.authoringClass().name():entity?AuthoringClass.UNSAFE.name():"GENERIC_BLOCK");
        o.addProperty("authoring_allowed",f!=null?f.allowed():genericProblem==null);
        o.addProperty("placement_tool",f!=null?f.placementTool():genericProblem==null?"we_set / we_batch_set":"NONE");
        if(f==null&&genericProblem!=null)o.addProperty("blocked_reason",genericProblem);
        o.addProperty("category",f!=null?f.category():null);
        o.add("properties",properties(block,f));
        o.add("allowed_facing",f!=null&&(f.facingProperty()!=null||f.layout()!=null)?array(names(f.facings())):new JsonArray());
        if(f!=null&&f.layout()!=null){var sz=f.layout().size();o.add("size",BridgeJson.object("width",sz[0],"height",sz[1],"depth",sz[2],"frame","facing=north; width and depth swap for east/west"));o.addProperty("cell_layout",true);}
        o.addProperty("has_inventory",f!=null&&f.hasInventory());
        o.addProperty("must_start_empty",true);
        var m=f==null?null:f.multiblock();o.addProperty("multiblock",m!=null);
        if(m!=null){
            o.add("size",size(m));o.addProperty("master_part",m.master());o.addProperty("part_property",m.partProperty());
            var parts=new JsonArray();var base=f.facingProperty()==null?block.defaultBlockState():with(block.defaultBlockState(),f.facingProperty(),"north");
            for(var part:m.parts()){
                var p=new JsonObject();p.addProperty("part",part);p.addProperty("block_entity_owner",ownsBlockEntity(with(base,m.partProperty(),part)));
                var offsets=new JsonObject();for(var facing:f.facings())offsets.add(facing.getName(),BridgeJson.GSON.toJsonTree(BridgeJson.xyz(m.offset(facing,part))));
                p.add("offset_from_anchor",offsets);parts.add(p);
            }
            o.add("parts",parts);
        }else o.add("parts",new JsonArray());
        if(f!=null&&m==null&&f.layout()==null)o.addProperty("block_entity_owner",ownsBlockEntity(f.facingProperty()==null?block.defaultBlockState():with(block.defaultBlockState(),f.facingProperty(),f.facings().iterator().next().getName())));
        o.addProperty("support_requirements",f!=null?f.support().text:"Validated only by audit_support (canSurvive).");
        o.addProperty("placement_notes",f!=null?(f.notes().isEmpty()?"-":f.notes()):entity?"Unregistered BlockEntity: not placeable and blocks region edits.":"Ordinary block: exact namespaced state through WorldEdit tools.");
        o.addProperty("neighbor_update_policy",f!=null?f.shapePolicy().name():"NONE");
        o.addProperty("shape_reconcile_safe",shapeSafe(block.defaultBlockState()));
        o.addProperty("raw_nbt_accepted",false);
        return o;
    }
    static JsonObject list(JsonObject a){
        String category=BridgeJson.string(a,"category","");boolean beOnly=BridgeJson.bool(a,"block_entity_only"),multiOnly=BridgeJson.bool(a,"multiblock_only"),blocked=BridgeJson.bool(a,"include_blocked");
        var fixtures=new JsonArray();var categories=new TreeSet<String>();
        for(var f:FIXTURES.values()){
            if(f.allowed()||blocked)categories.add(f.category());
            if(!f.allowed()&&!blocked)continue;if(!category.isEmpty()&&!category.equals(f.category()))continue;
            var block=block(f.id());boolean entity=block instanceof EntityBlock;
            if(beOnly&&!entity)continue;if(multiOnly&&f.multiblock()==null)continue;
            var o=BridgeJson.object("block_id",f.id(),"category",f.category(),"authoring_class",f.authoringClass().name(),"authoring_allowed",f.allowed(),
                    "placement_tool",f.placementTool(),"has_block_entity",entity,"has_inventory",f.hasInventory(),"multiblock",f.multiblock()!=null,
                    "parts",f.multiblock()==null?1:f.multiblock().parts().size(),"allowed_facing",names(f.facings()),"support",f.support().name());
            if(f.multiblock()!=null)o.add("size",size(f.multiblock()));
            fixtures.add(o);
        }
        return BridgeJson.object("fixtures",fixtures,"count",fixtures.size(),"categories",categories,"source","Java AuthoringFixtureRegistry (authoritative)");
    }
    static JsonObject snapshot(Block block){
        var f=get(block);var m=f==null?null:f.multiblock();
        var o=BridgeJson.object("authoring_allowed",f!=null?f.allowed():AuthoringRegionGuard.materialProblem(block.defaultBlockState())==null,
                "authoring_class",f!=null?f.authoringClass().name():block instanceof EntityBlock?"UNSAFE":"GENERIC_BLOCK",
                "fixture_category",f==null?null:f.category(),"multiblock",m!=null,"has_inventory",f!=null&&f.hasInventory(),
                "requires_support",f!=null&&f.support()!=Support.NONE,"placement_tool",f!=null?f.placementTool():"we_set");
        if(m!=null)o.add("multiblock_size",size(m));
        return o;
    }

    /** One cell now, with a 'half' property kept only so saved worlds load (the obsolete upper half removes itself). */
    private static final Set<String> LEGACY_HALF=Set.of("commercial_wall_mounted_sink");
    /** Contract self-check against the running registry; the GameTest requires an empty list. */
    static List<String> problems(){
        var problems=new ArrayList<String>();
        for(var f:FIXTURES.values()){
            try{
                var block=block(f.id());var s=block.defaultBlockState();
                if(f.facingProperty()!=null)for(var d:f.facings())with(s,f.facingProperty(),d.getName());
                for(var e:f.variants().entrySet())for(var v:e.getValue())with(s,e.getKey(),v);
                for(var e:f.fixed().entrySet())with(s,e.getKey(),e.getValue());
                for(var c:f.connections())if(block.getStateDefinition().getProperty(c)==null)problems.add(f.id()+": missing connection property "+c);
                if(f.hasInventory()&&!(block instanceof EntityBlock))problems.add(f.id()+": inventory declared without BlockEntity");
                var m=f.multiblock();
                if(m!=null){
                    var p=block.getStateDefinition().getProperty(m.partProperty());
                    if(p==null){problems.add(f.id()+": missing part property "+m.partProperty());continue;}
                    var partValues=new TreeSet<>(valueNames(p));
                    if(!partValues.equals(new TreeSet<>(m.parts())))problems.add(f.id()+": parts "+m.parts()+" != "+partValues);
                    for(var d:f.facings()){
                        if(!m.offset(d,m.master()).equals(BlockPos.ZERO))problems.add(f.id()+": master offset not zero for "+d);
                        if(new HashSet<>(m.parts().stream().map(part->m.offset(d,part)).toList()).size()!=m.parts().size())problems.add(f.id()+": duplicate part offsets for "+d);
                    }
                }
                var l=f.layout();
                if(l!=null)for(var d:f.facings()){
                    var cells=l.cells(block,BlockPos.ZERO,d);var sz=l.size();
                    if(cells.size()!=sz[0]*sz[1]*sz[2])problems.add(f.id()+": layout has "+cells.size()+" cells for "+d);
                    for(var c:cells.entrySet())if(!l.anchorOf(c.getKey(),c.getValue()).equals(BlockPos.ZERO)||l.facingOf(c.getValue())!=d){problems.add(f.id()+": layout cell "+c.getKey().toShortString()+" does not lead back to its anchor for "+d);break;}
                }
                if(block instanceof EntityBlock e&&f.facingProperty()!=null){
                    var owner=m==null?with(s,f.facingProperty(),f.facings().iterator().next().getName()):with(with(s,f.facingProperty(),f.facings().iterator().next().getName()),m.partProperty(),m.master());
                    var be=e.newBlockEntity(BlockPos.ZERO,owner);
                    if(be instanceof Container&&!f.hasInventory())problems.add(f.id()+": Container BlockEntity not declared as inventory");
                }
            }catch(RuntimeException ex){problems.add(f.id()+": "+ex.getMessage());}
        }
        for(var block:BuiltInRegistries.BLOCK){
            var key=BuiltInRegistries.BLOCK.getKey(block);if(!key.getNamespace().equals("apocalypse_firstlight"))continue;
            var half=block.getStateDefinition().getProperty("half");
            boolean multiblockShaped=block.getStateDefinition().getProperty("part")!=null||(half!=null&&valueNames(half).contains("lower"));
            if(block instanceof EntityBlock&&get(block)==null)problems.add(key+": unclassified BlockEntity block");
            if(multiblockShaped&&!LEGACY_HALF.contains(key.getPath())&&(get(block)==null||get(block).multiblock()==null))problems.add(key+": unclassified multi-part block");
        }
        return problems;
    }
    private AuthoringFixtureRegistry(){}
}
