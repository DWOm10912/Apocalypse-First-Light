package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.menu.GunMaintenanceMenu;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

public final class GunMaintenanceBenchBlockEntity extends BlockEntity implements Container, MenuProvider {
    private ItemStack maintenanceGunSlot=ItemStack.EMPTY;
    private int originHotbarSlot=-1;
    private UUID originPlayerUUID;
    private boolean originMetadataReady;
    private long attachmentRevision;
    public long attachmentRevision(){return attachmentRevision;}
    /** Server transaction commit preserves the gun's return-origin metadata. */
    public void commitAttachments(ItemStack gun){maintenanceGunSlot=gun;sync();}
    public boolean originMetadataReady(){return originMetadataReady;}
    public int originHotbarSlot(){return originHotbarSlot;}
    public UUID originPlayerUUID(){return originPlayerUUID;}
    public GunMaintenanceBenchBlockEntity(BlockPos pos, BlockState state) { super(AflBlockEntities.GUN_MAINTENANCE_BENCH.get(),pos,state); }
    public static boolean accepts(ItemStack stack) {
        var id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return stack.getCount()==1 && stack.getItem() instanceof NativeGunItem
                && id!=null && id.getNamespace().equals("apocalypse_firstlight");
    }
    public boolean insertFrom(Player player,int slot) {
        if(level==null || level.isClientSide || !stillValid(player) || slot<0 || slot>8 || !isEmpty())return false;
        var stack=player.getInventory().getItem(slot);
        if(!accepts(stack))return false;
        maintenanceGunSlot=player.getInventory().removeItemNoUpdate(slot);
        originHotbarSlot=slot;originPlayerUUID=player.getUUID();originMetadataReady=true;
        player.getInventory().setChanged();sync();return true;
    }
    public boolean takeBack(Player player) {
        if(level==null || level.isClientSide || !stillValid(player) || isEmpty())return false;
        var inventory=player.getInventory();int target=-1;
        if(player.getUUID().equals(originPlayerUUID) && originHotbarSlot>=0 && originHotbarSlot<9
                && inventory.getItem(originHotbarSlot).isEmpty())target=originHotbarSlot;
        if(target<0)for(int i=0;i<36;i++)if(inventory.getItem(i).isEmpty()){target=i;break;}
        var stack=maintenanceGunSlot;
        maintenanceGunSlot=ItemStack.EMPTY;
        if(target>=0)inventory.setItem(target,stack);
        else if(player.drop(stack,false)==null){maintenanceGunSlot=stack;return false;}
        originHotbarSlot=-1;originPlayerUUID=null;inventory.setChanged();sync();return true;
    }
    private void sync() {
        if(level!=null&&!level.isClientSide)attachmentRevision++;
        setChanged();if(level!=null && !level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);
    }
    @Override public int getContainerSize(){return 1;}
    @Override public int getMaxStackSize(){return 1;}
    @Override public boolean isEmpty(){return maintenanceGunSlot.isEmpty();}
    @Override public ItemStack getItem(int slot){return slot==0?maintenanceGunSlot:ItemStack.EMPTY;}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot==0 && accepts(stack);}
    @Override public ItemStack removeItem(int slot,int count){return count>0?removeItemNoUpdate(slot):ItemStack.EMPTY;}
    @Override public ItemStack removeItemNoUpdate(int slot){
        if(slot!=0)return ItemStack.EMPTY;
        var result=maintenanceGunSlot;maintenanceGunSlot=ItemStack.EMPTY;originHotbarSlot=-1;originPlayerUUID=null;sync();return result;
    }
    @Override public void setItem(int slot,ItemStack stack){
        if(slot!=0 || (!stack.isEmpty()&&!accepts(stack)))return;
        // Slot packets carry no ownership. Wait for the matching full BE snapshot after a change.
        if(!ItemStack.matches(maintenanceGunSlot,stack))originMetadataReady=false;
        maintenanceGunSlot=stack; if(stack.isEmpty()){originHotbarSlot=-1;originPlayerUUID=null;}sync();
    }
    @Override public void clearContent(){removeItemNoUpdate(0);}
    @Override public boolean stillValid(Player player){return !isRemoved() && level!=null && player.level()==level && player.isAlive()
            && level.getBlockEntity(worldPosition)==this && player.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)<=25 && complete();}
    private boolean complete(){
        var facing=getBlockState().getValue(com.antaurora.apofirstlight.block.StaticWorkstationBlock.FACING);
        for(var part:com.antaurora.apofirstlight.block.StaticWorkstationBlock.Part.values()){
            var s=level.getBlockState(com.antaurora.apofirstlight.block.StaticWorkstationBlock.partPosition(worldPosition,facing,part));
            if(!s.is(getBlockState().getBlock())||s.getValue(com.antaurora.apofirstlight.block.StaticWorkstationBlock.FACING)!=facing
                    ||s.getValue(com.antaurora.apofirstlight.block.StaticWorkstationBlock.PART)!=part)return false;
        }return true;
    }
    @Override public Component getDisplayName(){return Component.translatable("screen.apocalypse_firstlight.gun_maintenance");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return new GunMaintenanceMenu(id,inventory,this);}
    @Override protected void saveAdditional(CompoundTag tag){
        super.saveAdditional(tag);tag.put("MaintenanceGun",maintenanceGunSlot.save(new CompoundTag()));
        tag.putLong("AttachmentRevision",attachmentRevision);
        tag.putInt("OriginHotbarSlot",originHotbarSlot);if(originPlayerUUID!=null)tag.putUUID("OriginPlayerUUID",originPlayerUUID);
    }
    @Override public void load(CompoundTag tag){
        super.load(tag);var stack=ItemStack.of(tag.getCompound("MaintenanceGun"));
        attachmentRevision=tag.getLong("AttachmentRevision");
        maintenanceGunSlot=accepts(stack)?stack:ItemStack.EMPTY;
        originHotbarSlot=tag.contains("OriginHotbarSlot")?tag.getInt("OriginHotbarSlot"):-1;
        originPlayerUUID=tag.hasUUID("OriginPlayerUUID")?tag.getUUID("OriginPlayerUUID"):null;
        // A complete snapshot with no UUID is confirmed legacy data, not a pending packet.
        originMetadataReady=true;
    }
    @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(worldPosition).inflate(2);}
}
