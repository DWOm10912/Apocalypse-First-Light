package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.weapon.*;
import com.antaurora.apofirstlight.registry.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.*;

@GameTestHolder("afl_suppressor_tests") @PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class NativeSuppressorTests {
    @GameTestGenerator public static java.util.Collection<TestFunction> cases(){
        var tests=new java.util.ArrayList<TestFunction>();
        add(tests,"sight_survival",NativeSightGameTests::sightSurvivalTransaction);
        add(tests,"sight_creative",NativeSightGameTests::sightCreativeAndRestrictions);
        add(tests,"suppressor_transactions",NativeSuppressorTests::transactions);
        add(tests,"suppressor_creative",NativeSuppressorTests::creative);
        add(tests,"rifle_suppressor",NativeSuppressorTests::rifle);
        add(tests,"rifle_maintenance",MaintenanceAttachmentTests::rifle);
        add(tests,"dual_maintenance",MaintenanceMultiplayerTests::run);
        return tests;
    }
    private static void add(java.util.List<TestFunction> tests,String name,java.util.function.Consumer<GameTestHelper> test){
        tests.add(new TestFunction("suppressor","afl_suppressor_tests:"+name,"afl_workbench_tests:empty",200,0L,true,test));
    }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h,String name){
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),name));
        p.setGameMode(GameType.SURVIVAL);p.containerMenu=p.inventoryMenu;
        p.setPos(h.absolutePos(new net.minecraft.core.BlockPos(4,2,4)).getCenter());
        return p;
    }
    public static void transactions(GameTestHelper h){
        var p=player(h,"suppressor_test");var gun=new ItemStack(AflItems.P9_01.get());
        var d=((NativeGunItem)gun.getItem()).definition();NativeGunAmmo.set(gun,d,7);
        p.setItemInHand(InteractionHand.MAIN_HAND,gun);
        var bare=NativeGunNoise.resolve(gun,d);
        h.assertTrue(bare.radius()==64&&!bare.suppressed()&&bare.fireSound((NativeGunItem)gun.getItem())==AflSounds.P9_01_FIRE.get(),"normal sound and radius");
        p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(AflItems.PISTOL_RED_DOT.get()));
        h.assertTrue(LegacyAttachmentFixture.exchange(p),"red dot install");
        var suppressor=new ItemStack(AflItems.PISTOL_SUPPRESSOR_01.get());suppressor.getOrCreateTag().putString("Marker","exact");
        p.setItemInHand(InteractionHand.OFF_HAND,suppressor);
        var requested=gun.copy();var off=suppressor.copy();int slot=p.getInventory().selected;
        h.assertTrue(LegacyAttachmentFixture.requestMatches(p,slot,requested,off),"valid target");
        h.assertTrue(!LegacyAttachmentFixture.requestMatches(p,9,requested,off),"invalid slot rejected");
        h.assertTrue(LegacyAttachmentFixture.exchange(p)&&p.getOffhandItem().isEmpty(),"survival consumed");
        h.assertTrue(!LegacyAttachmentFixture.requestMatches(p,slot,requested,off),"replay rejected");
        h.assertTrue(!NativeAttachments.activeSight(gun).isEmpty()&&!NativeAttachments.active(gun,NativeAttachment.Slot.MUZZLE).isEmpty(),"both slots");
        var resolved=NativeGunNoise.resolve(gun,d);
        h.assertTrue(resolved.radius()==3&&resolved.suppressed()&&!d.gunshotTinnitus(),"suppressed radius and no tinnitus");
        h.assertTrue(resolved.fireSound((NativeGunItem)gun.getItem())==AflSounds.P9_01_SUPPRESSED.get(),"suppressed sound");
        // Actual native raycast reaches the real NoiseSystem, not a test-side radius substitution.
        NativeGunShot.execute(p,d);
        p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(AflItems.PISTOL_SUPPRESSOR_01.get()));
        h.assertTrue(!LegacyAttachmentFixture.exchange(p)&&p.getOffhandItem().getCount()==1,"occupied rejected");
        h.assertTrue(!NativeAttachments.compatible(new ItemStack(AflItems.BR51_01.get()),p.getOffhandItem()),"rifle incompatible");
        var saved=ItemStack.of(gun.save(new CompoundTag()));
        h.assertTrue(ItemStack.matches(gun,saved)&&NativeGunAmmo.read(saved,d)==7,"serialize all NBT and ammo");
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(8,2,8));
        h.getLevel().setBlock(pos,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(),3);
        var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)h.getLevel().getBlockEntity(pos);
        chest.setItem(0,saved);var chestTag=chest.saveWithoutMetadata();chest.clearContent();chest.load(chestTag);
        h.assertTrue(ItemStack.matches(gun,chest.getItem(0)),"chest save/load");
        var drop=new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),p.getX(),p.getY(),p.getZ(),chest.removeItemNoUpdate(0));
        var dropTag=new CompoundTag();drop.save(dropTag);
        var restored=new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),p.getX(),p.getY(),p.getZ(),ItemStack.EMPTY);restored.load(dropTag);
        h.assertTrue(ItemStack.matches(gun,restored.getItem()),"drop entity save/load");
        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        var pickup=restored.getItem().copy();h.assertTrue(p.getInventory().add(pickup)&&pickup.isEmpty(),"pickup inventory");
        var picked=p.getInventory().items.stream().filter(s->s.is(AflItems.P9_01.get())).findFirst().orElseThrow();
        h.assertTrue(ItemStack.matches(gun,picked),"pickup preserves both");
        p.setItemInHand(InteractionHand.MAIN_HAND,gun);
        h.assertTrue(LegacyAttachmentFixture.exchange(p)&&p.getOffhandItem().is(AflItems.PISTOL_RED_DOT.get()),"sight first");
        h.assertTrue(!NativeAttachments.active(gun,NativeAttachment.Slot.MUZZLE).isEmpty(),"muzzle untouched");
        p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        h.assertTrue(LegacyAttachmentFixture.exchange(p)&&p.getOffhandItem().getOrCreateTag().getString("Marker").equals("exact"),"muzzle returned with NBT");
        h.assertTrue(NativeGunNoise.resolve(gun,d).radius()==64,"bare radius restored");
        com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[SUPPRESSOR TEST] PASS transactions dual slots sound 64->3 chest drop pickup serialization");
        h.succeed();
    }
    public static void rifle(GameTestHelper h){
        var p=player(h,"rifle_suppressor");var gun=new ItemStack(AflItems.BR51_01.get());
        var item=(NativeGunItem)gun.getItem();var d=item.definition();
        p.setItemInHand(InteractionHand.MAIN_HAND,gun);
        h.assertTrue(NativeGunNoise.resolve(gun,d).radius()==112,"rifle bare noise");
        var normal=NativeGunNoise.resolve(gun,d).fireSound(item);
        p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(AflItems.RIFLE_SUPPRESSOR_01.get()));
        var expected=gun.copy();var source=p.getOffhandItem().copy();
        h.assertTrue(NativeAttachments.supportsSlot(gun,NativeAttachment.Slot.MUZZLE),"data driven muzzle");
        h.assertTrue(!NativeAttachments.supportsSlot(gun,NativeAttachment.Slot.SIGHT),"no new rifle sight");
        h.assertTrue(LegacyAttachmentFixture.exchange(p)&&p.getOffhandItem().isEmpty(),"rifle quick install consumes");
        h.assertTrue(!LegacyAttachmentFixture.requestMatches(p,p.getInventory().selected,expected,source),"rifle replay rejected");
        var result=NativeGunNoise.resolve(gun,d);
        h.assertTrue(result.suppressed()&&result.radius()==6,"rifle 112 -> 6");
        h.assertTrue(result.fireSound(item)==AflSounds.BR51_01_SUPPRESSED.get()&&normal!=result.fireSound(item),"gun defined sound");
        NativeGunShot.execute(p,d);
        var restored=ItemStack.of(gun.save(new CompoundTag()));
        h.assertTrue(ItemStack.matches(gun,restored)&&NativeAttachments.active(restored,NativeAttachment.Slot.MUZZLE).is(AflItems.RIFLE_SUPPRESSOR_01.get()),"rifle persistence");
        h.assertTrue(!NativeAttachments.compatible(new ItemStack(AflItems.P9_01.get()),source),"rifle accessory not P9 compatible");
        h.assertTrue(LegacyAttachmentFixture.exchange(p)&&p.getOffhandItem().is(AflItems.RIFLE_SUPPRESSOR_01.get()),"rifle detach return");
        h.assertTrue(NativeGunNoise.resolve(gun,d).fireSound(item)==normal,"bare sound restored");
        h.succeed();
    }
    public static void creative(GameTestHelper h){
        var p=player(h,"suppressor_creative");p.setGameMode(GameType.CREATIVE);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AflItems.P9_01.get()));
        p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(AflItems.PISTOL_SUPPRESSOR_01.get()));
        h.assertTrue(LegacyAttachmentFixture.exchange(p)&&p.getOffhandItem().getCount()==1,"creative retains");
        h.assertTrue(!LegacyAttachmentFixture.exchange(p),"creative occupied");
        p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);p.setGameMode(GameType.SPECTATOR);
        h.assertTrue(!LegacyAttachmentFixture.exchange(p),"spectator blocked");
        h.succeed();
    }
}
