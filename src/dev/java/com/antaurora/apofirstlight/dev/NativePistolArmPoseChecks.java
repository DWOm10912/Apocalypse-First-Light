package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.weapon.P901Item;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.HashSet;
import java.util.Set;

/** DEV-only: detached player/model probes, plus bounded actual-render observations. */
@Mod.EventBusSubscriber(modid=ApocalypseFirstLight.MOD_ID,value=Dist.CLIENT)
public final class NativePistolArmPoseChecks {
    private static boolean checked;
    private static final Set<String> observed=new HashSet<>();
    private NativePistolArmPoseChecks() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        var mc=Minecraft.getInstance();
        if(checked || event.phase!=TickEvent.Phase.END || mc.player==null || mc.level==null) return;
        checked=true;
        try {
            int frames=0;
            for(boolean slim:new boolean[]{false,true}) for(var hand:HumanoidArm.values()) {
                var entity=new RemotePlayer(mc.level,mc.player.getGameProfile()) {
                    @Override public HumanoidArm getMainArm() { return hand; }
                };
                var model=new PlayerModel<RemotePlayer>(mc.getEntityModels().bakeLayer(
                        slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim);
                model.rightArmPose=HumanoidModel.ArmPose.EMPTY;
                model.leftArmPose=HumanoidModel.ArmPose.EMPTY;
                // Initialize Vanilla switch BEFORE asking for the pistol pose.
                model.setupAnim(entity,0,0,1,0,0);
                int countBefore=HumanoidModel.ArmPose.values().length;
                var stack=new ItemStack(AflItems.P9_01.get());
                entity.setItemSlot(EquipmentSlot.MAINHAND,stack);
                var extension=IClientItemExtensions.of(stack);
                var pose=extension.getArmPose(entity,InteractionHand.MAIN_HAND,stack);
                require(pose==HumanoidModel.ArmPose.CROSSBOW_HOLD,"Vanilla pose identity");
                require(pose.ordinal()<10 && HumanoidModel.ArmPose.values().length==countBefore,"no enum extension");
                require(extension.getArmPose(entity,InteractionHand.OFF_HAND,stack)==null,"offhand fallback unchanged");
                for(boolean crouch:new boolean[]{false,true}) for(int i=0;i<120;i++) {
                    boolean pistol=i%3==0;
                    entity.setItemSlot(EquipmentSlot.MAINHAND,pistol?stack:i%3==1?new ItemStack(Items.STICK):ItemStack.EMPTY);
                    model.crouching=crouch;
                    var main=pistol?pose:i%3==1?HumanoidModel.ArmPose.ITEM:HumanoidModel.ArmPose.EMPTY;
                    model.rightArmPose=hand==HumanoidArm.RIGHT?main:HumanoidModel.ArmPose.EMPTY;
                    model.leftArmPose=hand==HumanoidArm.LEFT?main:HumanoidModel.ArmPose.EMPTY;
                    model.setupAnim(entity,0,0,1+i,0,0);
                    require(Float.isFinite(model.rightArm.xRot)&&Float.isFinite(model.leftArm.xRot),"finite rotations");
                    if(pistol) {
                        require(model.rightArm.xRot<-.5F && model.leftArm.xRot<-.5F,"both arms raised");
                        var dominant=hand==HumanoidArm.RIGHT?model.rightArm:model.leftArm;
                        var support=hand==HumanoidArm.RIGHT?model.leftArm:model.rightArm;
                        require(Math.abs(dominant.yRot)<Math.abs(support.yRot),"mirrored primary/support role");
                    }
                    require(model.rightSleeve.xRot==model.rightArm.xRot && model.leftSleeve.xRot==model.leftArm.xRot,"sleeves follow arms");
                    frames++;
                }
            }
            ApocalypseFirstLight.LOGGER.info("[AFL ARMPOSE V0471] PASS: {} setupAnim calls; Classic/Slim, RIGHT/LEFT, crouch, pistol/stick/empty; Vanilla CROSSBOW_HOLD ordinal={}; no custom enum. F5/visual still user checks",frames,HumanoidModel.ArmPose.CROSSBOW_HOLD.ordinal());
        } catch(Throwable failure) { ApocalypseFirstLight.LOGGER.error("[AFL ARMPOSE V0471] FAIL",failure); }
    }

    @SubscribeEvent
    public static void rendered(RenderPlayerEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(event.getEntity()!=mc.player || !(event.getEntity().getMainHandItem().getItem() instanceof P901Item)) return;
        String camera=mc.options.getCameraType().name();
        if(observed.add(camera)) ApocalypseFirstLight.LOGGER.info("[AFL ARMPOSE V0471 RENDER] completed PlayerRenderer; camera={}, skin={}, mainArm={}; not visual acceptance",camera,mc.player.getModelName(),mc.player.getMainArm());
    }

    private static void require(boolean condition,String message) {
        if(!condition) throw new IllegalStateException(message);
    }
}
