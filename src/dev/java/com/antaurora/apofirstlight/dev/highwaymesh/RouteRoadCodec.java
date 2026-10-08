package com.antaurora.apofirstlight.dev.highwaymesh;

import com.google.gson.*;
import java.util.*;

/** Explicit record constructors for Forge's Gson 2.9.x. No reflective final-field writes or dependency upgrade. */
public final class RouteRoadCodec {
    public static RouteRoadGeometry.Plan decode(String text){
        if(text.length()>32768)throw new IllegalArgumentException("RECIPE_BUDGET");
        JsonObject j=JsonParser.parseString(text).getAsJsonObject();
        List<RoadAlignment.Point> points=new ArrayList<>();
        if(j.getAsJsonArray("controls").size()>128||j.getAsJsonArray("corridors").size()>128)throw new IllegalArgumentException("CONTROL_BUDGET");
        for(var value:j.getAsJsonArray("controls"))points.add(point(value.getAsJsonObject()));
        List<RoadAlignment.Corridor> corridors=new ArrayList<>();
        for(var value:j.getAsJsonArray("corridors")){var c=value.getAsJsonObject();corridors.add(new RoadAlignment.Corridor(point(c.getAsJsonObject("a")),point(c.getAsJsonObject("b")),number(c,"halfWidth")));}
        List<String> notes=new ArrayList<>();for(var note:j.getAsJsonArray("limitations"))notes.add(note.getAsString());
        return new RouteRoadGeometry.Plan(j.get("version").getAsInt(),j.get("seed").getAsLong(),j.get("graphVersion").getAsInt(),
            j.get("routeId").getAsString(),j.get("edgeId").getAsString(),j.get("startNode").getAsString(),j.get("endNode").getAsString(),
            number(j,"authorityStart"),points,corridors,number(j,"start"),number(j,"length"),RoadSection.decode(j.getAsJsonObject("from")),RoadSection.decode(j.getAsJsonObject("to")),
            number(j,"transitionStart"),number(j,"transitionLength"),number(j,"profileSpan"),number(j,"grade"),notes);
    }
    private static RoadAlignment.Point point(JsonObject p){return new RoadAlignment.Point(number(p,"x"),number(p,"z"));}
    private static double number(JsonObject j,String key){double n=j.get(key).getAsDouble();if(!Double.isFinite(n))throw new IllegalArgumentException("NON_FINITE_"+key);return n;}
    private RouteRoadCodec(){}
}