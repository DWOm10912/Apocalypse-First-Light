package com.antaurora.apofirstlight.weapon;

import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;

/**
 * Magazine-specific compatibility and presentation; capacity comes from the actual attachment. For a gun that loads from
 * magazine items (its mount names a standard magazine, {@link NativeMagazineMount#magazineItems}) the magazine also holds
 * rounds: {@link NativeMagazines#rounds}, shown as the item bar and a tooltip line, and loaded by right-clicking with it
 * (NativeMagazineLoading; docs/native_guns/magazine_system_v1.md).
 */
public final class NativeMagazineItem extends Item implements NativeAttachment {
    private final String gunId;
    private final int rounds;
    private final java.util.Set<String> bones;
    private final boolean subtree;
    private final float itemLift, itemTilt;
    private final String textureAsset;
    /** P9-01 28-round Extended Magazine V2: replaces the V2 rig's loaded, reload (mag_out) and dropped magazine bones;
     * the Pure Mesh asset bakes the 22 deg grip tilt, which the standalone item presentation takes back out. */
    public NativeMagazineItem(){this("p9_01",28,java.util.Set.of("magazine","reload_magazine","empty_old_mag"),false,3.58f,22f);}
    public NativeMagazineItem(String gunId,int rounds,java.util.Set<String> bones,boolean subtree,float itemLift){this(gunId,rounds,bones,subtree,itemLift,0);}
    public NativeMagazineItem(String gunId,int rounds,java.util.Set<String> bones,boolean subtree,float itemLift,float itemTilt){
        this(gunId,rounds,bones,subtree,itemLift,itemTilt,null);
    }
    /**
     * {@code textureAsset}: the textures/item/ name the standalone model is drawn with, when it is cut from the gun's own
     * mesh and shares its texture (the P9-01 standard magazine: p9_01_v3_native); null: the item's own name.
     */
    public NativeMagazineItem(String gunId,int rounds,java.util.Set<String> bones,boolean subtree,float itemLift,float itemTilt,String textureAsset){
        super(new Properties().stacksTo(1));this.gunId="apocalypse_firstlight:"+gunId;
        this.rounds=rounds;this.bones=java.util.Set.copyOf(bones);this.subtree=subtree;this.itemLift=itemLift;this.itemTilt=itemTilt;
        this.textureAsset=textureAsset;
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
    /** The texture the standalone model is drawn with (textures/item/&lt;name&gt;.png), or null for the item's own. */
    public String textureAsset(){return textureAsset;}
    /** The gun this magazine fits, when that gun keeps its rounds in magazines (else null: a capacity attachment only). */
    public NativeGunDefinition roundGun(){
        var gun=NativeGunData.get(compatibleGun());
        return gun!=null&&gun.magazineMount()!=null&&gun.magazineMount().magazineItems()?gun:null;
    }
    @Override public boolean isBarVisible(ItemStack stack){return roundGun()!=null&&NativeMagazines.rounds(stack)>0;}
    @Override public int getBarWidth(ItemStack stack){return Math.round(13F*NativeMagazines.rounds(stack)/Math.max(1,rounds));}
    @Override public int getBarColor(ItemStack stack){return 0xD8D2C4;}
    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(hand!=net.minecraft.world.InteractionHand.MAIN_HAND||roundGun()==null)return net.minecraft.world.InteractionResultHolder.pass(stack);
        if(player instanceof net.minecraft.server.level.ServerPlayer server)NativeMagazineLoading.click(server,stack);
        return net.minecraft.world.InteractionResultHolder.consume(stack);
    }
    /** A loading clock left on a magazine nobody is loading (dropped, a lost session) is cleared. */
    @Override public void inventoryTick(ItemStack stack,net.minecraft.world.level.Level level,net.minecraft.world.entity.Entity entity,int slot,boolean selected){
        if(!level.isClientSide&&NativeMagazines.loadingSince(stack)>=0&&!(entity instanceof net.minecraft.server.level.ServerPlayer p
                &&NativeMagazineLoading.loading(p,stack)))NativeMagazines.setLoading(stack,-1);
    }
    /** Rounds and the loading clock change while it is held: no re-equip dip for them. */
    @Override public boolean shouldCauseReequipAnimation(ItemStack a,ItemStack b,boolean changed){
        if(changed||a.getItem()!=b.getItem())return true;
        return !NativeMagazines.tagWithoutRounds(a).equals(NativeMagazines.tagWithoutRounds(b));
    }
    @Override public void appendHoverText(ItemStack stack,net.minecraft.world.level.Level level,java.util.List<Component> lines,TooltipFlag flag){
        String key="tooltip."+getDescriptionId().substring("item.".length());
        com.antaurora.apofirstlight.tooltip.AflEquipmentTooltip.attachment(lines,stack,key+".description");
        if(roundGun()!=null)lines.add(Component.translatable("tooltip.apocalypse_firstlight.magazine.rounds",NativeMagazines.rounds(stack),rounds)
                .withStyle(net.minecraft.ChatFormatting.GRAY));
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
