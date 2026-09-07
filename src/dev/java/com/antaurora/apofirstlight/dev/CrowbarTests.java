package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrowbarTests {
    @GameTest(template = "network_empty", timeoutTicks = 60, batch = "crowbar")
    public static void crowbarPropertiesAndHit(GameTestHelper h) {
        var stack = new ItemStack(AflItems.CROWBAR.get());
        h.assertTrue(stack.getMaxStackSize() == 1 && stack.getMaxDamage() == 480, "stack and durability");
        h.assertTrue(!stack.isRepairable() && !stack.getItem().isValidRepairItem(stack, new ItemStack(Items.IRON_INGOT)), "repair disabled");
        h.assertTrue(stack.getAttributeModifiers(EquipmentSlot.OFFHAND).isEmpty(), "no offhand attributes");
        // Forge FakePlayer.tick() is empty: use Vanilla's ticking mock for equipment and recharge.
        var player = h.makeMockSurvivalPlayer();
        player.getAbilities().instabuild = false;
        player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2, 2.5)));
        player.setItemSlot(EquipmentSlot.MAINHAND, stack);
        for (int i = 0; i < 20; i++) player.tick();
        h.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_DAMAGE) - 6) < 0.001, "total damage 6");
        h.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_SPEED) - 20.0 / 13) < 0.001, "13 tick charge");
        h.assertTrue(Math.abs(player.getAttributeValue(ForgeMod.ENTITY_REACH.get()) - 3) < 0.001, "reach 3");
        var cow = h.spawn(EntityType.COW, new BlockPos(2, 2, 4));
        cow.setNoAi(true);
        float health = cow.getHealth();
        player.attack(cow);
        h.assertTrue(Math.abs(health - cow.getHealth() - 6) < 0.01, "full melee hit 6");
        h.assertTrue(stack.getDamageValue() == 1, "successful hit costs 1");
        cow.setInvulnerable(true);
        player.attack(cow);
        h.assertTrue(stack.getDamageValue() == 1, "rejected hit costs nothing");
        cow.discard();
        h.succeed();
    }
}
