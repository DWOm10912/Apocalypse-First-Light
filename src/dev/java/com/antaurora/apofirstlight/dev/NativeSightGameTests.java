package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.weapon.*;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

@GameTestHolder("apocalypse_firstlight") @PrefixGameTestTemplate(false)
public final class NativeSightGameTests {
    @GameTest(template="network_empty") public static void sightSurvivalTransaction(GameTestHelper h){
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"sight_survival"));
        p.setGameMode(GameType.SURVIVAL);p.setShiftKeyDown(true);
        var gun=new ItemStack(AflItems.P9_01.get());var sight=new ItemStack(AflItems.PISTOL_RED_DOT.get());
        sight.setHoverName(net.minecraft.network.chat.Component.literal("named optic"));
        NativeGunAmmo.set(gun,NativeGunDefinition.P9_01,5);
        p.setItemInHand(InteractionHand.MAIN_HAND,gun);p.setItemInHand(InteractionHand.OFF_HAND,sight);
        h.assertTrue(LegacyAttachmentFixture.exchange(p),"install");
        h.assertTrue(p.getOffhandItem().isEmpty()&&!NativeAttachments.activeSight(gun).isEmpty(),"consume exactly one");
        h.assertTrue(NativeGunAmmo.read(gun,NativeGunDefinition.P9_01)==5,"ammo preserved");
        var saved=ItemStack.of(gun.save(new net.minecraft.nbt.CompoundTag()));
        h.assertTrue(!NativeAttachments.activeSight(saved).isEmpty(),"serialized/drop stack persistence");
        p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(AflItems.PISTOL_RED_DOT.get()));
        h.assertTrue(!LegacyAttachmentFixture.exchange(p)&&p.getOffhandItem().getCount()==1,"occupied slot no loss");
        p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        h.assertTrue(LegacyAttachmentFixture.exchange(p),"detach");
        h.assertTrue(NativeAttachments.storedSight(gun).isEmpty()&&p.getOffhandItem().getHoverName().getString().equals("named optic"),"return original sight");
        h.assertTrue(NativeGunAmmo.read(gun,NativeGunDefinition.P9_01)==5,"detach preserves ammo");
        h.assertTrue(!NativeAttachments.compatible(new ItemStack(AflItems.BR51_01.get()),sight),"rifle incompatible");
        h.succeed();
    }
    @GameTest(template="network_empty") public static void sightCreativeAndRestrictions(GameTestHelper h){
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"sight_creative"));
        p.setGameMode(GameType.CREATIVE);p.setShiftKeyDown(true);
        var gun=new ItemStack(AflItems.P9_01.get());
        p.setItemInHand(InteractionHand.MAIN_HAND,gun);p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(AflItems.PISTOL_RED_DOT.get()));
        h.assertTrue(LegacyAttachmentFixture.exchange(p)&&p.getOffhandItem().getCount()==1,"creative keeps held sight");
        h.assertTrue(!LegacyAttachmentFixture.exchange(p),"cannot duplicate installed slot");
        p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);p.setGameMode(GameType.SPECTATOR);
        h.assertTrue(!LegacyAttachmentFixture.exchange(p),"spectator denied");h.succeed();
    }
}
