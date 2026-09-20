package com.antaurora.apofirstlight.tooltip;

/** Fixed V1 semantic colors; display metadata only. */
public enum AflTooltipStatType {
    WEAPON_TYPE("weapon_type",0xAAB8C2), DAMAGE("damage",0xD97878), AMMUNITION("ammunition",0xD6B46A),
    FIRE_MODE("fire_mode",0xD79A68),
    RANGE("range",0x82B98A), RECOIL("recoil",0xC49567),
    SOUND_RADIUS("sound_radius",0xB0B0B0);
    private final String key;private final int color;
    AflTooltipStatType(String key,int color){this.key="tooltip.apocalypse_firstlight.stat."+key;this.color=color;}
    public String key(){return key;}
    public int color(){return color;}
}
