package com.antaurora.apofirstlight.energy;

public enum EnergyCellMode {
    CHARGE(0),
    DISCHARGE(1);

    private final int serializedValue;

    EnergyCellMode(int serializedValue) {
        this.serializedValue = serializedValue;
    }

    public int serializedValue() {
        return serializedValue;
    }

    public EnergyCellMode toggled() {
        return this == CHARGE ? DISCHARGE : CHARGE;
    }

    public static EnergyCellMode fromSerialized(int value) {
        return value == DISCHARGE.serializedValue ? DISCHARGE : CHARGE;
    }
}
