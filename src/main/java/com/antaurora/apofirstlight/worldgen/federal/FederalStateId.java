package com.antaurora.apofirstlight.worldgen.federal;

/**
 * The six states of the federal national map (docs/worldgen/federal_six_state_c1.md). Stable, neutral internal ids:
 * saves, signs, broadcasts, structures and the spawn refer to a state by {@link #key()} or {@link #index()}, never by
 * its name. Names and abbreviations are display text from the language files ({@link #nameKey()},
 * {@link #abbreviationKey()}), so they can change without touching code or saves. Which environment a state has is
 * template data ({@link FederalTemplate#theme}), not part of the id.
 */
public enum FederalStateId {
    S1, S2, S3, S4, S5, S6;

    /** 1..6. */
    public int index() { return ordinal() + 1; }

    /** "s1".."s6": the id in data files and saves. */
    public String key() { return name().toLowerCase(java.util.Locale.ROOT); }

    public String nameKey() { return "afl.federal_state." + key() + ".name"; }

    public String abbreviationKey() { return "afl.federal_state." + key() + ".abbr"; }

    public static FederalStateId byKey(String key) {
        for (FederalStateId s : values()) if (s.key().equals(key)) return s;
        return null;
    }

    public static FederalStateId byIndex(int index) {
        return index >= 1 && index <= 6 ? values()[index - 1] : null;
    }
}
