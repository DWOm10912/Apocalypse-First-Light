package com.antaurora.apofirstlight.dev.highwaymesh;

import com.google.gson.*;
import java.io.*;
import java.util.*;

/** Single section contract; all widths are metres. Extras are TOTAL widths, never per-side. */
public record RoadSection(String id,int lanes,double laneWidth,double innerShoulder,double outerShoulder,
                          double median,String medianType,double formationExtra,double constructionExtra,
                          double protectionExtra,double crossfall) {
    public RoadSection {
        if(id==null||medianType==null||lanes<2||lanes>3)throw new IllegalArgumentException("SECTION_ID_OR_LANES");
        for(double n:new double[]{laneWidth,innerShoulder,outerShoulder,median,formationExtra,constructionExtra,protectionExtra,crossfall})
            if(!Double.isFinite(n)||n<0)throw new IllegalArgumentException("NON_FINITE_OR_NEGATIVE_SECTION");
        if(laneWidth<3||laneWidth>4.5||median<1||crossfall>.04||formationExtra>constructionExtra||constructionExtra>protectionExtra||2*(lanes*laneWidth+innerShoulder+outerShoulder)+median>56)
            throw new IllegalArgumentException("SECTION_LIMIT");
    }
    public double carriageway(){return lanes*laneWidth;}
    public double paved(){return 2*(carriageway()+innerShoulder+outerShoulder);}
    public double width(){return paved()+median;}
    public double formation(){return width()+formationExtra;}
    public double construction(){return width()+constructionExtra;}
    public double protection(){return width()+protectionExtra;}
    public static RoadSection decode(JsonObject j) {
        return new RoadSection(j.get("id").getAsString(),j.get("lanes").getAsInt(),j.get("laneWidth").getAsDouble(),
            j.get("innerShoulder").getAsDouble(),j.get("outerShoulder").getAsDouble(),j.get("median").getAsDouble(),j.get("medianType").getAsString(),
            j.get("formationExtra").getAsDouble(),j.get("constructionExtra").getAsDouble(),j.get("protectionExtra").getAsDouble(),j.get("crossfall").getAsDouble());
    }
    public static Map<String,RoadSection> read(Reader reader) {
        JsonObject json=JsonParser.parseReader(reader).getAsJsonObject();
        if(json.get("version").getAsInt()!=1)throw new IllegalArgumentException("SECTION_VERSION");
        Map<String,RoadSection> out=new TreeMap<>();
        for(String key:List.of("rural4","suburban6")){RoadSection s=decode(json.getAsJsonObject(key));if(!key.equals(s.id()))throw new IllegalArgumentException("SECTION_KEY");out.put(key,s);}
        return Map.copyOf(out);
    }
}