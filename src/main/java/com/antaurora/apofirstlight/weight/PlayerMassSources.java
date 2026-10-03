package com.antaurora.apofirstlight.weight;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Function;

/** Ownership, not GUI slot enumeration. Providers must expose only additional, disjoint carried items. */
public final class PlayerMassSources {
    private static final Map<ResourceLocation, Function<ServerPlayer, Iterable<ItemStack>>> EXTRA = new LinkedHashMap<>();
    private PlayerMassSources() {}
    public static void registerExtraEquipment(ResourceLocation id, Function<ServerPlayer, Iterable<ItemStack>> provider) {
        if (EXTRA.putIfAbsent(id, provider) != null) throw new IllegalArgumentException("Duplicate equipment source " + id);
    }
    public static MassResult calculate(ServerPlayer player, ItemMassData.Snapshot data) {
        var result = new MassResult.Builder();
        var inv = player.getInventory();
        add(result, "inventory", inv.items, data); // ALL 36, including locked occupied slots.
        add(result, "armor", inv.armor, data);
        add(result, "offhand", inv.offhand, data);
        result.add("cursor", StackMassCalculator.mass(player.containerMenu.getCarried(), data));
        // Known Vanilla ownership contract: slot 0 is RESULT, inputs are 1..4 / 1..9.
        for (int i = 1; i <= 4; i++) result.add("crafting", StackMassCalculator.mass(player.inventoryMenu.getSlot(i).getItem(), data));
        if (player.containerMenu instanceof CraftingMenu) for (int i = 1; i <= 9; i++)
            result.add("crafting", StackMassCalculator.mass(player.containerMenu.getSlot(i).getItem(), data));
        EXTRA.forEach((id, provider) -> {
            try { add(result, "equipment:" + id, provider.apply(player), data); }
            catch (RuntimeException ex) { result.issue("equipment_provider_failed:" + id); }
        });
        return result.build();
    }
    private static void add(MassResult.Builder out, String source, Iterable<ItemStack> stacks, ItemMassData.Snapshot data) {
        for (var stack : stacks) out.add(source, StackMassCalculator.mass(stack, data));
    }
}
