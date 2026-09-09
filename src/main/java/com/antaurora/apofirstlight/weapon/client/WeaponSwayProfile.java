package com.antaurora.apofirstlight.weapon.client;

/** Visual-only defaults; independent of gun JSON, aim and recoil. Angles are degrees. */
public record WeaponSwayProfile(double x,double y,float yaw,float pitch,float roll,
                                double period,double secondaryPeriod,float ads,float crouchAds) {
    public static final WeaponSwayProfile DEFAULT=new WeaponSwayProfile(.0008,.0012,.20f,.15f,.07f,4.2,6.7,.30f,.20f);
}
