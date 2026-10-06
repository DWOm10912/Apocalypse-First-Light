package com.antaurora.apofirstlight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.Vec3;

/**
 * A droplet of liquid (2026-10-05; first user: the fuel nozzle's jet, FuelNozzleJets): splashed off where a jet lands, shed
 * from a jet's falling end, or dripping from a nozzle. A small square of the liquid's own sprite (block atlas, like a
 * block-breaking particle) in the liquid's tint, a little translucent, falling under gravity; gone when it lands. 2026-10-05:
 * a round drop sprite (textures/block/liquid_drop, tools/build-liquid-decals-v1.mjs) in the liquid's tint instead of a
 * quarter of the liquid's square sprite, which showed as little squares under shaders (user).
 * Velocities in blocks a second. Spawned directly (no particle type: client-only effect).
 */
public final class LiquidDroplet extends TextureSheetParticle {
    private static final net.minecraft.resources.ResourceLocation DROP = new net.minecraft.resources.ResourceLocation("apocalypse_firstlight", "block/liquid_drop");

    private LiquidDroplet(ClientLevel level, Vec3 at, Vec3 velocity, TextureAtlasSprite sprite, int rgb, float size) {
        super(level, at.x, at.y, at.z);
        setSprite(sprite);
        xd = velocity.x / 20;
        yd = velocity.y / 20;
        zd = velocity.z / 20;
        gravity = 1.0F;
        friction = 0.98F;
        hasPhysics = true;
        quadSize = size;
        lifetime = 24 + random.nextInt(16);
        rCol = (rgb >> 16 & 255) / 255.0F;
        gCol = (rgb >> 8 & 255) / 255.0F;
        bCol = (rgb & 255) / 255.0F;
        alpha = 0.8F;
    }

    /** A droplet of {@code fluid}: its still sprite and tint (the particle providers of the fuel drips). */
    public static LiquidDroplet of(ClientLevel level, Vec3 at, Vec3 velocity, net.minecraft.world.level.material.Fluid fluid, float size) {
        net.minecraftforge.fluids.FluidStack stack = new net.minecraftforge.fluids.FluidStack(fluid, 1000);
        net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions client = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid);
        return new LiquidDroplet(level, at, velocity, drop(), client.getTintColor(stack) & 0xFFFFFF, size);
    }

    /** Spawns a droplet of {@code fluid} ({@link #of}). */
    public static void spawnOf(ClientLevel level, Vec3 at, Vec3 velocity, net.minecraft.world.level.material.Fluid fluid, float size) {
        Minecraft.getInstance().particleEngine.add(of(level, at, velocity, fluid, size));
    }

    /** A droplet in {@code rgb} (the sprite argument is no longer used: every droplet is the round drop). */
    public static void spawn(ClientLevel level, Vec3 at, Vec3 velocity, TextureAtlasSprite sprite, int rgb, float size) {
        Minecraft.getInstance().particleEngine.add(new LiquidDroplet(level, at, velocity, drop(), rgb, size));
    }

    private static TextureAtlasSprite drop() {
        return Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(DROP);
    }

    @Override
    public void tick() {
        super.tick();
        if (onGround) remove();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.TERRAIN_SHEET;
    }
}
