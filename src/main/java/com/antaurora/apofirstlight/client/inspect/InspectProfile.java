package com.antaurora.apofirstlight.client.inspect;

import com.antaurora.apofirstlight.client.GeigerCounterItemRenderer;
import com.antaurora.apofirstlight.inspect.InspectActions.Action;
import com.antaurora.apofirstlight.item.GeigerCounterItem;
import com.antaurora.vextris.mesh.MeshPart;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * An item's hotspots on the inspect screen (docs/gameplay/item_inspect_v1_design.md 4): found by mesh part name in the
 * item's drawn frame, with the way the part faces (a hotspot turned away from the camera stays dark). V1: the Geiger
 * counter only; every other item is looked at without hotspots.
 */
final class InspectProfile {
    /** What a click does: an action for the server, or {@link #CHOOSE} (the candidate strip first). */
    record Hotspot(String part, MeshPart.Bounds bounds, Vector3f normal, @Nullable Action left, String leftLabel,
                   @Nullable Action right, @Nullable String rightLabel) {}

    /** The bay's left click when empty: pick a battery from the strip, then FIT_BATTERY. */
    static final Action CHOOSE = null;

    private static final Vector3f BACK = new Vector3f(0, -1, 0), SIDE = new Vector3f(1, 0, 0);

    private InspectProfile() {}

    static boolean has(ItemStack stack) {
        return stack.getItem() instanceof GeigerCounterItem && stack.getCount() == 1;
    }

    /** The hotspots for the stack as it is now (the cover open or shut, a battery in or not). */
    static List<Hotspot> hotspots(ItemStack stack) {
        List<Hotspot> list = new ArrayList<>();
        if (!has(stack)) return list;
        var power = GeigerCounterItemRenderer.bounds("power_switch", stack);
        if (power != null)
            list.add(new Hotspot("power_switch", power, SIDE, Action.SWITCH, GeigerCounterItem.on(stack) ? "switch_off" : "switch_on", null, null));
        if (!GeigerCounterItem.coverOpen(stack)) {
            var cover = GeigerCounterItemRenderer.bounds("cover", stack);
            if (cover != null) list.add(new Hotspot("cover", cover, BACK, Action.OPEN_COVER, "open_cover", null, null));
            return list;
        }
        // the cover is off: the bay takes the battery out or a new one in (left), the cover back on (right)
        boolean fitted = !GeigerCounterItem.battery(stack).isEmpty();
        var bay = GeigerCounterItemRenderer.bounds(fitted ? "battery" : "bay", stack);
        if (bay != null)
            list.add(new Hotspot(fitted ? "battery" : "bay", bay, BACK, fitted ? Action.REMOVE_BATTERY : CHOOSE,
                    fitted ? "remove_battery" : "fit_battery", Action.CLOSE_COVER, "close_cover"));
        return list;
    }
}
