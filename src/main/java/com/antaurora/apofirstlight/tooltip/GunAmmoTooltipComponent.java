package com.antaurora.apofirstlight.tooltip;

import com.antaurora.apofirstlight.weapon.NativeGunDefinition;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Common-side payload; rendering and factory registration live exclusively on the client. */
public record GunAmmoTooltipComponent(ItemStack ammoStack, Component ammoName) implements TooltipComponent {
    public static final String ROW_KEY = "tooltip.apocalypse_firstlight.gun_ammo_row";

    public GunAmmoTooltipComponent {
        ammoStack = ammoStack.copy();
        ammoName = ammoName.copy().withStyle(style -> style
                .withColor(AflTooltipStatType.AMMUNITION.color()).withItalic(false));
    }

    public static GunAmmoTooltipComponent resolve(NativeGunDefinition gun) {
        var id = gun.ammoType();
        var item = id != null && ForgeRegistries.ITEMS.containsKey(id)
                ? ForgeRegistries.ITEMS.getValue(id) : null;
        ItemStack stack = item == null ? ItemStack.EMPTY : new ItemStack(item);
        String nameKey = item != null && !stack.isEmpty() ? item.getDescriptionId()
                : id == null ? "" : "item." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        Component name;
        if (Language.getInstance().has(nameKey + ".caliber")) {
            name = Component.translatable(nameKey + ".caliber");
        } else if (!stack.isEmpty()) {
            name = stack.getHoverName();
        } else if (!nameKey.isEmpty() && Language.getInstance().has(nameKey)) {
            name = Component.translatable(nameKey);
        } else {
            name = Component.translatable("tooltip.apocalypse_firstlight.unknown_ammunition");
        }
        return new GunAmmoTooltipComponent(stack, name);
    }
}
