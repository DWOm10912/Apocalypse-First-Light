package com.antaurora.apofirstlight.client.inventory;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Flat white weapon silhouettes facing left, for the HUD's weapon list (docs/ui/afl_inventory_grid_v0_plan.md 10). Native
 * guns use their side-view HUD silhouette ({@code textures/gui/gun/<id>_hud.png}, muzzle left). Melee weapons with a mesh
 * use the side view the mesh build draws ({@code textures/gui/melee/<id>_hud.png}, head left: the crowbar); others get one
 * made from their item sprite: every opaque pixel white, mirrored so the head points left. Looked up on first use and
 * dropped on a resource reload.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WeaponSilhouettes {
    /** A texture and its size in pixels (its aspect for drawing). */
    /** made: a dynamic texture made here (released on reload), not a resource. */
    public record Shape(ResourceLocation texture, float width, float height, boolean made) {}

    private static final Map<Item, Shape> MADE = new HashMap<>();
    private static final Shape NONE = new Shape(new ResourceLocation("missingno"), 0, 0, false);

    private WeaponSilhouettes() {}

    @Nullable
    public static Shape of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() instanceof NativeGunItem gun) {
            try {
                var d = gun.definition();
                if (d != null) return new Shape(d.hudIcon(), d.hudWidth(), d.hudHeight(), false);
            } catch (RuntimeException ignored) {
                // no gun data: fall through to the sprite
            }
        }
        var shape = MADE.computeIfAbsent(stack.getItem(), item -> make(stack));
        return shape == NONE ? null : shape;
    }

    private static Shape make(ItemStack stack) {
        var mc = Minecraft.getInstance();
        var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key != null) {
            var drawn = new ResourceLocation(key.getNamespace(), "textures/gui/melee/" + key.getPath() + "_hud.png");
            var resource = mc.getResourceManager().getResource(drawn);
            if (resource.isPresent()) {
                try (var in = resource.get().open(); var image = NativeImage.read(in)) {
                    return new Shape(drawn, image.getWidth(), image.getHeight(), false);
                } catch (Exception ignored) {
                    // unreadable: fall back to the sprite
                }
            }
        }
        try {
            var sprite = mc.getItemRenderer().getModel(stack, null, null, 0).getParticleIcon();
            var name = sprite.contents().name();
            var file = new ResourceLocation(name.getNamespace(), "textures/" + name.getPath() + ".png");
            try (var in = mc.getResourceManager().open(file); var image = NativeImage.read(in)) {
                int w = image.getWidth(), h = Math.min(image.getHeight(), w);   // animated sprites: the first frame
                var out = new NativeImage(w, h, true);
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++) {
                        int alpha = image.getPixelRGBA(x, y) >>> 24;
                        out.setPixelRGBA(w - 1 - x, y, alpha < 16 ? 0 : alpha << 24 | 0xFFFFFF);
                    }
                var id = mc.getTextureManager().register("afl_weapon_silhouette", new DynamicTexture(out));
                return new Shape(id, w, h, true);
            }
        } catch (Exception ex) {
            return NONE;
        }
    }

    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            var mc = Minecraft.getInstance();
            for (var shape : MADE.values()) if (shape.made()) mc.getTextureManager().release(shape.texture());
            MADE.clear();
        });
    }
}
