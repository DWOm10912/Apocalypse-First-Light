package com.antaurora.apofirstlight.worldgen.structure;

import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import com.antaurora.apofirstlight.worldgen.spatial.Bounds3i;
import static com.antaurora.apofirstlight.worldgen.structure.StructureValidationIssue.Code.*;

/** Mechanical checks only. Uses real Vanilla coordinate APIs, never a world or block registry. */
public final class StructureContractTest {
    private static int checks;
    private static String json;
    private static final ResourceLocation ID=new ResourceLocation("test","metadata");
    static void require(boolean value,String message) {
        checks++;
        if(!value) throw new AssertionError(message);
    }
    private static void rejects(Class<? extends Throwable> type,Runnable action) {
        checks++;
        try { action.run(); }
        catch(Throwable error) {
            if(type.isInstance(error)) return;
            throw new AssertionError("Expected "+type,error);
        }
        throw new AssertionError("Expected "+type);
    }
    private static boolean has(StructureValidationResult report,StructureValidationIssue.Code code) {
        return report.issues().stream().anyMatch(i->i.code()==code);
    }
    static ListTag ints(int... values) {
        var result=new ListTag();
        for(int value:values) result.add(IntTag.valueOf(value));
        return result;
    }
    static CompoundTag fixture() {
        var root=new CompoundTag();
        root.put("size",ints(3,4,5));
        var palette=new ListTag();
        var air=new CompoundTag(); air.putString("Name","minecraft:air"); palette.add(air);
        var stone=new CompoundTag(); stone.putString("Name","minecraft:stone"); palette.add(stone);
        root.put("palette",palette);
        var blocks=new ListTag();
        var cell=new CompoundTag(); cell.put("pos",ints(0,0,0)); cell.putInt("state",0); blocks.add(cell);
        root.put("blocks",blocks); root.put("entities",new ListTag());
        return root;
    }
    private static StructureDefinition base() {
        return StructureDefinitionLoader.parse(ID,json).definition().orElseThrow();
    }
    private static StructureDefinition with(int anchor,List<StructureSocket> sockets) {
        var d=base();
        return new StructureDefinition(d.id(),d.structureNbt(),d.category(),d.front(),anchor,
                d.allowedRotations(),sockets,d.tags(),d.assetRevision(),1);
    }
    private static StructureValidationResult validate(int anchor,List<StructureSocket> sockets) {
        return StructureDefinitionValidator.validate(with(anchor,sockets),StructureNbtReader.inspect(fixture()));
    }
    private static StructureSocket socket(int x,int y,int z,Direction facing) {
        return new StructureSocket("main",new BlockPos(x,y,z),facing,StructureSocketType.DRIVEWAY);
    }
    private static void values() {
        var d=base();
        require(!d.id().equals(d.structureNbt()),"Metadata and NBT identities distinct");
        for(int field=0;field<8;field++) {
            int f=field;
            rejects(NullPointerException.class,()->new StructureDefinition(f==0?null:d.id(),f==1?null:d.structureNbt(),
                    f==2?null:d.category(),f==3?null:d.front(),1,f==4?null:d.allowedRotations(),
                    f==5?null:d.sockets(),f==6?null:d.tags(),f==7?null:d.assetRevision(),1));
        }
        for(String revision:List.of(""," ","\n")) rejects(IllegalArgumentException.class,()->new StructureDefinition(
                d.id(),d.structureNbt(),d.category(),d.front(),1,d.allowedRotations(),d.sockets(),d.tags(),revision,1));
        for(int schema:new int[]{-1,0,2,Integer.MAX_VALUE}) rejects(IllegalArgumentException.class,()->new StructureDefinition(
                d.id(),d.structureNbt(),d.category(),d.front(),1,d.allowedRotations(),d.sockets(),d.tags(),"v",schema));
        for(Direction dir:List.of(Direction.UP,Direction.DOWN)) {
            rejects(IllegalArgumentException.class,()->new StructureDefinition(d.id(),d.structureNbt(),d.category(),dir,1,
                    d.allowedRotations(),d.sockets(),d.tags(),"v",1));
            rejects(IllegalArgumentException.class,()->socket(1,1,1,dir));
            rejects(IllegalArgumentException.class,()->StructureTransform.facing(dir,Rotation.NONE));
        }
        rejects(IllegalArgumentException.class,()->new StructureDefinition(d.id(),d.structureNbt(),d.category(),d.front(),1,
                Set.of(),d.sockets(),d.tags(),"v",1));
        var mutable=new BlockPos.MutableBlockPos(1,1,4);
        var socket=new StructureSocket("main",mutable,Direction.SOUTH,StructureSocketType.PEDESTRIAN);
        mutable.set(2,2,2);
        require(socket.localPosition().equals(new BlockPos(1,1,4)),"Socket freezes MutableBlockPos");
        var rotations=new HashSet<>(d.allowedRotations());
        var tags=new HashSet<>(d.tags());
        var sockets=new ArrayList<>(d.sockets());
        var copied=new StructureDefinition(d.id(),d.structureNbt(),d.category(),d.front(),1,rotations,sockets,tags,"v",1);
        rotations.clear(); tags.clear(); sockets.clear();
        require(copied.allowedRotations().size()==4 && copied.tags().size()==1 && copied.sockets().size()==1,"Defensive copies");
        rejects(UnsupportedOperationException.class,()->copied.allowedRotations().clear());
        rejects(UnsupportedOperationException.class,()->copied.tags().clear());
        rejects(UnsupportedOperationException.class,()->copied.sockets().clear());
        require(has(validate(1,List.of(socket,socket)),DUPLICATE_SOCKET_NAME),"Duplicate socket via record caught by validator");
        for(int anchor:new int[]{0,1,4}) require(validate(anchor,List.of()).valid(),"Inclusive anchor endpoints");
        for(int anchor:new int[]{-1,5,Integer.MAX_VALUE}) require(has(validate(anchor,List.of()),INVALID_GROUND_ANCHOR),"Bad anchor");
        require(StructureTransform.originY(64,1)==63 && StructureTransform.originY(-60,4)==-64,"Surface formula");
        rejects(ArithmeticException.class,()->StructureTransform.originY(Integer.MIN_VALUE,1));
        require(Arrays.toString(StructureSocketType.values()).equals("[PEDESTRIAN, DRIVEWAY, ROAD, SERVICE_ROAD]"),"Socket types");
    }
    private static void sockets() {
        for(var s:List.of(socket(1,1,0,Direction.NORTH),socket(1,1,4,Direction.SOUTH),
                socket(0,1,2,Direction.WEST),socket(2,1,2,Direction.EAST),
                socket(0,1,0,Direction.NORTH),socket(0,1,0,Direction.WEST)))
            require(validate(1,List.of(s)).valid(),"Outward edge/corner");
        for(var s:List.of(socket(-1,1,0,Direction.NORTH),socket(3,1,0,Direction.NORTH),
                socket(1,-1,0,Direction.NORTH),socket(1,4,0,Direction.NORTH),
                socket(1,1,-1,Direction.NORTH),socket(1,1,5,Direction.SOUTH)))
            require(has(validate(1,List.of(s)),SOCKET_OUT_OF_BOUNDS),"Each outside axis");
        for(var s:List.of(socket(1,1,0,Direction.SOUTH),socket(1,1,4,Direction.NORTH),
                socket(0,1,2,Direction.EAST),socket(2,1,2,Direction.WEST)))
            require(has(validate(1,List.of(s)),SOCKET_FACING_INWARD),"Inward edge");
        require(has(validate(1,List.of(socket(1,1,2,Direction.SOUTH))),SOCKET_NOT_ON_BOUNDARY),"Interior socket");
    }
    private static void transforms() {
        Vec3i size=new Vec3i(3,4,5);
        for(var origin:List.of(BlockPos.ZERO,new BlockPos(-100,-60,-200))) for(var rotation:Rotation.values()) {
            var settings=new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(rotation);
            require(settings.getRotationPivot().equals(BlockPos.ZERO),"Default Vanilla pivot");
            var bounds=StructureTransform.bounds(size,rotation,origin);
            require(bounds.equals(VanillaBoundsAdapter.fromVanilla(TemplateFixture.box(size,settings,origin))),"Actual Vanilla template bounds API");
            Set<BlockPos> transformed=new HashSet<>();
            for(int x=0;x<3;x++) for(int y=0;y<4;y++) for(int z=0;z<5;z++) {
                var p=new BlockPos(x,y,z);
                var local=StructureTransform.local(p,rotation);
                require(local.equals(StructureTemplate.calculateRelativePosition(settings,p)),"Vanilla settings position");
                var expected=switch(rotation) {
                    case NONE -> p;
                    case CLOCKWISE_90 -> new BlockPos(-z,y,x);
                    case CLOCKWISE_180 -> new BlockPos(-x,y,-z);
                    case COUNTERCLOCKWISE_90 -> new BlockPos(z,y,-x);
                };
                require(local.equals(expected),"Known zero-pivot block transform");
                var world=StructureTransform.world(p,rotation,origin);
                require(world.equals(expected.offset(origin)),"Negative world origin");
                require(bounds.contains(world.getX(),world.getY(),world.getZ()),"Rotated bounds contain cells");
                transformed.add(world);
                var inverse=switch(rotation) {
                    case NONE -> Rotation.NONE;
                    case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
                    case CLOCKWISE_180 -> Rotation.CLOCKWISE_180;
                    case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
                };
                require(StructureTransform.local(local,inverse).equals(p),"Round trip");
            }
            require(transformed.size()==60,"Rotation bijection");
            boolean swap=rotation==Rotation.CLOCKWISE_90||rotation==Rotation.COUNTERCLOCKWISE_90;
            require(bounds.width()==(swap?5:3) && bounds.depth()==(swap?3:5) && bounds.height()==4,"Non-square dimension swap");
            for(Direction front:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST))
                require(StructureTransform.facing(front,rotation)==rotation.rotate(front),"Front and socket facing");
        }
        require(StructureTransform.facing(Direction.SOUTH,Rotation.CLOCKWISE_90)==Direction.WEST,"SOUTH CW90 WEST");
        for(Rotation r:List.of(Rotation.CLOCKWISE_90,Rotation.CLOCKWISE_180))
            rejects(ArithmeticException.class,()->StructureTransform.local(new BlockPos(0,0,Integer.MIN_VALUE),r));
        for(Rotation r:List.of(Rotation.COUNTERCLOCKWISE_90,Rotation.CLOCKWISE_180))
            rejects(ArithmeticException.class,()->StructureTransform.local(new BlockPos(Integer.MIN_VALUE,0,0),r));
        rejects(ArithmeticException.class,()->StructureTransform.world(new BlockPos(1,0,0),Rotation.NONE,new BlockPos(Integer.MAX_VALUE,0,0)));
        rejects(ArithmeticException.class,()->StructureTransform.bounds(size,Rotation.NONE,new BlockPos(Integer.MAX_VALUE,0,0)));
        rejects(IllegalArgumentException.class,()->StructureTransform.bounds(new Vec3i(0,4,5),Rotation.NONE,BlockPos.ZERO));
        var box=new BoundingBox(-8,-60,-9,3,4,5);
        var shared=VanillaBoundsAdapter.fromVanilla(box);
        require(shared.equals(new Bounds3i(-8,-60,-9,4,5,6)),"Inclusive max +1");
        require(VanillaBoundsAdapter.fromVanilla(VanillaBoundsAdapter.toVanilla(shared)).equals(shared),"Bounds roundtrip");
        rejects(IllegalArgumentException.class,()->VanillaBoundsAdapter.toVanilla(new Bounds3i(0,0,0,0,2,3)));
        for(var b:List.of(new BoundingBox(0,0,0,Integer.MAX_VALUE,1,1),new BoundingBox(0,0,0,1,Integer.MAX_VALUE,1),
                new BoundingBox(0,0,0,1,1,Integer.MAX_VALUE)))
            rejects(ArithmeticException.class,()->VanillaBoundsAdapter.fromVanilla(b));
    }
    private static final class TemplateFixture extends StructureTemplate {
        static BoundingBox box(Vec3i size,StructurePlaceSettings settings,BlockPos origin) {
            return getBoundingBox(origin,settings.getRotation(),settings.getRotationPivot(),settings.getMirror(),size);
        }
    }
    private static void loader() {
        require(StructureDefinitionLoader.parse(ID,json).validation().valid(),"Valid JSON");
        for(String field:List.of("schema_version","asset_revision","structure_nbt","category","front",
                "ground_anchor_offset_y","allowed_rotations","sockets","tags")) {
            var missing=JsonParser.parseString(json).getAsJsonObject(); missing.remove(field);
            require(has(StructureDefinitionLoader.parse(ID,missing.toString()).validation(),MISSING_FIELD),"Missing "+field);
        }
        require(has(StructureDefinitionLoader.parse(ID,json.replace("\"schema_version\": 1","\"schema_version\": 2")).validation(),UNSUPPORTED_SCHEMA),"Unsupported schema");
        for(String bad:List.of("south","SIDEWAYS","SOUTH ")) require(!StructureDefinitionLoader.parse(ID,json.replace("\"SOUTH\"","\""+bad+"\"")).validation().valid(),"Bad enum");
        for(String bad:List.of("Test:BAD","missing_namespace","test:../escape","test:/absolute","test:double//slash","..:escape"))
            require(!StructureDefinitionLoader.parse(ID,json.replace("test:rectangular",bad)).validation().valid(),"Bad id");
        for(String bad:List.of("{}",json+" true",json.replace("\"schema_version\": 1","\"schema_version\": 1.5"),
                json.replace("\"schema_version\": 1","\"schema_version\": 2147483648"),
                json.replace("\"schema_version\": 1","\"schema_version\": \"1\""),
                json.replace("\"schema_version\": 1","\"schema_version\": 1, \"schema_version\": 1"),
                json.replace("\"schema_version\": 1","\"weight\": 10, \"schema_version\": 1"),
                json.replace("\"name\": \"main\"","\"name\": \"main\", \"name\": \"other\""),
                json.replace("[1, 1, 4]","[1, 1]"),json.replace("\"tags\": [\"test:fixture\"]","\"tags\": null")))
            require(!StructureDefinitionLoader.parse(ID,bad).validation().valid(),"Strict parser rejection");
        var duplicate=JsonParser.parseString(json).getAsJsonObject();
        duplicate.getAsJsonArray("sockets").add(duplicate.getAsJsonArray("sockets").get(0).deepCopy());
        require(has(StructureDefinitionLoader.parse(ID,duplicate.toString()).validation(),DUPLICATE_SOCKET_NAME),"Duplicate socket JSON");
        var batch=StructureDefinitionLoader.parseAll(List.of(new StructureDefinitionLoader.Source(ID,json),new StructureDefinitionLoader.Source(ID,json)));
        require(has(batch.validation(),DUPLICATE_METADATA_ID)&&batch.definitions().isEmpty(),"Duplicate ID rejects whole batch");
        var validBatch=StructureDefinitionLoader.parseAll(List.of(new StructureDefinitionLoader.Source(ID,json)));
        require(validBatch.validation().valid()&&validBatch.definitions().size()==1,"Valid batch");
        rejects(UnsupportedOperationException.class,()->validBatch.definitions().clear());
        var missing=StructureNbtReader.read(java.nio.file.Path.of("src/test/resources/worldgen/structure/missing.nbt"),1024);
        require(has(StructureDefinitionLoader.validate(ID,json,missing).validation(),MISSING_NBT),"Missing NBT diagnostic");
    }
    private static void nbt() {
        var root=fixture();
        var info=StructureNbtReader.inspect(root);
        require(info.validation().valid()&&info.info().orElseThrow().localBounds().equals(new Bounds3i(0,0,0,3,4,5)),"Real size, no tight cropping");
        require(info.info().orElseThrow().explicitAirCount()==1,"Explicit air");
        var cell=root.getList("blocks",Tag.TAG_COMPOUND).getCompound(0);
        cell.put("nbt",new CompoundTag());
        require(has(StructureNbtReader.inspect(root).validation(),BE_REQUIRES_GAME_QA),"BE warning");
        require(StructureNbtReader.inspect(root).validation().status()==StructureValidationResult.Status.VALID_WITH_WARNINGS,"BE not invalid");
        root=fixture(); root.put("size",ints(3,0,5));
        require(has(StructureNbtReader.inspect(root).validation(),INVALID_NBT_SIZE),"Zero size");
        root=fixture(); root.put("size",ints(3,4));
        require(has(StructureNbtReader.inspect(root).validation(),INVALID_NBT_SIZE),"Wrong size type/length");
        root=fixture(); root.getList("blocks",Tag.TAG_COMPOUND).getCompound(0).put("pos",ints(3,0,0));
        require(has(StructureNbtReader.inspect(root).validation(),INVALID_NBT_DATA),"NBT block outside size");
        root=fixture(); root.getList("blocks",Tag.TAG_COMPOUND).getCompound(0).putInt("state",99);
        require(has(StructureNbtReader.inspect(root).validation(),INVALID_NBT_DATA),"Bad palette index");
        root=fixture(); root.getList("palette",Tag.TAG_COMPOUND).getCompound(0).putString("Name","minecraft:jigsaw");
        require(has(StructureNbtReader.inspect(root).validation(),FORBIDDEN_NBT_CONTENT),"Control block");
        root=fixture(); var props=new CompoundTag(); props.putString("half","upper");
        root.getList("palette",Tag.TAG_COMPOUND).getCompound(0).put("Properties",props);
        require(has(StructureNbtReader.inspect(root).validation(),MULTIBLOCK_REQUIRES_GAME_QA),"Pair-state QA warning");
        root=fixture();
        var palettes=new ListTag(); palettes.add(root.getList("palette",Tag.TAG_COMPOUND).copy());
        var alternative=root.getList("palette",Tag.TAG_COMPOUND).copy();
        alternative.getCompound(0).putString("Name","minecraft:stone"); palettes.add(alternative);
        root.remove("palette"); root.put("palettes",palettes);
        require(StructureNbtReader.inspect(root).info().orElseThrow().explicitAirCount()==0,"Alternative palettes conservative air");
        require(StructureNbtReader.inspect(root).info().orElseThrow().nonAir().contains(BlockPos.ZERO),"Alternative occupied");
        var sourceIssues=new ArrayList<StructureValidationIssue>();
        sourceIssues.add(StructureValidationIssue.warning(BE_REQUIRES_GAME_QA,"fixture","test"));
        var copiedReport=new StructureValidationResult(sourceIssues); sourceIssues.clear();
        require(copiedReport.status()==StructureValidationResult.Status.VALID_WITH_WARNINGS,"Immutable report snapshot");
        rejects(UnsupportedOperationException.class,()->copiedReport.issues().clear());
        root=fixture(); root.getList("blocks",Tag.TAG_COMPOUND).add(root.getList("blocks",Tag.TAG_COMPOUND).getCompound(0).copy());
        require(has(StructureNbtReader.inspect(root).validation(),INVALID_NBT_DATA),"Duplicate NBT coordinate");
        root=fixture(); root.getList("entities",Tag.TAG_COMPOUND).add(new CompoundTag());
        require(has(StructureNbtReader.inspect(root).validation(),FORBIDDEN_NBT_CONTENT),"No entity import");
        root=fixture();
        var blocked=root.getList("blocks",Tag.TAG_COMPOUND).getCompound(0);
        blocked.put("pos",ints(1,1,4)); blocked.putInt("state",1);
        require(!StructureDefinitionValidator.validate(base(),StructureNbtReader.inspect(root)).valid(),"Occupied socket entrance rejected");
        rejects(IllegalArgumentException.class,()->StructureNbtReader.read(java.nio.file.Path.of("unused"),0));
    }
    private static String legacy() {
        return """
                {"id":"old","structure":"test:rectangular","category":"MIDRISE_OFFICE",
                 "footprint":{"width":3,"depth":5},"height":4,"front":"SOUTH","surface_offset_y":1,
                 "allowed_rotations":["NONE","CLOCKWISE_90","CLOCKWISE_180","COUNTERCLOCKWISE_90"],
                 "road_facing":true,"city_zones":["CORE"],"authoring_version":1,"damage_compatible":false}
                """;
    }
    private static void legacyMapping() {
        var nbt=StructureNbtReader.inspect(fixture());
        var confirmed=LegacyStructureAuthoringAdapter.OffsetSemantics.CONFIRMED_FIRST_CELL_ABOVE_GROUND;
        var category=new ResourceLocation("test","commercial");
        var mapped=LegacyStructureAuthoringAdapter.map(ID,legacy(),category,"audit-v1",confirmed,nbt);
        require(mapped.validation().valid(),"Matching legacy dimensions");
        require(mapped.definition().orElseThrow().sockets().isEmpty(),"road_facing never synthesizes socket");
        require(mapped.definition().orElseThrow().tags().isEmpty(),"City zones not tags");
        require(mapped.fieldDisposition().get("city_zones").equals("BELONGS_TO_CITY_POLICY"),"City policy disposition");
        require(has(mapped.validation(),MISSING_REQUIRED_SOCKET_INFORMATION),"Missing entrance coordinates warning");
        require(mapped.definition().orElseThrow().groundAnchorOffsetY()==1,"Confirmed offset");
        var uncertain=LegacyStructureAuthoringAdapter.map(ID,legacy(),category,"audit-v1",LegacyStructureAuthoringAdapter.OffsetSemantics.UNCONFIRMED,nbt);
        require(uncertain.definition().isEmpty()&&has(uncertain.validation(),LEGACY_OFFSET_REQUIRES_CONFIRMATION),"Unknown offset cannot publish definition");
        var mismatch=LegacyStructureAuthoringAdapter.map(ID,legacy().replace("\"width\":3","\"width\":4"),category,"audit-v1",confirmed,nbt);
        require(mismatch.definition().isEmpty()&&has(mismatch.validation(),LEGACY_DIMENSION_MISMATCH),"Legacy footprint assertion");
        mismatch=LegacyStructureAuthoringAdapter.map(ID,legacy().replace("\"height\":4","\"height\":5"),category,"audit-v1",confirmed,nbt);
        require(has(mismatch.validation(),LEGACY_DIMENSION_MISMATCH),"Legacy height assertion");
    }
    public static void main(String[] args) {
        try(var input=StructureContractTest.class.getResourceAsStream("/worldgen/structure/valid.json")) {
            json=new String(Objects.requireNonNull(input).readAllBytes(),StandardCharsets.UTF_8);
        } catch(java.io.IOException error) { throw new AssertionError(error); }
        values(); sockets(); transforms(); loader(); nbt(); legacyMapping();
        System.out.println("PASS WG-03 structure checks="+checks+"; GAME_QA=NOT_PERFORMED");
        RuralAssetScanTest.main(args);
    }
}
