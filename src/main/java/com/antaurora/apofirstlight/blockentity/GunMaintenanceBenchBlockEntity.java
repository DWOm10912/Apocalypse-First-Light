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
    /** Where the gun came from: a hotbar index (0..8) or a grid entry (GridSource), -1 for none; the entry's turn. */
    private int originSource=-1;
    private boolean originRotated;
    private UUID originPlayerUUID;
    private boolean originMetadataReady;
    private long attachmentRevision;
    public long attachmentRevision(){return attachmentRevision;}
    /** Server transaction commit preserves the gun's return-origin metadata. */
    public void commitAttachments(ItemStack gun){maintenanceGunSlot=gun;sync();}
    public boolean originMetadataReady(){return originMetadataReady;}
    public int originSource(){return originSource;}
    public UUID originPlayerUUID(){return originPlayerUUID;}
    public GunMaintenanceBenchBlockEntity(BlockPos pos, BlockState state) { super(AflBlockEntities.GUN_MAINTENANCE_BENCH.get(),pos,state); }
    public static boolean accepts(ItemStack stack) {
        // Type check for persisted/synchronized contents too; refusal must not erase a stored gun.
        return stack.getCount()==1 && stack.getItem() instanceof NativeGunItem;
    }
    private boolean refused(Player player,ItemStack stack){
        if(com.antaurora.apofirstlight.weapon.AttachmentModificationPolicy.allowed(stack))return false;
        player.displayClientMessage(((NativeGunItem)stack.getItem()).inspectionRefusalReason(),true);
        return true;
    }
    public boolean insertFrom(Player player,int slot) {
        if(level==null || level.isClientSide || !stillValid(player) || slot<0 || slot>8 || !isEmpty())return false;
        var stack=player.getInventory().getItem(slot);
        if(!accepts(stack)||refused(player,stack))return false;
        maintenanceGunSlot=player.getInventory().removeItemNoUpdate(slot);
        originSource=slot;originRotated=false;originPlayerUUID=player.getUUID();originMetadataReady=true;
        player.getInventory().setChanged();sync();return true;
    }
    /**
     * The bench's gun picker (docs/native_guns/attachments/gun_maintenance_attachment_interaction_v1.md): a gun from a
     * hotbar index (the weapon positions and the hand) or a grid entry (GridSource), only while it is still the expected one.
     */
    public boolean insertFrom(Player player,int source,ItemStack expected) {
        if(level==null || level.isClientSide || !stillValid(player) || !isEmpty())return false;
        if(!com.antaurora.apofirstlight.inventory.grid.GridSource.is(source))
            return source>=0&&source<9&&ItemStack.matches(player.getInventory().getItem(source),expected)&&insertFrom(player,source);
        var found=com.antaurora.apofirstlight.inventory.grid.GridSource.find(player,source);
        var cell=com.antaurora.apofirstlight.inventory.grid.GridSource.cell(source);
        if(found==null||cell==null)return false;
        var stack=found.entry().stack();
        if(!ItemStack.matches(stack,expected)||!accepts(stack)||refused(player,stack))return false;
        found.view().get(cell.target()).remove(found.entry());
        found.view().commit();
        maintenanceGunSlot=stack;
        originSource=source;originRotated=found.entry().rotated();originPlayerUUID=player.getUUID();originMetadataReady=true;
        sync();return true;
    }
    /**
     * The gun back to whoever takes it: its owner gets it where it came from while that place is free and still takes it;
     * otherwise the normal pickup order (its weapon position by role, then pockets, rig, backpack), and at the feet when
     * nothing takes it. The bench empties only once the gun is somewhere.
     */
    public boolean takeBack(Player player) {
        if(level==null || level.isClientSide || !stillValid(player) || isEmpty())return false;
        var inventory=player.getInventory();
        var stack=maintenanceGunSlot;
        maintenanceGunSlot=ItemStack.EMPTY;
        if(!(player.getUUID().equals(originPlayerUUID)&&backToOrigin(player,stack))){
            inventory.add(stack);
            if(!stack.isEmpty()&&player.drop(stack,false)==null){maintenanceGunSlot=stack;return false;}
        }
        originSource=-1;originRotated=false;originPlayerUUID=null;inventory.setChanged();sync();return true;
    }
    private boolean backToOrigin(Player player,ItemStack stack){
        var inventory=player.getInventory();
        if(originSource>=0&&originSource<9){
            if(!inventory.getItem(originSource).isEmpty()
                    ||com.antaurora.apofirstlight.inventory.PlayerStorageCapacity.rejects(inventory,originSource,stack))return false;
            inventory.setItem(originSource,stack.split(stack.getCount()));
            return true;
        }
        var cell=com.antaurora.apofirstlight.inventory.grid.GridSource.cell(originSource);
        if(cell==null||!com.antaurora.apofirstlight.inventory.grid.PlayerGrids.allowedInGrid(stack))return false;
        var view=com.antaurora.apofirstlight.inventory.grid.PlayerGrids.view(player);
        var storage=view.get(cell.target());
        if(storage==null||!storage.fits(stack,cell.section(),cell.x(),cell.y(),originRotated,null))return false;
        // place keeps the stack it is given: hand all of it over
        storage.place(stack.split(stack.getCount()),cell.section(),cell.x(),cell.y(),originRotated);
        view.commit();
        return true;
    }
    private void sync() {
        if(level!=null&&!level.isClientSide)attachmentRevision++;
        setChanged();if(level!=null && !level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);
    }
    @Override public int getContainerSize(){return 1;}
    @Override public int getMaxStackSize(){return 1;}
    @Override public boolean isEmpty(){return maintenanceGunSlot.isEmpty();}
    @Override public ItemStack getItem(int slot){return slot==0?maintenanceGunSlot:ItemStack.EMPTY;}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot==0 && com.antaurora.apofirstlight.weapon.AttachmentModificationPolicy.allowed(stack);}
    @Override public ItemStack removeItem(int slot,int count){return count>0?removeItemNoUpdate(slot):ItemStack.EMPTY;}
    @Override public ItemStack removeItemNoUpdate(int slot){
        if(slot!=0)return ItemStack.EMPTY;
        var result=maintenanceGunSlot;maintenanceGunSlot=ItemStack.EMPTY;originSource=-1;originPlayerUUID=null;sync();return result;
    }
    @Override public void setItem(int slot,ItemStack stack){
        if(slot!=0 || (!stack.isEmpty()&&!accepts(stack)))return;
        // Slot packets carry no ownership. Wait for the matching full BE snapshot after a change.
        if(!ItemStack.matches(maintenanceGunSlot,stack))originMetadataReady=false;
        maintenanceGunSlot=stack; if(stack.isEmpty()){originSource=-1;originPlayerUUID=null;}sync();
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
        tag.putInt("OriginSource",originSource);tag.putBoolean("OriginRotated",originRotated);
        if(originPlayerUUID!=null)tag.putUUID("OriginPlayerUUID",originPlayerUUID);
    }
    @Override public void load(CompoundTag tag){
        super.load(tag);var stack=ItemStack.of(tag.getCompound("MaintenanceGun"));
        attachmentRevision=tag.getLong("AttachmentRevision");
        maintenanceGunSlot=accepts(stack)?stack:ItemStack.EMPTY;
        // OriginHotbarSlot: saves from before the gun picker (a hotbar index then)
        originSource=tag.contains("OriginSource")?tag.getInt("OriginSource"):tag.contains("OriginHotbarSlot")?tag.getInt("OriginHotbarSlot"):-1;
        originRotated=tag.getBoolean("OriginRotated");
        originPlayerUUID=tag.hasUUID("OriginPlayerUUID")?tag.getUUID("OriginPlayerUUID"):null;
        // A complete snapshot with no UUID is confirmed legacy data, not a pending packet.
        originMetadataReady=true;
    }
    @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(worldPosition).inflate(2);}
}
