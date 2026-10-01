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
        ATTACHED_OPPOSITE_FACING("The block on the side opposite `facing` needs a sturdy face toward the fixture; facing=down hangs under a ceiling."),
        NONE("No engine support requirement."),
        BLOCK_RULE("Checked with the block's own canSurvive after placement; no simplified rule is published.");
        final String text;
        Support(String text){this.text=text;}
    }
    /** SELF_AND_NEIGHBORS: the fixture's own connection properties are computed from its neighbours. */
    enum ShapePolicy { NEIGHBORS, SELF_AND_NEIGHBORS }
    interface PartLocator { BlockPos locate(BlockPos anchor, Direction facing, String part); }
    /** Parts are listed in placement order; the first entry is the master/anchor part. */
    record Multiblock(String partProperty, List<String> parts, PartLocator locator) {
        String master(){return parts.get(0);}
        BlockPos offset(Direction facing,String part){return locator.locate(BlockPos.ZERO,facing,part);}
        BlockPos anchorOf(BlockPos pos,Direction facing,String part){return pos.subtract(offset(facing,part));}
    }
    record Fixture(String id, AuthoringClass authoringClass, String category, String facingProperty, Set<Direction> facings,
                   Map<String,Set<String>> variants, Map<String,String> fixed, Set<String> connections, boolean hasInventory,
                   Support support, ShapePolicy shapePolicy, Multiblock multiblock, String notes) {
        boolean allowed(){return authoringClass==AuthoringClass.SAFE_FIXTURE||authoringClass==AuthoringClass.STORAGE_WITH_INVENTORY;}
        String placementTool(){return !allowed()?"NONE":multiblock!=null?"place_multiblock":"place_fixture";}
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
        ShapePolicy shape=ShapePolicy.NEIGHBORS;Multiblock multi;String notes="";
        Def(String id,AuthoringClass c,String category){this.id=id;this.c=c;this.category=category;}
        Def facing(Set<Direction> f){facing="facing";facings=f;return this;}
        Def multi(Multiblock m){multi=m;return this;}
        Def variant(String p,String... v){variants.put(p,Set.of(v));return this;}
        Def fixed(String... pairs){for(int i=0;i<pairs.length;i+=2)fixed.put(pairs[i],pairs[i+1]);return this;}
        Def connect(String... p){connections.addAll(List.of(p));shape=ShapePolicy.SELF_AND_NEIGHBORS;return this;}
        Def inventory(){inventory=true;return this;}
        Def support(Support s){support=s;return this;}
        Def notes(String n){notes=n;return this;}
        Fixture build(){return new Fixture(id,c,category,facing,Set.copyOf(facings),Map.copyOf(variants),Map.copyOf(fixed),Set.copyOf(connections),inventory,support,shape,multi,notes);}
    }
    private static Map<String,Fixture> build(){
        var safe=AuthoringClass.SAFE_FIXTURE;var storage=AuthoringClass.STORAGE_WITH_INVENTORY;var machine=AuthoringClass.MACHINE;var unsafe=AuthoringClass.UNSAFE;
        var defs=List.of(
            new Def(A+"retail_shelf_single",storage,"retail").facing(H4).multi(TWO_TALL).inventory().support(Support.FLOOR)
                    .notes("Two-tall display shelf. The lower half owns the display BlockEntity, which always starts empty."),
            new Def(A+"beverage_cooler",safe,"retail").facing(H4).multi(COOLER).fixed("left_open","false","right_open","false").support(Support.FLOOR)
                    .notes("2 wide x 2 tall. Anchor = lower_left master (BlockEntity); the right column is at facing.getCounterClockWise(). Doors start closed."),
            new Def(A+"chest_freezer",safe,"retail").facing(H4).multi(FREEZER).fixed("lid","closed").support(Support.FLOOR)
                    .notes("2 wide x 1 tall. Anchor = left master (BlockEntity); right part at facing.getCounterClockWise(). Lid starts closed."),
            new Def(A+"vending_machine",storage,"retail").facing(H4).multi(TWO_TALL).fixed("broken","false").inventory().support(Support.FLOOR)
                    .notes("Intact source state only (broken=false). Display slots start empty; damage belongs to the later world pass."),
            new Def(A+"cash_register",storage,"retail").facing(H4).fixed("open","false").inventory().support(Support.FLOOR)
                    .notes("Countertop POS (V2); facing = the operator side. 9-slot cash drawer, starts closed and empty; it slides 0.5 block out toward the operator when opened."),
            new Def(A+"commercial_glass_double_door",safe,"doors").facing(H4).multi(GLASS_DOOR).fixed("open","false").support(Support.FLOOR)
                    .notes("2 wide x 2 tall. Anchor = lower_left master (BlockEntity); the second leaf is at facing.getClockWise(), the opposite side from beverage_cooler."),
            new Def(A+"restroom_stall_door",safe,"doors").facing(H4).multi(TWO_TALL).variant("hinge","left","right").fixed("open","false").support(Support.FLOOR)
                    .notes("Lower half owns the BlockEntity. Adjacent restroom_partition door_support bits are reconciled automatically."),
            new Def(A+"steel_door",safe,"doors").facing(H4).multi(TWO_TALL).variant("hinge","left","right").fixed("open","false","powered","false").support(Support.FLOOR),
            new Def(A+"poplar_door",safe,"doors").facing(H4).multi(TWO_TALL).variant("hinge","left","right").fixed("open","false","powered","false").support(Support.FLOOR),
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
            new Def(A+"commercial_wall_mounted_sink",safe,"restroom").facing(H4).multi(TWO_TALL).support(Support.NONE)
                    .notes("Engine needs no support; place it against a wall for the intended look."),
            new Def(A+"metal_trash_can",safe,"utility"),
            new Def(A+"commercial_dumpster",safe,"utility").facing(H4).multi(DUMPSTER).support(Support.FLOOR)
                    .notes("2 wide. Anchor = master; secondary at facing.getClockWise()."),
            new Def(A+"water_dispenser",safe,"utility").facing(H4).multi(TWO_TALL).support(Support.FLOOR),
            new Def(A+"industrial_utility_light",safe,"utility").facing(ATTACH5).support(Support.ATTACHED_OPPOSITE_FACING),
            new Def(A+"industrial_electrical_box",storage,"utility").facing(H4).fixed("open","false","locked","true").inventory().support(Support.ATTACHED_OPPOSITE_FACING)
                    .notes("Wall-mounted only (V2, no ceiling mount); facing = the door side. 9-slot container, door starts closed, locked and empty."),
            new Def(A+"alloy_furnace",machine,"machine").notes("Processing machine with runtime state; not an authoring fixture."),
            new Def(A+"chemical_reactor",machine,"machine").notes("Processing machine with runtime state; not an authoring fixture."),
            new Def(A+"compressor",machine,"machine").notes("Processing machine with runtime state; not an authoring fixture."),
            new Def(A+"crusher",machine,"machine").notes("Processing machine with runtime state; not an authoring fixture."),
            new Def(A+"industrial_furnace",machine,"machine").notes("Processing machine with runtime state; not an authoring fixture."),
            new Def(A+"thermal_generator",machine,"machine").notes("Energy producer with fuel/fluid state; not an authoring fixture."),
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
    /** Every part position of the multiblock that the given part belongs to, according to its own state. */
    static List<BlockPos> peers(BlockPos pos,BlockState s){
        var f=get(s.getBlock());if(f==null||f.multiblock()==null)return List.of(pos);var m=f.multiblock();
        var facing=Direction.byName(value(s,f.facingProperty()));var part=value(s,m.partProperty());
        if(facing==null||part==null)return List.of(pos);var anchor=m.anchorOf(pos,facing,part);
        return m.parts().stream().map(p->m.locator().locate(anchor,facing,p)).toList();
    }
    static boolean ownsBlockEntity(BlockState s){
        return OWNS_BLOCK_ENTITY.computeIfAbsent(s,state->state.getBlock() instanceof EntityBlock e&&e.newBlockEntity(BlockPos.ZERO,state)!=null);
    }
    /** Only blocks whose updateShape is a pure connection function; others (doors, lights, AFL fixtures) may drop items or break. */
    static boolean shapeSafe(BlockState s){
        var b=s.getBlock();
        return b instanceof CrossCollisionBlock||b instanceof WallBlock||b instanceof StairBlock||b instanceof FenceGateBlock
                ||b instanceof OfficeCubiclePartitionBlock||b instanceof OfficeDesktopDecorationBlock||b instanceof ModernLcdMonitorBlock;
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
                if(p.getName().equals(f.facingProperty())){role="FACING";allowed=names(f.facings());}
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
        o.add("allowed_facing",f!=null&&f.facingProperty()!=null?array(names(f.facings())):new JsonArray());
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
        if(f!=null&&m==null)o.addProperty("block_entity_owner",ownsBlockEntity(f.facingProperty()==null?block.defaultBlockState():with(block.defaultBlockState(),f.facingProperty(),f.facings().iterator().next().getName())));
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
            if(multiblockShaped&&(get(block)==null||get(block).multiblock()==null))problems.add(key+": unclassified multi-part block");
        }
        return problems;
    }
    private AuthoringFixtureRegistry(){}
}
