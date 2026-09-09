package com.antaurora.apofirstlight.weapon;

/** Shared attachment semantics; gun JSON owns compatibility. */
public interface NativeAttachment {
    enum Slot { SIGHT, MUZZLE, MAGAZINE }
    Slot slot();
    default double noiseRadiusMultiplier(){return 1;}
    default boolean suppressesFireSound(){return false;}
}
