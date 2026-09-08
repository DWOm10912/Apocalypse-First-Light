package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.weapon.NativeGunItem;
import net.minecraft.world.item.ItemStack;
import java.util.Map;

public record MaintenanceViewProfile(float scale,double offsetX,double offsetY,double offsetZ,
                                     float rotationX,float rotationY,float rotationZ,
                                     double centerX,double centerY,double centerZ,int clickWidth) {
    // Model forward -Z -> bench +X (screen left); model +Y -> bench +Z (away from player).
    // The lateral axis becomes vertical: the weapon rests on its side, grip toward the viewer.
    private static final MaintenanceViewProfile DEFAULT=new MaintenanceViewProfile(.29f,0,.045,0,0,-80,-90,0,.52,0,224);
    private static final Map<String,MaintenanceViewProfile> PROFILES=Map.of(
            "apocalypse_firstlight:p9_01",new MaintenanceViewProfile(.55f,0,.045,0,0,-80,-90,-.186,.416,.161,145),
            "apocalypse_firstlight:br51_01",DEFAULT);
    public static MaintenanceViewProfile of(ItemStack stack){return stack.getItem() instanceof NativeGunItem gun
            ?PROFILES.getOrDefault(gun.definition().id().toString(),DEFAULT):DEFAULT;}
    public Map<String,MaintenanceInteractionAnchor> interactionAnchors(){return Map.of();}
}
