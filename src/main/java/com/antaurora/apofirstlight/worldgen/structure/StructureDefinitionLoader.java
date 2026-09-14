package com.antaurora.apofirstlight.worldgen.structure;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import static com.antaurora.apofirstlight.worldgen.structure.StructureValidationIssue.*;

/**
 * Strict, explicit parser; NOT a resource reload listener. Resource keys come from
 * data/namespace/afl_worldgen/structures/path.json; NBT references resolve independently.
 * Enum spellings are case-sensitive UPPER_CASE. Unknown fields and duplicate keys are errors.
 * No generated sockets, default front/rotations/anchor, or partial collection publication.
 */
public final class StructureDefinitionLoader {
    private StructureDefinitionLoader() {}
    public record Loaded(Optional<StructureDefinition> definition, StructureValidationResult validation) {
        public Loaded {
            Objects.requireNonNull(definition); Objects.requireNonNull(validation);
            if (definition.isPresent()!=validation.valid()) throw new IllegalArgumentException("Definition present iff load is valid");
        }
    }
    public record Source(ResourceLocation id, String json) {
        public Source { Objects.requireNonNull(id); Objects.requireNonNull(json); }
    }
    public record Batch(Map<ResourceLocation,StructureDefinition> definitions, StructureValidationResult validation) {
        public Batch { definitions=Map.copyOf(definitions); Objects.requireNonNull(validation); }
    }
    public static Loaded parse(ResourceLocation id, String json) {
        Objects.requireNonNull(id);
        try {
            var j=object(parseJson(json),"$");
            fields(j,Set.of("schema_version","asset_revision","structure_nbt","category","front",
                    "ground_anchor_offset_y","allowed_rotations","sockets","tags"),"$");
            int schema=integer(j,"schema_version");
            if (schema!=1) throw new Invalid(Code.UNSUPPORTED_SCHEMA,"schema_version","Only schema 1 is supported");
            var sockets=new ArrayList<StructureSocket>();
            var names=new HashSet<String>();
            for (var element : array(j,"sockets")) {
                var s=object(element,"sockets[]");
                fields(s,Set.of("name","position","facing","type"),"sockets[]");
                String name=string(s,"name");
                if (!names.add(name)) throw new Invalid(Code.DUPLICATE_SOCKET_NAME,"sockets."+name,"Repeated name");
                var pos=array(s,"position");
                if (pos.size()!=3) throw new Invalid(Code.INVALID_FIELD,"position","Expected [x,y,z]");
                sockets.add(new StructureSocket(name,new BlockPos(integer(pos.get(0),"position[0]"),
                        integer(pos.get(1),"position[1]"),integer(pos.get(2),"position[2]")),
                        enumeration(Direction.class,string(s,"facing"),"facing"),
                        enumeration(StructureSocketType.class,string(s,"type"),"type")));
            }
            var tags=new HashSet<ResourceLocation>();
            for (var tag : array(j,"tags")) {
                if (!tags.add(identifier(text(tag,"tags[]"),"tags[]")))
                    throw new Invalid(Code.DUPLICATE_FIELD,"tags","Repeated tag");
            }
            var definition=new StructureDefinition(id,identifier(string(j,"structure_nbt"),"structure_nbt"),
                    identifier(string(j,"category"),"category"),enumeration(Direction.class,string(j,"front"),"front"),
                    integer(j,"ground_anchor_offset_y"),rotations(j),sockets,tags,string(j,"asset_revision"),schema);
            return new Loaded(Optional.of(definition),new StructureValidationResult(List.of()));
        } catch (Invalid invalid) {
            return new Loaded(Optional.empty(),new StructureValidationResult(List.of(invalid.issue())));
        }
    }
    /** Validate an already-resolved read-only NBT inspection; caller owns resource resolution. */
    public static Loaded validate(ResourceLocation id,String json,StructureNbtReader.Inspection nbt) {
        var parsed=parse(id,json);
        if (parsed.definition().isEmpty()) return parsed;
        var report=StructureDefinitionValidator.validate(parsed.definition().orElseThrow(),nbt);
        return new Loaded(report.valid()?parsed.definition():Optional.empty(),report);
    }
    /** List input deliberately retains duplicate IDs that a Map would hide. Any error rejects the batch. */
    public static Batch parseAll(List<Source> sources) {
        var definitions=new LinkedHashMap<ResourceLocation,StructureDefinition>();
        var issues=new ArrayList<StructureValidationIssue>();
        var ids=new HashSet<ResourceLocation>();
        for (var source : sources) {
            if (!ids.add(source.id())) {
                issues.add(error(Code.DUPLICATE_METADATA_ID,source.id().toString(),"Duplicate metadata resource ID"));
                continue;
            }
            var loaded=parse(source.id(),source.json());
            issues.addAll(loaded.validation().issues());
            loaded.definition().ifPresent(d->definitions.put(d.id(),d));
        }
        var result=new StructureValidationResult(issues);
        return new Batch(result.valid()?definitions:Map.of(),result);
    }
    static Set<Rotation> rotations(JsonObject object) {
        var values=EnumSet.noneOf(Rotation.class);
        for (var element : array(object,"allowed_rotations"))
            if (!values.add(enumeration(Rotation.class,text(element,"allowed_rotations[]"),"allowed_rotations")))
                throw new Invalid(Code.DUPLICATE_FIELD,"allowed_rotations","Repeated rotation");
        return values;
    }
    static JsonElement parseJson(String text) {
        Objects.requireNonNull(text);
        if (text.length()>1_048_576) throw new Invalid(Code.INVALID_FIELD,"$","Metadata exceeds 1 Mi characters");
        try (var reader=new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JsonElement value=read(reader,0);
            if (reader.peek()!=JsonToken.END_DOCUMENT) throw new Invalid(Code.INVALID_FIELD,"$","Trailing JSON content");
            return value;
        } catch (IOException | NumberFormatException invalid) {
            throw new Invalid(Code.INVALID_FIELD,"$",invalid.toString());
        }
    }
    private static JsonElement read(JsonReader r,int depth) throws IOException {
        if (depth>32) throw new Invalid(Code.INVALID_FIELD,r.getPath(),"JSON nesting exceeds 32");
        return switch(r.peek()) {
            case BEGIN_OBJECT -> {
                var object=new JsonObject(); r.beginObject();
                while(r.hasNext()) {
                    String name=r.nextName();
                    if(object.has(name)) throw new Invalid(Code.DUPLICATE_FIELD,r.getPath(),"Duplicate key "+name);
                    object.add(name,read(r,depth+1));
                }
                r.endObject(); yield object;
            }
            case BEGIN_ARRAY -> {
                var array=new JsonArray(); r.beginArray();
                while(r.hasNext()) array.add(read(r,depth+1));
                r.endArray(); yield array;
            }
            case STRING -> new JsonPrimitive(r.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(r.nextString()));
            case BOOLEAN -> new JsonPrimitive(r.nextBoolean());
            case NULL -> { r.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new Invalid(Code.INVALID_FIELD,r.getPath(),"Expected JSON value");
        };
    }
    static void fields(JsonObject j,Set<String> allowed,String path) {
        for (String key : j.keySet()) if (!allowed.contains(key))
            throw new Invalid(Code.INVALID_FIELD,path+"."+key,"Unknown field (policy fields do not belong to an asset)");
    }
    static JsonElement required(JsonObject j,String key) {
        if (!j.has(key) || j.get(key).isJsonNull()) throw new Invalid(Code.MISSING_FIELD,key,"Required field");
        return j.get(key);
    }
    static JsonObject object(JsonElement e,String path) {
        if (!e.isJsonObject()) throw new Invalid(Code.INVALID_FIELD,path,"Expected object");
        return e.getAsJsonObject();
    }
    static JsonArray array(JsonObject j,String key) {
        JsonElement e=required(j,key);
        if (!e.isJsonArray()) throw new Invalid(Code.INVALID_FIELD,key,"Expected array");
        return e.getAsJsonArray();
    }
    static String text(JsonElement e,String path) {
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString())
            throw new Invalid(Code.INVALID_FIELD,path,"Expected string");
        return e.getAsString();
    }
    static String string(JsonObject j,String key) { return text(required(j,key),key); }
    static int integer(JsonObject j,String key) { return integer(required(j,key),key); }
    static int integer(JsonElement e,String path) {
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())
            throw new Invalid(Code.INVALID_FIELD,path,"Expected integer number");
        try { return e.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException invalid) { throw new Invalid(Code.INVALID_FIELD,path,"Integer out of range or fractional"); }
    }
    static boolean bool(JsonObject j,String key) {
        JsonElement e=required(j,key);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean())
            throw new Invalid(Code.INVALID_FIELD,key,"Expected boolean");
        return e.getAsBoolean();
    }
    static ResourceLocation identifier(String value,String path) {
        // Require explicit namespace; Vanilla's implicit minecraft fallback is not an authoring decision.
        if (!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || value.startsWith(".:") || value.startsWith("..:")
                || Arrays.asList(value.substring(value.indexOf(':')+1).split("/",-1)).stream()
                .anyMatch(s->s.isEmpty()||s.equals(".")||s.equals("..")))
            throw new Invalid(Code.INVALID_FIELD,path,"Expected safe explicit namespace:path");
        ResourceLocation id=ResourceLocation.tryParse(value);
        if(id==null) throw new Invalid(Code.INVALID_FIELD,path,"Invalid resource identifier");
        return id;
    }
    static <E extends Enum<E>> E enumeration(Class<E> type,String value,String path) {
        try { return Enum.valueOf(type,value); }
        catch(IllegalArgumentException invalid) { throw new Invalid(Code.INVALID_FIELD,path,"Unknown case-sensitive "+type.getSimpleName()+": "+value); }
    }
}
