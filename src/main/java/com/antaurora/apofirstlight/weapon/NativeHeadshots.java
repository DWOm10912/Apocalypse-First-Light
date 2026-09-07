package com.antaurora.apofirstlight.weapon;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.List;
import java.util.Optional;

/** Server hit zones. Exact registry allow-list, never instanceof Zombie. */
public final class NativeHeadshots {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITIES;
    public static final ForgeConfigSpec.DoubleValue MULTIPLIER;
    static {
        var b = new ForgeConfigSpec.Builder();
        ENTITIES = b.comment("Exact entity IDs using the humanoid top head-box profile. Restart server after editing.")
                .defineListAllowEmpty("headshotEntities", List.of("minecraft:zombie"),
                        x -> x instanceof String s && net.minecraft.resources.ResourceLocation.tryParse(s) != null);
        MULTIPLIER = b.defineInRange("headshotMultiplier", 1.5, 1.0, 100.0);
        SPEC = b.build();
    }
    public record Zone(Vec3 point, boolean head) {}
    public static boolean enabled(Entity e) {
        return e instanceof LivingEntity && ENTITIES.get().contains(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).toString());
    }
    public static AABB headBox(AABB b) {
        double insetX=b.getXsize()*.075, insetZ=b.getZsize()*.075;
        return new AABB(b.minX+insetX,b.maxY-Math.min(.5,b.getYsize()*.27),b.minZ+insetZ,
                b.maxX-insetX,b.maxY,b.maxZ-insetZ);
    }
    private static Optional<Vec3> intersect(AABB b, Vec3 start, Vec3 end) {
        return b.contains(start)?Optional.of(start):b.clip(start,end);
    }
    public static Optional<Zone> intersect(AABB bounds, boolean enabled, Vec3 start, Vec3 end) {
        if (!enabled) return intersect(bounds,start,end).map(p->new Zone(p,false));
        AABB head=headBox(bounds);
        AABB body=new AABB(bounds.minX,bounds.minY,bounds.minZ,bounds.maxX,head.minY,bounds.maxZ);
        var h=intersect(head,start,end); var b=intersect(body,start,end);
        if(h.isPresent() && (b.isEmpty() || start.distanceToSqr(h.get())<start.distanceToSqr(b.get())-1e-10))
            return Optional.of(new Zone(h.get(),true));
        return b.map(p->new Zone(p,false));
    }
    private NativeHeadshots() {}
}
