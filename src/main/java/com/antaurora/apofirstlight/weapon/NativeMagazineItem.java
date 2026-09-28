package com.antaurora.apofirstlight.weapon;

import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;

/** Magazine-specific compatibility and presentation; capacity comes from the actual attachment. */
public final class NativeMagazineItem extends Item implements NativeAttachment {
    private final String gunId;
    private final int rounds;
    private final java.util.Set<String> bones;
    private final boolean subtree;
    private final float itemLift, itemTilt;
    /** P9-01 28-round Extended Magazine V2: replaces the V2 rig's loaded, reload (mag_out) and dropped magazine bones;
     * the Pure Mesh asset bakes the 22 deg grip tilt, which the standalone item presentation takes back out. */
    public NativeMagazineItem(){this("p9_01",28,java.util.Set.of("magazine","reload_magazine","empty_old_mag"),false,3.58f,22f);}
    public NativeMagazineItem(String gunId,int rounds,java.util.Set<String> bones,boolean subtree,float itemLift){this(gunId,rounds,bones,subtree,itemLift,0);}
    public NativeMagazineItem(String gunId,int rounds,java.util.Set<String> bones,boolean subtree,float itemLift,float itemTilt){
        super(new Properties().stacksTo(1));this.gunId="apocalypse_firstlight:"+gunId;
        this.rounds=rounds;this.bones=java.util.Set.copyOf(bones);this.subtree=subtree;this.itemLift=itemLift;this.itemTilt=itemTilt;
    }
    @Override public Slot slot(){return Slot.MAGAZINE;}
    public boolean accepts(NativeGunDefinition gun){return gun.id().toString().equals(gunId);}
    public int capacity(){return rounds;}
    public net.minecraft.resources.ResourceLocation compatibleGun(){return new net.minecraft.resources.ResourceLocation(gunId);}
    public boolean replacesBone(String bone){return bones.contains(bone);}
    public boolean replacesSubtree(){return subtree;}
    public float itemLift(){return itemLift;}
    /** Degrees about X that undo an asset's baked mount tilt for inventory / dropped / held item presentation. */
    public float itemTilt(){return itemTilt;}
    @Override public void appendHoverText(ItemStack stack,net.minecraft.world.level.Level level,java.util.List<Component> lines,TooltipFlag flag){
        String key="tooltip."+getDescriptionId().substring("item.".length());
        com.antaurora.apofirstlight.tooltip.AflEquipmentTooltip.attachment(lines,stack,key+".description");
    }
    @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer){
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions(){
            private net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer renderer;
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer(){
                if(renderer==null)renderer=new com.antaurora.apofirstlight.weapon.client.NativeMagazineRendering.ItemRenderer();
                return renderer;
            }
        });
    }
}
