package com.antaurora.apofirstlight.client.inventory;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.inventory.HotbarRoles;
import com.antaurora.apofirstlight.inventory.PlayerStorageCapacity;
import com.antaurora.apofirstlight.inventory.grid.GridPackets;
import com.antaurora.apofirstlight.inventory.grid.ItemSizes;
import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Grid Inventory V0 on the client (docs/ui/afl_inventory_grid_v0_plan.md 5, 10): the AFL inventory page instead of the
 * vanilla one in Survival / Adventure (Creative keeps its own), the rotate key, the quick binding keys 4-9, the size and
 * sling lines in item tooltips, and forgetting the server's sizes, pockets and bindings on leaving.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class GridClient {
    /**
     * Only the AFL inventory page and container screens with the grid side panel (ContainerGridPanel): R does not clash
     * with reload or the inspection reset.
     */
    private static final IKeyConflictContext INVENTORY_PAGE = new IKeyConflictContext() {
        @Override public boolean isActive() {
            var screen = Minecraft.getInstance().screen;
            return screen instanceof AflInventoryScreen || screen instanceof ExternalGridScreen || ContainerGridPanel.active(screen);
        }
        @Override public boolean conflicts(IKeyConflictContext other) { return other == this; }
    };
    public static final KeyMapping ROTATE = new KeyMapping("key.apocalypse_firstlight.rotate_item", INVENTORY_PAGE,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.apocalypse_firstlight");

    private GridClient() {}

    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event) {
        var player = Minecraft.getInstance().player;
        if (player == null || !(event.getNewScreen() instanceof InventoryScreen)) return;
        if (PlayerStorageCapacity.unrestricted(player)) return;
        event.setNewScreen(new AflInventoryScreen(player));
    }

    /**
     * Item tooltips on the AFL inventory and external grid pages take the pages' panel colours instead of vanilla's purple
     * (the text hints there are drawn in the same colours by the pages themselves). Vanilla chest screens keep theirs.
     */
    @SubscribeEvent
    public static void tooltipColour(RenderTooltipEvent.Color event) {
        var screen = Minecraft.getInstance().screen;
        if (!(screen instanceof AflInventoryScreen || screen instanceof ExternalGridScreen)) return;
        event.setBackground(AflUiStyle.argb(AflUiStyle.BACK, 0.94F));
        int line = AflUiStyle.argb(AflUiStyle.LINE, AflUiStyle.LINE_ALPHA);
        event.setBorderStart(line);
        event.setBorderEnd(line);
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ItemSizes.clearClient();
        PlayerGrids.clearClient();
        com.antaurora.apofirstlight.inventory.grid.Loadout.clearClient();
    }

    /**
     * Survival / Adventure: keys 4-9 are the quick bindings (docs/ui/afl_inventory_grid_v0_plan.md 10), taken before
     * vanilla reads them as hotbar keys; keys 1-3 stay vanilla's (the weapon positions).
     */
    @SubscribeEvent
    public static void clientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.START) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || PlayerStorageCapacity.unrestricted(mc.player)) return;
        var keys = mc.options.keyHotbarSlots;
        // driving: keys 1-3 do not take up a weapon (Loadout puts one away on the server too)
        if (com.antaurora.apofirstlight.vehicle.PickupTruckEntity.driving(mc.player))
            for (int i = 0; i < HotbarRoles.HAND_SLOT; i++)
                while (keys[i].consumeClick())
                    if (!mc.player.getInventory().items.get(i).isEmpty() && i != mc.player.getInventory().selected)
                        mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("hint.apocalypse_firstlight.loadout.driving"), true);
        for (int i = HotbarRoles.HAND_SLOT; i < keys.length; i++)
            while (keys[i].consumeClick())
                if (mc.screen == null && !mc.player.isSpectator()) GridPackets.sendUseBind(i - HotbarRoles.HAND_SLOT);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void tooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (stack.isEmpty()) return;
        if (com.antaurora.apofirstlight.inventory.grid.GunSling.has(stack))
            event.getToolTip().add(Component.translatable("tooltip.apocalypse_firstlight.sling_fitted").withStyle(ChatFormatting.GRAY));
        var size = ItemSizes.of(stack, true);
        if (size.w() == 1 && size.h() == 1) return;
        event.getToolTip().add(Component.translatable("tooltip.apocalypse_firstlight.item_size", size.w(), size.h())
                .withStyle(ChatFormatting.GRAY));
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) { event.register(ROTATE); }
    }
}
