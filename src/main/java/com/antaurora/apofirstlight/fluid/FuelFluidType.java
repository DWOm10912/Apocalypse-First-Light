package com.antaurora.apofirstlight.fluid;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidType;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.function.Consumer;

/**
 * Fuel fluids V1 (docs/gameplay/fuel_fluids_v1.md): gasoline and diesel. Carried only in containers (tanks, pipes, the
 * dispenser): no bucket; source blocks only by command. Lighter than water and do not put out fire; swimming and
 * drowning as in water. Textures tools/build-fuel-fluids-v1.mjs (animated, LabPBR _s / _n); a short coloured fog under
 * the surface.
 */
public final class FuelFluidType extends FluidType {
    private final String name;
    private final Vector3f fogColor;
    private final float fogEnd;

    /** density kg/m3, viscosity (water 1000), fog colour 0xRRGGBB, fog end in blocks. */
    public FuelFluidType(String name, int density, int viscosity, double motionScale, int fogColor, float fogEnd) {
        super(Properties.create().descriptionId("fluid.apocalypse_firstlight." + name)
                .fallDistanceModifier(0.0F).canExtinguish(false).canConvertToSource(false).supportsBoating(true).canHydrate(false)
                .canPushEntity(true).canSwim(true).canDrown(true).density(density).viscosity(viscosity).motionScale(motionScale)
                .temperature(295)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY));
        this.name = name;
        this.fogColor = new Vector3f((fogColor >> 16 & 0xFF) / 255.0F, (fogColor >> 8 & 0xFF) / 255.0F, (fogColor & 0xFF) / 255.0F);
        this.fogEnd = fogEnd;
    }

    /**
     * Called by FluidType's own constructor, before this class's fields are set: everything that reads them (name, fog)
     * is read when asked, not captured here (2026-10-04: the first version built "fluid/null_still" and the fluids showed
     * the missing texture).
     */
    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return new ResourceLocation(ApocalypseFirstLight.MOD_ID, "fluid/" + name + "_still");
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return new ResourceLocation(ApocalypseFirstLight.MOD_ID, "fluid/" + name + "_flow");
            }

            @Override
            public int getTintColor() {
                return 0xFFFFFFFF;
            }

            @Override
            public @NotNull Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance,
                                                    float darkenWorldAmount, Vector3f fluidFogColor) {
                return new Vector3f(fogColor);
            }

            @Override
            public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance, float partialTick,
                                        float nearDistance, float farDistance, FogShape shape) {
                RenderSystem.setShaderFogStart(0.0F);
                RenderSystem.setShaderFogEnd(fogEnd);
                RenderSystem.setShaderFogShape(FogShape.SPHERE);
            }
        });
    }
}
