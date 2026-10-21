package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.inventory.AflEquipmentSlot;
import com.antaurora.apofirstlight.registry.AflItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;
import top.theillusivec4.curios.api.type.ISlotType;

/**
 * The client side of AFL's equipment slots: their slot frames on the inventory page (the vanilla background has none
 * in that column), Curios' own panel button hidden while every slot the player has lives on AFL's page, and worn gear
 * drawn on the body: the hearing protection over the ears, chest rig and backpacks on the torso, the pistol holster on
 * the right thigh (their meshes, {@link WornMeshRenderer}), the wrist thermometer on the left wrist, the compass and
 * Geiger counter on the chest rig's shoulder-strap clips ({@link ClipRenderer}; the flip lighter joins when it reaches
 * this branch).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEquipmentSlots {
    private static final String CURIOS_BUTTON = "top.theillusivec4.curios.client.gui.CuriosButton";
    private ClientEquipmentSlots() {}

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.addListener(ClientEquipmentSlots::frames);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ClientEquipmentSlots::hideCuriosButton);
        event.enqueueWork(() -> {
            // origins: the bake shifts printed by tools/build-equipment-meshes-v1.mjs
            CuriosRendererRegistry.register(AflItems.SIMPLE_HEARING_PROTECTION.get(),
                    () -> new WornMeshRenderer(WornMeshRenderer.Part.HEAD, "simple_hearing_protection", 0F, -1.7504F, 0.2F));
            // the chest rig with what hangs on its straps' clips (ClipRenderer)
            CuriosRendererRegistry.register(AflItems.FIELD_CHEST_RIG.get(), () -> new ClipRenderer(
                    new WornMeshRenderer(WornMeshRenderer.Part.BODY, "field_chest_rig", 0F, 10.175F, -0.73F), ClipRenderer.FIELD_CHEST_RIG));
            CuriosRendererRegistry.register(AflItems.CANVAS_BACKPACK.get(),
                    () -> new WornMeshRenderer(WornMeshRenderer.Part.BODY, "canvas_backpack", 0F, 9.3F, 1.58F));
            CuriosRendererRegistry.register(AflItems.HIKING_BACKPACK.get(),
                    () -> new WornMeshRenderer(WornMeshRenderer.Part.BODY, "hiking_backpack", 0F, 11.6F, 1.855F));
            CuriosRendererRegistry.register(AflItems.PISTOL_HOLSTER.get(),
                    () -> new WornMeshRenderer(WornMeshRenderer.Part.RIGHT_LEG, "pistol_holster", -0.605F, 7.6F, 0F));
            CuriosRendererRegistry.register(AflItems.WRIST_THERMOMETER.get(), WristItemRenderer::new);
        });
    }

    /** Vanilla-style slot frames (18 × 18: dark top / left, white bottom / right, slot grey inside). */
    private static void frames(ContainerScreenEvent.Render.Background event) {
        if (!(event.getContainerScreen() instanceof InventoryScreen screen)) return;
        var g = event.getGuiGraphics();
        int left = screen.getGuiLeft(), top = screen.getGuiTop();
        for (var slot : screen.getMenu().slots) {
            if (!(slot instanceof AflEquipmentSlot) || !slot.isActive()) continue;
            int x = left + slot.x - 1, y = top + slot.y - 1;
            g.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
            g.fill(x, y, x + 17, y + 1, 0xFF373737);
            g.fill(x, y, x + 1, y + 17, 0xFF373737);
            g.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
            g.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        }
    }

    /** Curios' panel would be empty: AFL's slots are not on it (use_native_gui false). Keep it when another mod adds one. */
    private static void hideCuriosButton(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen) && !(event.getScreen() instanceof CreativeModeInventoryScreen)) return;
        var player = Minecraft.getInstance().player;
        if (player == null || CuriosApi.getPlayerSlots(player).values().stream().anyMatch(ISlotType::useNativeGui)) return;
        for (var listener : java.util.List.copyOf(event.getListenersList()))
            if (listener.getClass().getName().equals(CURIOS_BUTTON)) event.removeListener(listener);
    }

    /**
     * Draws a strap-on curio around the left wrist: its mesh (tools/build-equipment-meshes-v1.mjs) is authored around a
     * 4 x 4 px arm along +Y with the face to +X; the item is drawn with an unused display context (HEAD: no transform)
     * so it keeps its native size, centred on the mesh, then moved so the strap's arm axis sits on the arm.
     */
    private static final class WristItemRenderer implements ICurioRenderer {
        /** The arm axis in the baked mesh (px, from the build's wornAnchor), and where the strap sits along the arm. */
        private static final float MESH_ARM_X = -0.575F, WRIST_Y = 8.5F;
        @Override
        public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext, PoseStack poseStack,
                RenderLayerParent<T, M> parent, MultiBufferSource buffer, int light, float limbSwing, float limbSwingAmount,
                float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!(parent.getModel() instanceof HumanoidModel<?> humanoid)) return;
            var entity = slotContext.entity();
            boolean slim = entity instanceof net.minecraft.client.player.AbstractClientPlayer p && "slim".equals(p.getModelName());
            float armCentre = slim ? 0.5F : 1.0F, sx = slim ? 0.8F : 1.0F;   // left arm box: x -1..3 (slim -1..2) from its pivot
            poseStack.pushPose();
            humanoid.leftArm.translateAndRotate(poseStack);
            poseStack.translate((armCentre - MESH_ARM_X * sx) / 16F, WRIST_Y / 16F, 0);
            poseStack.scale(sx, -1F, -1F);                                       // mesh +Y up -> model +Y down; +X stays outward
            Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer()
                    .renderItem(entity, stack, ItemDisplayContext.HEAD, false, poseStack, buffer, light);
            poseStack.popPose();
        }
    }

    /**
     * Worn gear drawn as its mesh on the body part it sits on (tools/build-equipment-meshes-v1.mjs authors each piece in
     * that part's frame: BODY and HEAD +X the player's left, +Y up, +Z to the front, origin at the part's pivot; RIGHT_LEG
     * +X outward, +Y up, +Z to the back, origin at the hip joint). The bake rests the mesh on y = 0 centred on X / Z; the
     * build prints that shift as "origin", undone here. Moves with the part (crouching, walking, looking around).
     */
    public static final class WornMeshRenderer implements ICurioRenderer {
        public enum Part { BODY, HEAD, RIGHT_LEG }

        private final Part part;
        private final ResourceLocation geometry, texture;
        private final float ox, oy, oz;

        public WornMeshRenderer(Part part, String model, float ox, float oy, float oz) {
            this.part = part;
            this.geometry = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/" + model + ".geo.json");
            this.texture = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/" + model + "_mesh.png");
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
        }

        @Override
        public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext, PoseStack poseStack,
                RenderLayerParent<T, M> parent, MultiBufferSource buffer, int light, float limbSwing, float limbSwingAmount,
                float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!(parent.getModel() instanceof HumanoidModel<?> humanoid) || !visible(humanoid, part)) return;
            poseStack.pushPose();
            apply(humanoid, part, poseStack);
            draw(poseStack, buffer, light);
            poseStack.popPose();
        }

        /** Draws the mesh in the frame {@link #apply} set up. */
        public void draw(PoseStack poseStack, MultiBufferSource buffer, int light) {
            poseStack.pushPose();
            poseStack.translate(-ox / 16F, -oy / 16F, -oz / 16F);
            AflStaticMeshItemRenderer.drawMesh(geometry, texture, poseStack, buffer, light, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }

        /** A part the model hides (the head in the first-person body, client/FirstPersonBody) takes its mesh with it. */
        private static boolean visible(HumanoidModel<?> humanoid, Part part) {
            return switch (part) {
                case BODY -> humanoid.body.visible;
                case HEAD -> humanoid.head.visible;
                case RIGHT_LEG -> humanoid.rightLeg.visible;
            };
        }

        /** Moves the pose from model space into a part's authoring frame. */
        public static void apply(HumanoidModel<?> humanoid, Part part, PoseStack poseStack) {
            switch (part) {
                case BODY -> { humanoid.body.translateAndRotate(poseStack); poseStack.scale(1F, -1F, -1F); }
                case HEAD -> { humanoid.head.translateAndRotate(poseStack); poseStack.scale(1F, -1F, -1F); }
                case RIGHT_LEG -> { humanoid.rightLeg.translateAndRotate(poseStack); poseStack.mulPose(Axis.ZP.rotationDegrees(180)); }
            }
        }
    }
}
