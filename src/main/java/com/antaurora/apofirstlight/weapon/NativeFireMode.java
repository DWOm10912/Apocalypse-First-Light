package com.antaurora.apofirstlight.weapon;

import java.util.Locale;

public enum NativeFireMode {
    SEMI(0x9FC7D9), BURST(0xD6A15F), AUTO(0xD97878);
    public final int color;
    NativeFireMode(int color) { this.color=color; }
    public String key() { return name().toLowerCase(Locale.ROOT); }
    public static NativeFireMode parse(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
