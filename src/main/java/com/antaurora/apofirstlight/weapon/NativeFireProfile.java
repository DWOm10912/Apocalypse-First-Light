package com.antaurora.apofirstlight.weapon;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Ordered supported modes, independent of WeaponClass. Optional per-mode overrides (fire.mode_overrides.<mode>)
 * change only that mode's shot interval, recoil fields and sustained-fire spread bloom; everything else is shared. */
public record NativeFireProfile(List<NativeFireMode> modes, NativeFireMode defaultMode, int burstCount,
                                Map<NativeFireMode, ModeOverride> overrides) {
    /** Extra spread half-angle added per accepted shot, capped, decaying exponentially (time constant in server ticks). */
    public record Bloom(double perShotDegrees, double maxDegrees, double recoveryTicks) {
        public Bloom {
            if(!Double.isFinite(perShotDegrees)||!Double.isFinite(maxDegrees)||!Double.isFinite(recoveryTicks)
                    ||perShotDegrees<0||maxDegrees<0||maxDegrees>45||recoveryTicks<=0||recoveryTicks>1200)
                throw new IllegalArgumentException("fire.mode_overrides.bloom: invalid values");
        }
    }
    /** Null members inherit the gun-wide value (interval_ticks, recoil) or mean none (bloom). */
    public record ModeOverride(Integer intervalTicks, NativeRecoilProfile recoil, Bloom bloom) {}
    public NativeFireProfile {
        modes=List.copyOf(modes);
        overrides=Map.copyOf(overrides);
        if(modes.isEmpty() || new HashSet<>(modes).size()!=modes.size() || !modes.contains(defaultMode))
            throw new IllegalArgumentException("fire: empty/duplicate modes or unsupported default_mode");
        if(burstCount<2 || burstCount>32)throw new IllegalArgumentException("fire.burst_count: expected integer 2..32");
        if(!modes.containsAll(overrides.keySet()))throw new IllegalArgumentException("fire.mode_overrides: mode not in modes");
    }
    public NativeFireProfile(List<NativeFireMode> modes, NativeFireMode defaultMode, int burstCount) {
        this(modes,defaultMode,burstCount,Map.of());
    }
    public int intervalTicks(NativeFireMode mode, int gunInterval) {
        var o=overrides.get(mode);return o==null||o.intervalTicks()==null?gunInterval:o.intervalTicks();
    }
    public NativeRecoilProfile recoil(NativeFireMode mode, NativeRecoilProfile gunRecoil) {
        var o=overrides.get(mode);return o==null||o.recoil()==null?gunRecoil:o.recoil();
    }
    public Bloom bloom(NativeFireMode mode) {
        var o=overrides.get(mode);return o==null?null:o.bloom();
    }
    public static NativeFireProfile parse(JsonObject fire) { return parse(fire,null); }
    /** recoilPatch merges a partial recoil object over the gun's recoil block; null rejects mode recoil. */
    public static NativeFireProfile parse(JsonObject fire, Function<JsonObject,NativeRecoilProfile> recoilPatch) {
        var modes=new ArrayList<NativeFireMode>();
        if(fire.has("modes")) {
            if(fire.has("mode"))throw new IllegalArgumentException("fire: use modes or legacy mode, not both");
            for(var mode:fire.getAsJsonArray("modes"))modes.add(NativeFireMode.parse(mode.getAsString()));
        } else modes.add(NativeFireMode.parse(fire.get("mode").getAsString()));
        var initial=fire.has("default_mode")?NativeFireMode.parse(fire.get("default_mode").getAsString())
                :modes.isEmpty()?null:modes.get(0);
        if(fire.has("burst_count")&&(!fire.get("burst_count").isJsonPrimitive()
                ||!fire.getAsJsonPrimitive("burst_count").isNumber()))
            throw new IllegalArgumentException("fire.burst_count: expected number");
        double count=fire.has("burst_count")?fire.get("burst_count").getAsDouble():3;
        if(!Double.isFinite(count)||count!=Math.rint(count)||count<2||count>32)
            throw new IllegalArgumentException("fire.burst_count: expected integer 2..32");
        var overrides=new HashMap<NativeFireMode,ModeOverride>();
        if(fire.has("mode_overrides"))for(var e:fire.getAsJsonObject("mode_overrides").entrySet()) {
            var mode=NativeFireMode.parse(e.getKey());
            var o=e.getValue().getAsJsonObject();
            for(String key:o.keySet())if(!Set.of("interval_ticks","recoil","bloom").contains(key))
                throw new IllegalArgumentException("fire.mode_overrides."+e.getKey()+": unknown key "+key);
            Integer interval=null;
            if(o.has("interval_ticks")) {
                double v=o.get("interval_ticks").getAsDouble();
                if(!Double.isFinite(v)||v!=Math.rint(v)||v<1||v>200)
                    throw new IllegalArgumentException("fire.mode_overrides.interval_ticks: expected integer 1..200");
                interval=(int)v;
            }
            NativeRecoilProfile recoil=null;
            if(o.has("recoil")) {
                if(recoilPatch==null)throw new IllegalArgumentException("fire.mode_overrides.recoil: no gun recoil to patch");
                recoil=recoilPatch.apply(o.getAsJsonObject("recoil"));
            }
            Bloom bloom=null;
            if(o.has("bloom")) {
                var b=o.getAsJsonObject("bloom");
                bloom=new Bloom(b.get("per_shot_degrees").getAsDouble(),b.get("max_degrees").getAsDouble(),b.get("recovery_ticks").getAsDouble());
            }
            if(overrides.put(mode,new ModeOverride(interval,recoil,bloom))!=null)
                throw new IllegalArgumentException("fire.mode_overrides: duplicate "+e.getKey());
        }
        return new NativeFireProfile(modes,initial,(int)count,overrides);
    }
}
