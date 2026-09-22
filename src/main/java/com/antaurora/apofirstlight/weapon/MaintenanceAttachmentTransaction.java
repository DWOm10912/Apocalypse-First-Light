package com.antaurora.apofirstlight.weapon;

import com.antaurora.apofirstlight.menu.GunMaintenanceMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Bench identity/revision adapter for the shared server-thread attachment transaction. */
public final class MaintenanceAttachmentTransaction {
    public static boolean valid(ServerPlayer player, MaintenanceActionRequest r) {
        if(player.isSpectator()||!player.isAlive()||!(player.containerMenu instanceof GunMaintenanceMenu menu)
                ||menu.containerId!=r.containerId()||!menu.stillValid(player)
                ||!menu.bench.getBlockPos().equals(r.bench())||menu.bench.attachmentRevision()!=r.revision())return false;
        var gun=menu.bench.getItem(0);
        return ItemStack.matches(gun,r.expectedGun())
                && AttachmentInteractionCore.valid(player,gun,r.target(),r.sourceSlot(),r.expectedSource());
    }
    public static boolean commit(ServerPlayer player, MaintenanceActionRequest r) {
        if(!valid(player,r))return false;
        var menu=(GunMaintenanceMenu)player.containerMenu;
        return AttachmentInteractionCore.commit(player,menu.bench.getItem(0),r.target(),r.sourceSlot(),r.expectedSource(),
                updated->{menu.bench.commitAttachments(updated);menu.broadcastChanges();});
    }
    private MaintenanceAttachmentTransaction() {}
}
