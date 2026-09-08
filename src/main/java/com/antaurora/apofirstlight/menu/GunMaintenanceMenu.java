package com.antaurora.apofirstlight.menu;

import com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity;
import com.antaurora.apofirstlight.registry.AflMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class GunMaintenanceMenu extends AbstractContainerMenu {
    public static final int RETURN_GUN=9, TAKE_GUN=10;
    public final GunMaintenanceBenchBlockEntity bench;
    /** BE update tags already carry the complete origin metadata to every nearby viewer. */
    public GunMaintenanceBenchBlockEntity synchronizedBench(){
        var level=bench.getLevel();
        if(level!=null&&level.isClientSide&&level.getBlockEntity(bench.getBlockPos()) instanceof GunMaintenanceBenchBlockEntity live)return live;
        return bench;
    }
    public int originSlotFor(java.util.UUID viewer){
        var b=synchronizedBench();int slot=b.originHotbarSlot();
        return b.originMetadataReady()&&!b.isEmpty()&&viewer.equals(b.originPlayerUUID())&&slot>=0&&slot<9?slot:-1;
    }
    public boolean canShowTakeButton(java.util.UUID viewer){
        var b=synchronizedBench();return b.originMetadataReady()&&!b.isEmpty()&&!viewer.equals(b.originPlayerUUID());
    }
    public GunMaintenanceMenu(int id,Inventory inventory,FriendlyByteBuf data){this(id,inventory,clientSnapshot(inventory,data.readBlockPos()));}
    private static GunMaintenanceBenchBlockEntity clientSnapshot(Inventory inventory,net.minecraft.core.BlockPos pos){
        // Opening can arrive before the chunk/BE packet. The real menu slot synchronizes its server contents.
        var level=inventory.player.level();
        if(level.getBlockEntity(pos) instanceof GunMaintenanceBenchBlockEntity bench)return bench;
        var snapshot=new GunMaintenanceBenchBlockEntity(pos,com.antaurora.apofirstlight.registry.AflBlocks.GUN_MAINTENANCE_BENCH.get().defaultBlockState());
        snapshot.setLevel(level);return snapshot;
    }
    public GunMaintenanceMenu(int id,Inventory inventory,GunMaintenanceBenchBlockEntity bench){
        super(AflMenus.GUN_MAINTENANCE.get(),id);this.bench=bench;
        // Real synchronized slots, intentionally not exposed as a drag/drop inventory.
        for(int i=0;i<9;i++)addSlot(new Slot(inventory,i,-1000,-1000));
        addSlot(new Slot(bench,0,-1000,-1000));
    }
    @Override public boolean stillValid(Player player){return bench!=null && bench.stillValid(player);}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    @Override public void clicked(int slot,int button,ClickType type,Player player){ }
    @Override public boolean clickMenuButton(Player player,int button){
        if(player.level().isClientSide || player.containerMenu!=this || !stillValid(player))return false;
        // Vanilla button packets are bound to this player's current containerId. This server Menu owns
        // the root position/dimension; stillValid rejects another world, removed root, or excess distance.
        if(button==RETURN_GUN&&!player.getUUID().equals(bench.originPlayerUUID()))return false;
        boolean moved=button==RETURN_GUN||button==TAKE_GUN?bench.takeBack(player):button>=0&&button<9&&bench.insertFrom(player,button);
        if(moved){broadcastChanges();player.inventoryMenu.broadcastChanges();}return moved;
    }
}
