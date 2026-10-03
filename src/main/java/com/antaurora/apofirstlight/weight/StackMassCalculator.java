package com.antaurora.apofirstlight.weight;

import com.antaurora.apofirstlight.weapon.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import java.util.function.Function;

/**
 * Read-only calculation; no ammo initialization or capability mutation. Contents: a registered provider, else the
 * standard container list (BlockEntityTag.Items or Items, vanilla ContainerHelper format) read item by item; registered
 * fluid providers add their fluid. No other NBT is read.
 */
public final class StackMassCalculator {
    private static final Map<ResourceLocation, Function<ItemStack, Iterable<ItemStack>>> CONTENTS = new HashMap<>();
    private static final Map<ResourceLocation, Function<ItemStack, Iterable<FluidStack>>> FLUIDS = new HashMap<>();
    private static final int MAX_DEPTH = 8, MAX_ELEMENTS = 512;
    private static final ResourceLocation EMPTY_BUCKET = new ResourceLocation("minecraft", "bucket");
    /** Per 1000 mB of a fluid without a priced bucket: water's (a bucket carries 10 L). */
    private static final long FALLBACK_GRAMS_PER_BUCKET = 10_000;
    private StackMassCalculator() {}

    /** Register during common setup. Contents are for ONE outer item; must be read-only and exclude its own shell. */
    public static void registerContents(ResourceLocation item, Function<ItemStack, Iterable<ItemStack>> provider) {
        if (CONTENTS.putIfAbsent(item, provider) != null) throw new IllegalArgumentException("Duplicate contents provider " + item);
    }

    /** Register during common setup. The fluid ONE outer item keeps (a broken tank's); read-only. See {@link #fluid}. */
    public static void registerFluidContents(ResourceLocation item, Function<ItemStack, Iterable<FluidStack>> provider) {
        if (FLUIDS.putIfAbsent(item, provider) != null) throw new IllegalArgumentException("Duplicate fluid provider " + item);
    }
    public static MassResult mass(ItemStack stack) { return mass(stack, ItemMassData.snapshot()); }
    public static MassResult mass(ItemStack stack, ItemMassData.Snapshot data) { return calculate(stack, data, 0, new int[]{0}); }

    static void unit(MassResult.Builder out, String part, ItemMassData.Unit unit, long count) {
        if (count == 0) return;
        out.add(part, MassResult.multiply(unit.grams(), count));
        if (unit.estimated()) out.issue(unit.source().startsWith("fallback:") ? unit.source() : "test_estimate:" + unit.source());
    }

    /**
     * 1000 mB of a fluid weighs its filled bucket minus the empty bucket (both from the mass table), so pouring buckets
     * into a tank and breaking it keeps the total. No bucket, or an unpriced one: {@link #FALLBACK_GRAMS_PER_BUCKET}.
     */
    static void fluid(MassResult.Builder out, FluidStack fluid, ItemMassData.Snapshot data) {
        if (fluid.isEmpty() || fluid.getAmount() <= 0) return;
        var fluidId = ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
        var bucket = fluid.getFluid().getBucket();
        var full = bucket == Items.AIR ? null : data.unit(ForgeRegistries.ITEMS.getKey(bucket));
        var empty = data.unit(EMPTY_BUCKET);
        long perBucket;
        if (full == null || full.source().startsWith("fallback:") || empty.source().startsWith("fallback:") || full.grams() < empty.grams()) {
            perBucket = FALLBACK_GRAMS_PER_BUCKET;
            out.issue("fallback:fluid:" + fluidId);
        } else {
            perBucket = full.grams() - empty.grams();
            if (full.estimated() || empty.estimated()) out.issue("test_estimate:fluid:" + fluidId);
        }
        out.add("fluid", MassResult.multiply(perBucket, fluid.getAmount()) / 1000);
    }

    /** The standard container list (BlockEntityTag.Items for a block item, else Items), or null in any other format. */
    private static ListTag standardItems(CompoundTag tag) {
        var blockEntity = tag.getCompound("BlockEntityTag");
        Tag items = blockEntity.contains("Items") ? blockEntity.get("Items") : tag.get("Items");
        return items instanceof ListTag list && (list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND) ? list : null;
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
                var list = standardItems(stack.getTag());
                if (list == null) out.issue("unsupported_contents:" + id);
                else for (int i = 0; i < list.size(); i++) {
                    if (elements[0] >= MAX_ELEMENTS) { out.issue("contents_limit:" + id); break; }
                    out.add("contents", calculate(ItemStack.of(list.getCompound(i)), data, depth + 1, elements));
                }
            }
            var fluids = FLUIDS.get(id);
            if (fluids != null) for (var fluid : fluids.apply(stack)) fluid(out, fluid, data);
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
