package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.block.ThermalGeneratorBlock;
import com.antaurora.apofirstlight.blockentity.ThermalGeneratorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="apocalypse_firstlight",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ThermalGeneratorRenderer implements BlockEntityRenderer<ThermalGeneratorBlockEntity> {
    public static final double ROTOR_DEGREES_PER_TICK = 6.0; // 20 RPM at 20 TPS.
    private static final ResourceLocation ROTOR=model("rotor"), GREEN=model("status_green"),
            YELLOW=model("status_yellow"), RED=model("status_red"), GLASS=model("glass");
    private static final double[][] PILE={{-.085,0,-.025,-18},{.07,0,.02,24},{0,.045,-.005,6}};
    public ThermalGeneratorRenderer(BlockEntityRendererProvider.Context context) {}
    private static ResourceLocation model(String name) { return new ResourceLocation("apocalypse_firstlight","block/thermal_generator_dynamic_"+name); }
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) {
        for(var model:new ResourceLocation[]{ROTOR,GREEN,YELLOW,RED,GLASS})event.register(model);
    }
    @Override public void render(ThermalGeneratorBlockEntity machine,float partialTick,PoseStack pose,
                                  MultiBufferSource buffers,int light,int overlay) {
        pose.pushPose();
        pose.translate(.5,0,.5);
        float yaw=switch(machine.getBlockState().getValue(ThermalGeneratorBlock.FACING)) {
            case EAST -> -90;case SOUTH -> 180;case WEST -> 90;default -> 0;
        };
        pose.mulPose(Axis.YP.rotationDegrees(yaw));pose.translate(-.5,0,-.5);
        var pivot=ThermalGeneratorRenderGeometry.ROTOR_PIVOT;
        pose.pushPose();pose.translate(pivot[0],pivot[1],pivot[2]);
        pose.mulPose(Axis.XP.rotationDegrees((float)((machine.getRotorTime(partialTick)*ROTOR_DEGREES_PER_TICK)%360)));
        pose.translate(-pivot[0],-pivot[1],-pivot[2]);draw(ROTOR,pose,buffers,light,overlay,1);pose.popPose();
        var state=machine.getVisualState();
        lamp(GREEN,state==ThermalGeneratorBlockEntity.VisualState.RUNNING,pose,buffers,light,overlay);
        lamp(YELLOW,state==ThermalGeneratorBlockEntity.VisualState.FULL,pose,buffers,light,overlay);
        lamp(RED,state==ThermalGeneratorBlockEntity.VisualState.ERROR,pose,buffers,light,overlay);
        solid(machine.getItem(ThermalGeneratorBlockEntity.FUEL_SLOT),pose,buffers,light,overlay);
        var fluid=machine.getLiquidFuel();
        if(!fluid.isEmpty()) {
            var min=ThermalGeneratorRenderGeometry.LIQUID_MIN;var max=ThermalGeneratorRenderGeometry.LIQUID_MAX;
            float top=(float)(min[1]+(max[1]-min[1])*Math.min(1.0,(double)fluid.getAmount()/machine.getTankCapacity()));
            int emission=fluid.getFluid().getFluidType().getLightLevel(fluid);
            int fluidLight=LightTexture.pack(Math.max(LightTexture.block(light),emission),LightTexture.sky(light));
            FluidRenderHelper.renderTankCuboid(fluid,pose,buffers,fluidLight,overlay,
                    (float)min[0],(float)min[1],(float)min[2],(float)max[0],top,(float)max[2],true);
        }
        // Glass must not write an opaque depth mask before vanilla world particles render.
        var mc=Minecraft.getInstance();
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),buffers.getBuffer(GlassType.TYPE),null,
                mc.getModelManager().getModel(GLASS),1,1,1,light,overlay);
        pose.popPose();
    }
    private static void lamp(ResourceLocation id,boolean on,PoseStack p,MultiBufferSource b,int light,int overlay) {
        draw(id,p,b,on?LightTexture.FULL_BRIGHT:light,overlay,on?1:.45f);
    }
    private static final class GlassType extends RenderType {
        private static final RenderType TYPE=create("thermal_heat_glass",
                com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,256,false,true,
                CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS,false,false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
        private GlassType(){super("unused",com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
    }
    private static void draw(ResourceLocation id,PoseStack p,MultiBufferSource buffers,int light,int overlay,float shade) {
        var mc=Minecraft.getInstance();
        mc.getBlockRenderer().getModelRenderer().renderModel(p.last(),
                buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)),null,
                mc.getModelManager().getModel(id),shade,shade,shade,light,overlay);
    }
    private static void solid(ItemStack stack,PoseStack p,MultiBufferSource b,int light,int overlay) {
        if(!(stack.is(Items.COAL)||stack.is(Items.CHARCOAL)||stack.is(Items.COAL_BLOCK)))return;
        var anchor=ThermalGeneratorRenderGeometry.SOLID_ANCHOR;
        boolean block=stack.is(Items.COAL_BLOCK);
        for(int i=0;i<(block?1:PILE.length);i++) {
            p.pushPose();
            p.translate(anchor[0]+(block?0:PILE[i][0]),anchor[1]+(block?.05:.009+PILE[i][1]),anchor[2]+(block?0:PILE[i][2]));
            p.mulPose(Axis.YP.rotationDegrees(block?0:(float)PILE[i][3]));
            if(!block)p.mulPose(Axis.XP.rotationDegrees(90));
            float scale=block?.10f:.12f;p.scale(scale,scale,scale);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack,ItemDisplayContext.NONE,light,overlay,p,b,null,0);
            p.popPose();
        }
    }
}
