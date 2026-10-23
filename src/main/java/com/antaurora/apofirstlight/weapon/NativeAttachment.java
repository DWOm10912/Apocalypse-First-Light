package com.antaurora.apofirstlight.weapon;

/** Shared attachment semantics; gun JSON owns compatibility. */
public interface NativeAttachment {
    /** SLING: the gun sling at the rear swivel (GunSling); no model on the gun, the worn sling is CarriedWeaponsLayer's. */
    enum Slot { SIGHT, MUZZLE, MAGAZINE, SLING }
    Slot slot();
    default double noiseRadiusMultiplier(){return 1;}
    default boolean suppressesFireSound(){return false;}
}
