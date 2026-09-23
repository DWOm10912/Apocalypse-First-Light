package com.antaurora.apofirstlight.weapon;

/** Mechanical action, independent of the trigger's SEMI/BURST/AUTO cadence. */
public enum NativeActionType {
    MAGAZINE, BREAK_ACTION;

    public static NativeActionType parse(String value) {
        return switch (value) {
            case "magazine" -> MAGAZINE;
            case "break_action" -> BREAK_ACTION;
            default -> throw new IllegalArgumentException("action_type: unknown value " + value);
        };
    }
}
