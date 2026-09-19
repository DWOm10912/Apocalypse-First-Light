package com.antaurora.apofirstlight.client.hudlayout;

/** Client-only built-in adapter registration; future JSON HUDs add an adapter here. */
public final class HudLayouts {
    public static final HudLayoutRegistry REGISTRY=new HudLayoutRegistry();
    static {
        REGISTRY.register(new NativeGunHudLayoutAdapter());
        REGISTRY.register(new GeigerHudLayoutAdapter());
    }
    private HudLayouts() {}
}
