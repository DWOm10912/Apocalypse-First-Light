package com.antaurora.apofirstlight.tooltip;

/** Fixed V1 semantic colors; display metadata only. */
public enum AflTooltipStatType {
    DAMAGE("damage",0xD97878), AMMUNITION("ammunition",0xD6B46A),
    MAGAZINE("magazine",0x79AFC9), FIRE_MODE("fire_mode",0xD79A68),
    RANGE("range",0x82B98A), RECOIL("recoil",0xC49567),
    NOISE("noise",0x9B8FC3), ADS("ads",0x79AAA7);
    private final String key;private final int color;
    AflTooltipStatType(String key,int color){this.key="tooltip.apocalypse_firstlight.stat."+key;this.color=color;}
    public String key(){return key;}
    public int color(){return color;}
}
