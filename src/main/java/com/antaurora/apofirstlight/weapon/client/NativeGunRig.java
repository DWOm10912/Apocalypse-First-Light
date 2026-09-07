package com.antaurora.apofirstlight.weapon.client;

/** Asset node names only. No weapon scale, hand offsets, or animation pose values. */
public record NativeGunRig(String gunModelRoot, String rightLocator, String leftLocator,
                           String firstPersonRoot) {
    public NativeGunRig {
        java.util.Objects.requireNonNull(gunModelRoot);
        java.util.Objects.requireNonNull(rightLocator);
        java.util.Objects.requireNonNull(leftLocator);
        java.util.Objects.requireNonNull(firstPersonRoot);
    }
}
