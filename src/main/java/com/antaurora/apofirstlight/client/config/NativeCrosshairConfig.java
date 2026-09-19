package com.antaurora.apofirstlight.client.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Visual GUI-pixel parameters only; never weapon accuracy data. */
public record NativeCrosshairConfig(double lineLength, double thickness, double baseGap, double maxGap,
        double spreadScale, double movementGap, double sprintGap, double airborneGap, double ladderGap,
        double waterGap, double shotImpulse, double maxBloom, double recoverySpeed, double responseSpeed,
        int color, double alpha, boolean outline, double sprintAlpha) {
    public static NativeCrosshairConfig defaults() { return parse("{}"); }
    public static NativeCrosshairConfig parse(String json) {
        JsonObject o;
        try { o = JsonParser.parseString(json).getAsJsonObject(); }
        catch (RuntimeException e) { o = new JsonObject(); }
        return new NativeCrosshairConfig(n(o,"line_length",4,1,12), n(o,"thickness",1,.5,3),
            n(o,"base_gap",3,1,16), n(o,"max_gap",24,16,64), n(o,"spread_scale",3,0,12),
            n(o,"movement_gap",1.5,0,12), n(o,"sprint_gap",3,0,16), n(o,"airborne_gap",3,0,16),
            n(o,"ladder_gap",5,0,16), n(o,"water_gap",5,0,16), n(o,"shot_impulse",2.5,0,16),
            n(o,"max_bloom",8,0,24), n(o,"recovery_speed",9,.1,40), n(o,"response_speed",14,.1,40),
            color(o), n(o,"alpha",.9,0,1), bool(o,"outline",true), n(o,"sprint_alpha",.45,0,1));
    }
    private static double n(JsonObject o,String key,double fallback,double min,double max) {
        try {
            if (!o.get(key).getAsJsonPrimitive().isNumber()) return fallback;
            double v=o.get(key).getAsDouble();
            return Double.isFinite(v)&&v>=min&&v<=max?v:fallback;
        } catch(RuntimeException e) { return fallback; }
    }
    private static boolean bool(JsonObject o,String key,boolean fallback) {
        try { return o.get(key).getAsJsonPrimitive().isBoolean()?o.get(key).getAsBoolean():fallback; }
        catch(RuntimeException e) { return fallback; }
    }
    private static int color(JsonObject o) {
        try { String s=o.get("color").getAsString();
            return s.matches("#[0-9a-fA-F]{6}")?Integer.parseInt(s.substring(1),16):0xE8E8E2;
        } catch(RuntimeException e) { return 0xE8E8E2; }
    }
}
