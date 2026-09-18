package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.weapon.NativeGunItem;
import net.minecraft.world.item.ItemStack;
import java.util.Map;

public record MaintenanceViewProfile(float scale,double offsetX,double offsetY,double offsetZ,
                                     float rotationX,float rotationY,float rotationZ,
                                     double centerX,double centerY,double centerZ,int clickWidth) {
    // Model forward -Z -> bench +X (screen left); model +Y -> bench +Z (away from player).
    // The lateral axis becomes vertical: the weapon rests on its side, grip toward the viewer.
    // Exact quarter turn keeps the barrel parallel to the mat's long edge.
    private static final MaintenanceViewProfile DEFAULT=new MaintenanceViewProfile(.29f,0,.045,0,0,-90,-90,0,.52,0,224);
    private static final Map<String,MaintenanceViewProfile> PROFILES=Map.of(
            // Artist V2 runtime Geo is centered near X=-0.024/Y=0.160 in Gecko model units.
            // Keep its established side-on maintenance orientation and dynamic longitudinal centering.
            "apocalypse_firstlight:p9_01",new MaintenanceViewProfile(.55f,0,.045,0,0,-90,-90,-.024,.160,.161,145),
            "apocalypse_firstlight:br51_01",DEFAULT,
            // HR55 source bounds are width 4, height 2.5, offset +0.75; the wider bench hit area
            // keeps its dedicated sight mount comfortably selectable without changing BR51's view.
            "apocalypse_firstlight:hr55",new MaintenanceViewProfile(.34f,0,.045,0,0,-90,-90,0,.75,0,236));
    public static MaintenanceViewProfile of(ItemStack stack){return stack.getItem() instanceof NativeGunItem gun
            ?PROFILES.getOrDefault(gun.definition().id().toString(),DEFAULT):DEFAULT;}
    public Map<String,MaintenanceInteractionAnchor> interactionAnchors(){return Map.of();}
}
