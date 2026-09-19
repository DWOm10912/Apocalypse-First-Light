package com.antaurora.apofirstlight.weapon;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;

/** Ordered supported modes, independent of WeaponClass. */
public record NativeFireProfile(List<NativeFireMode> modes, NativeFireMode defaultMode, int burstCount) {
    public NativeFireProfile {
        modes=List.copyOf(modes);
        if(modes.isEmpty() || new HashSet<>(modes).size()!=modes.size() || !modes.contains(defaultMode))
            throw new IllegalArgumentException("fire: empty/duplicate modes or unsupported default_mode");
        if(burstCount<2 || burstCount>32)throw new IllegalArgumentException("fire.burst_count: expected integer 2..32");
    }
    public static NativeFireProfile parse(JsonObject fire) {
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
        return new NativeFireProfile(modes,initial,(int)count);
    }
}
