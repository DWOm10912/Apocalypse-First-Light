package com.antaurora.apofirstlight.weapon;

/** Generic presentation presets, never inferred from a weapon ID. */
public enum NativeHitEffect {
    NONE(""), DIZZY_STARS("dizzy_stars");
    private final String id;
    NativeHitEffect(String id) { this.id=id; }
    public String id() { return id; }
    public String onHit(boolean entityHit) { return entityHit?id:""; }
    public static NativeHitEffect parse(String id) {
        if(id==null || id.isBlank()) return NONE;
        for(var value:values()) if(value.id.equals(id))return value;
        throw new IllegalArgumentException("presentation.hit_effect: unknown preset " + id);
    }
}
