package com.antaurora.apofirstlight.interaction;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** One packaged source controls both server deadlines and the independent FP clip sampler. */
public final class CrowbarSmashTimeline {
    private static final JsonObject DATA=read();
    public static final int DURATION=DATA.getAsJsonObject("afl_timing").get("duration_ticks").getAsInt();
    public static final int IMPACT=DATA.getAsJsonObject("afl_timing").get("impact_tick").getAsInt();
    private static JsonObject read() {
        try(var in=CrowbarSmashTimeline.class.getResourceAsStream("/assets/apocalypse_firstlight/animations/crowbar_first_person.animation.json")) {
            if(in==null)throw new IllegalStateException("Missing crowbar FP clip");
            return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        } catch(Exception e){throw new IllegalStateException("Invalid crowbar FP timeline",e);}
    }
    public static float[] sample(String bone,String channel,double ticks) {
        var tracks=DATA.getAsJsonObject("animations").getAsJsonObject("smash_glass").getAsJsonObject("bones").getAsJsonObject(bone);
        if(tracks==null||!tracks.has(channel))return new float[3];
        double seconds=Math.max(0,Math.min(DURATION,ticks))/20;
        JsonArray previous=null;double time=0;
        for(var key:tracks.getAsJsonObject(channel).entrySet()) {
            double next=Double.parseDouble(key.getKey());var value=key.getValue().getAsJsonObject().getAsJsonArray("vector");
            if(seconds<=next){
                double alpha=previous==null?1:(seconds-time)/(next-time);float[] result=new float[3];
                for(int i=0;i<3;i++)result[i]=(float)(previous==null?value.get(i).getAsDouble():previous.get(i).getAsDouble()+(value.get(i).getAsDouble()-previous.get(i).getAsDouble())*alpha);
                return result;
            }
            previous=value;time=next;
        }
        return new float[]{previous.get(0).getAsFloat(),previous.get(1).getAsFloat(),previous.get(2).getAsFloat()};
    }
    private CrowbarSmashTimeline(){}
}
