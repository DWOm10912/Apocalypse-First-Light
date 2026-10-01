package com.antaurora.apofirstlight.block;

/**
 * Front-first access for displays two items deep (Retail Shelf V3, Beverage Cooler V2), without restocking: taking the
 * front item leaves the back one where it is. Taking: the front item, else the back one. Placing: never past a front item;
 * into the front when only the back is filled; where the crosshair lands when both are free.
 */
public final class DisplayDepthRule {
    private DisplayDepthRule() {
    }

    /** The slot to take from or place into, or -1 when nothing applies. */
    public static int choose(int front, int back, boolean frontFilled, boolean backFilled, boolean placing, boolean aimBack) {
        if (!placing) {
            return frontFilled ? front : backFilled ? back : -1;
        }
        if (frontFilled) {
            return -1;
        }
        return backFilled || !aimBack ? front : back;
    }
}
