package com.antaurora.apofirstlight.weight;

import com.antaurora.apofirstlight.weapon.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import java.util.function.Function;

/** Read-only calculation; no ammo initialization, capability mutation, or arbitrary NBT recursion. */
public final class StackMassCalculator {
    private static final Map<ResourceLocation, Function<ItemStack, Iterable<ItemStack>>> CONTENTS = new HashMap<>();
    private static final int MAX_DEPTH = 8, MAX_ELEMENTS = 512;
    private StackMassCalculator() {}

    /** Register during common setup. Contents are for ONE outer item; must be read-only and exclude its own shell. */
    public static void registerContents(ResourceLocation item, Function<ItemStack, Iterable<ItemStack>> provider) {
        if (CONTENTS.putIfAbsent(item, provider) != null) throw new IllegalArgumentException("Duplicate contents provider " + item);
    }
    public static MassResult mass(ItemStack stack) { return mass(stack, ItemMassData.snapshot()); }
    public static MassResult mass(ItemStack stack, ItemMassData.Snapshot data) { return calculate(stack, data, 0, new int[]{0}); }

    static void unit(MassResult.Builder out, String part, ItemMassData.Unit unit, long count) {
        if (count == 0) return;
        out.add(part, MassResult.multiply(unit.grams(), count));
        if (unit.estimated()) out.issue(unit.source().startsWith("fallback:") ? unit.source() : "test_estimate:" + unit.source());
    }
    private static MassResult calculate(ItemStack stack, ItemMassData.Snapshot data, int depth, int[] elements) {
        var out = new MassResult.Builder();
        if (stack.isEmpty()) { elements[0]++; return out.build(); }
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (depth > MAX_DEPTH || ++elements[0] > MAX_ELEMENTS) {
            out.add("unresolved", MassResult.multiply(data.policy().fallbackGrams(), stack.getCount()));
            out.issue("contents_limit:" + id); return out.build();
        }
        try {
            if (stack.getItem() instanceof NativeGunItem gun) {
                var definition = gun.definition();
                var configured = data.guns().get(definition.id());
                var fallback = new ItemMassData.Unit(data.policy().fallbackGrams(), true, "fallback:gun:" + definition.id());
                unit(out, "receiver", configured == null ? fallback : configured.receiver(), 1);
                for (var slot : NativeAttachment.Slot.values()) {
                    var stored = NativeAttachments.stored(stack, slot);
                    boolean active = !NativeAttachments.active(stack, slot).isEmpty();
                    if (slot == NativeAttachment.Slot.MAGAZINE) {
                        if (active) out.add("magazine", calculate(stored, data, depth + 1, elements));
                        else {
                            unit(out, "magazine", configured == null ? fallback : configured.magazine(), 1);
                            if (!stored.isEmpty()) out.add("inactive_magazine", calculate(stored, data, depth + 1, elements));
                        }
                    } else if (!stored.isEmpty()) out.add(slot.name().toLowerCase(Locale.ROOT), calculate(stored, data, depth + 1, elements));
                    if (!stored.isEmpty() && !active) out.issue("inactive_attachment:" + slot);
                }
                unit(out, "loaded_ammo", data.unit(definition.ammoType()), NativeGunAmmo.read(stack, definition));
            } else unit(out, "shell", data.unit(id), 1);

            var provider = CONTENTS.get(id);
            if (provider != null) {
                for (var child : provider.apply(stack)) {
                    if (elements[0] >= MAX_ELEMENTS) { out.issue("contents_limit:" + id); break; }
                    out.add("contents", calculate(child, data, depth + 1, elements));
                }
            } else if (stack.hasTag() && (stack.getTag().contains("Items")
                    || stack.getTag().getCompound("BlockEntityTag").contains("Items"))) {
                out.issue("unsupported_contents:" + id);
            }
        } catch (RuntimeException ex) {
            var failed = new MassResult.Builder();
            failed.add("unresolved", MassResult.multiply(data.policy().fallbackGrams(), stack.getCount()));
            failed.issue("calculation_failed:" + id + ":" + ex.getClass().getSimpleName());
            return failed.build();
        }
        // Components above describe a single assembled item. Multiply exactly once at this boundary.
        var one = out.build();
        var result = new MassResult.Builder();
        one.breakdown().forEach((name, grams) -> result.add(name, MassResult.multiply(grams, stack.getCount())));
        one.issues().forEach(result::issue);
        return result.build();
    }
}
