package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.world.LightningStrikes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Lightning V1 (world/LightningStrikes, mixin LightningBoltLifeMixin): a strike kills a zombie in full diamond armour, and the
 * server keeps the bolt until the client's flash has played out.
 */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LightningGameTests {
    private LightningGameTests() {
    }

    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void lightningKillsArmouredZombie(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        zombie.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(helper.getLevel());
        if (bolt == null) throw new IllegalStateException("no lightning bolt");
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 2, 2.5));
        bolt.moveTo(at);
        helper.getLevel().addFreshEntity(bolt);
        helper.runAfterDelay(3, () -> {
            if (zombie.isAlive()) helper.fail("zombie survived the strike: health " + zombie.getHealth());
            if (bolt.isRemoved()) helper.fail("bolt removed after 3 ticks (should stay " + LightningStrikes.MIN_TICKS + ")");
        });
        helper.runAfterDelay(LightningStrikes.MIN_TICKS - 4, () -> {
            if (bolt.isRemoved()) helper.fail("bolt removed before " + LightningStrikes.MIN_TICKS + " ticks: " + bolt.tickCount);
        });
        helper.runAfterDelay(LightningStrikes.MIN_TICKS + 45, () -> {
            if (!bolt.isRemoved()) helper.fail("bolt still there after " + bolt.tickCount + " ticks");
            helper.succeed();
        });
    }
}
