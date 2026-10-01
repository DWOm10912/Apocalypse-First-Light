package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.lang.reflect.Field;

/** Binds the opted-in plain Items (ammo, Material Meshes V1) and the locker item to the AFL Mesh item renderer. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AflStaticMeshItemClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            bind(AflItems.ROUND_12_GAUGE.get(), "12_gauge_round", "12_gauge_round_mesh", 0.32);
            // 12 Gauge fired shell (tools/build-12ga-ammo.mjs, hand-made 12ga_hybrid_mesh_spent): 0.5 - height 18.945 / 32,
            // shares the round's atlas; display scales keep scale x height equal to the other casings.
            bind(AflItems.CASING_12_GAUGE.get(), "12_gauge_casing", "12_gauge_round_mesh", -0.09203125);
            // ItemEntityRenderer adds 0.25 * ground scale above the entity. Keep these upright rounds
            // at the same visible base height as the 12 Gauge shell without changing other views.
            bind(AflItems.ROUND_50_AE.get(), "50_ae_round", "blackridge_50ae_ammo_v1", 0.431217, 0.257);
            bind(AflItems.CASING_50_AE.get(), "50_ae_casing", "blackridge_50ae_ammo_v1", 0.444568);
            // 9x19mm Pure Mesh set (tools/build-9x19mm-ammo.mjs): offset = 0.5 - mesh height / 32 centres it like .50 AE.
            bind(AflItems.ROUND_9MM.get(), "9x19mm_round", "9x19mm_ammo_v1", 0.451463, 0.2558333333);
            bind(AflItems.CASING_9MM.get(), "9x19mm_casing", "9x19mm_ammo_v1", 0.468168);
            // 7.62x51mm set (tools/build-762x51mm-ammo.mjs): 0.5 - height / 32 (round 3.30791, casing 2.38605); the round's
            // ground offset puts its base 0.014 above the entity like the other rounds (0.25 + 0.014 / ground scale 1.33).
            bind(AflItems.ROUND_762MM.get(), "762x51mm_round", "762x51mm_ammo_v1", 0.396628, 0.2605263158);
            bind(AflItems.CASING_762MM.get(), "762x51mm_casing", "762x51mm_ammo_v1", 0.425436);
            // 12.7x55mm set (tools/build-127x55mm-ammo.mjs): 0.5 - height / 32 (round 3.109, casing 2.34453); the round's
            // ground offset puts its base 0.014 above the entity like the other rounds (0.25 + 0.014 / ground scale 1.415).
            bind(AflItems.ROUND_127MM.get(), "12_7x55mm_round", "12_7x55mm_ammo_v1", 0.40284375, 0.2598939929);
            bind(AflItems.CASING_127MM.get(), "12_7x55mm_casing", "12_7x55mm_ammo_v1", 0.4267334375);
            // Material Meshes V1 (tools/build-material-meshes-v1.mjs): offset = 0.5 - mesh height / 32 centres each mesh, its own atlas.
            bind(AflItems.STEEL_BILLET.get(), "steel_billet", "steel_billet_mesh", 0.3875);
            bind(AflItems.LEAD_BRICK.get(), "lead_brick", "lead_brick_mesh", 0.40625);
            bind(AflItems.ELECTROLYTIC_NICKEL.get(), "electrolytic_nickel", "electrolytic_nickel_mesh", 0.44375);
            bind(AflItems.TUNGSTEN_FILAMENT.get(), "tungsten_filament", "tungsten_filament_mesh", 0.263462);
            bind(AflItems.CEMENTED_CARBIDE_BLANK.get(), "cemented_carbide_blank", "cemented_carbide_blank_mesh", 0.388885);
            bind(AflItems.STEEL_SCRAP.get(), "steel_scrap", "steel_scrap_mesh", 0.446402);
            // Industrial Locker V2 (tools/build-industrial-locker-v2.mjs): the block's own mesh and atlas, closed door pose.
            // Mesh origin = block bottom centre, same place as the old 2-block item model, so its display transforms are kept.
            bind(AflItems.INDUSTRIAL_LOCKER.get(), () -> new AflStaticMeshItemRenderer(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/industrial_locker.geo.json"),
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/industrial_locker.png"), 0.0, 0.0), "industrial_locker");
        });
    }

    private static void bind(Item item, String model, String atlas, double verticalOffset) {
        bind(item, model, atlas, verticalOffset, verticalOffset);
    }

    private static void bind(Item item, String model, String atlas, double verticalOffset, double groundVerticalOffset) {
        bind(item, () -> new AflStaticMeshItemRenderer(model, atlas, verticalOffset, groundVerticalOffset), model);
    }

    private static void bind(Item item, java.util.function.Supplier<AflStaticMeshItemRenderer> factory, String model) {
        try {
            // Forge 1.20.1 has no separate ordinary-Item extension registration event.
            Field field = Item.class.getDeclaredField("renderProperties");
            field.setAccessible(true);
            field.set(item, new IClientItemExtensions() {
                private AflStaticMeshItemRenderer renderer;

                @Override
                public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (renderer == null) renderer = factory.get();
                    return renderer;
                }
            });
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot bind the AFL Mesh item renderer to Forge's Item extension: " + model, e);
        }
    }

    private AflStaticMeshItemClient() {}
}
