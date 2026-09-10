package com.antaurora.apofirstlight.authoring;

import com.google.gson.*;
import net.minecraft.world.level.block.Rotation;
import java.util.*;

/** Asset contract only; this is not a worldgen pool or runtime metadata loader. */
public record BuildingMetadata(String id, int width, int depth, int height, int surfaceOffset,
        Category category, Set<Zone> zones, boolean roadFacing, boolean damageCompatible) {
    public enum Category { HIGHRISE_OFFICE, HIGHRISE_APARTMENT, MIDRISE_OFFICE, MIDRISE_APARTMENT,
        COMMERCIAL, RESIDENTIAL, INDUSTRIAL, WAREHOUSE, UTILITY, FILLER, SPECIAL_POI }
    public enum Zone { CORE, MIXED, COMMERCIAL, RESIDENTIAL, INDUSTRIAL, EDGE }
    public BuildingMetadata {
        if(id==null || !id.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("ID: lowercase letters, digits, underscore; max 64");
        if(width<1||depth<1||height<2||width>128||depth>128||height>192)
            throw new IllegalArgumentException("Bounds: width/depth 1..128, height 2..192");
        if(surfaceOffset<0||surfaceOffset>=height) throw new IllegalArgumentException("surface_offset_y must be 0..height-1");
        Objects.requireNonNull(category); zones=Set.copyOf(zones);
        if(zones.isEmpty()) throw new IllegalArgumentException("At least one zone required");
    }
    public JsonObject json() {
        var j=new JsonObject();j.addProperty("id",id);j.addProperty("structure","apocalypse_firstlight:"+id);
        j.addProperty("category",category.name());var f=new JsonObject();f.addProperty("width",width);f.addProperty("depth",depth);
        j.add("footprint",f);j.addProperty("height",height);j.addProperty("front","SOUTH");j.addProperty("surface_offset_y",surfaceOffset);
        var r=new JsonArray();for(var rot:Rotation.values())r.add(rot.name());j.add("allowed_rotations",r);
        j.addProperty("road_facing",roadFacing);var z=new JsonArray();zones.stream().sorted().forEach(v->z.add(v.name()));j.add("city_zones",z);
        j.addProperty("damage_compatible",damageCompatible);j.addProperty("loot_ready",false);j.addProperty("authoring_version",1);return j;
    }
}
