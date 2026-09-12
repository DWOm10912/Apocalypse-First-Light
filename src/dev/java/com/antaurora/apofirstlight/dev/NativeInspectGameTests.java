package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.weapon.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.*;
import software.bernie.geckolib.animatable.GeoItem;
import java.util.UUID;

@GameTestHolder("apocalypse_firstlight")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class NativeInspectGameTests {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void load(net.minecraftforge.event.level.LevelEvent.Load event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
            level.getStructureManager().getOrCreate(new net.minecraft.resources.ResourceLocation("afl_inspect_tests", "empty"))
                .fillFromWorld(level, new net.minecraft.core.BlockPos(0,300,0), new net.minecraft.core.Vec3i(1,1,1), false,
                    net.minecraft.world.level.block.Blocks.STRUCTURE_VOID);
    }
    @GameTestGenerator public static java.util.Collection<TestFunction> cases() {
        return java.util.List.of(
            new TestFunction("inspect", "afl_inspect_tests:end", "afl_inspect_tests:empty", 140, 0L, true, NativeInspectGameTests::endNoMutationNoReplay),
            new TestFunction("inspect", "afl_inspect_tests:priority", "afl_inspect_tests:empty", 100, 0L, true, NativeInspectGameTests::fireAndReloadPriority),
            new TestFunction("inspect", "afl_inspect_tests:cancel", "afl_inspect_tests:empty", 20, 0L, true, NativeInspectGameTests::cancelIdentitySwapAndUnsupported));
    }
    private static ServerPlayer player(GameTestHelper h) {
        var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "inspect_probe"));
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, new ItemStack(AflItems.BR51_01.get()));
        GeoItem.getOrAssignId(p.getMainHandItem(), h.getLevel());
        NativeGunAmmo.set(p.getMainHandItem(), NativeGunDefinition.BR51_01, 10);
        p.getInventory().setItem(1, new ItemStack(AflItems.ROUND_762MM.get(), 32));
        return p;
    }
    private static void tick(ServerPlayer p) { P901Actions.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p)); }

    public static void endNoMutationNoReplay(GameTestHelper h) {
        var p = player(h); var before = p.getMainHandItem().copy();
        h.assertTrue(P901Actions.operation(p, "inspect"), "starts existing inspect");
        h.assertTrue(!P901Actions.operation(p, "inspect"), "repeat does not restart");
        int duration = NativeGunAnimations.ticks("br51_01", "inspect");
        h.runAfterDelay(duration, () -> {
            tick(p);
            h.assertTrue(!P901Actions.busy(p), "formal animation duration clears lock");
            h.assertTrue(ItemStack.matches(before, p.getMainHandItem()), "inspect changes no weapon data");
            h.assertTrue(p.getInventory().getItem(1).getCount() == 32, "no reserve consumption");
            h.succeed();
        });
    }
    public static void fireAndReloadPriority(GameTestHelper h) {
        var p = player(h);
        h.assertTrue(P901Actions.operation(p, "inspect"), "inspect begins");
        P901Actions.request(p, false, 0);
        h.assertTrue(NativeGunAmmo.read(p.getMainHandItem(), NativeGunDefinition.BR51_01) == 9, "same fire click fires");
        h.assertTrue(!P901Actions.operation(p, "inspect"), "fire blocks inspect");
        h.runAfterDelay(5, () -> {
            tick(p);
            h.assertTrue(P901Actions.operation(p, "inspect"), "inspect after fire");
            P901Actions.request(p, true, 0);
            h.assertTrue(!P901Actions.operation(p, "inspect"), "reload blocks inspect");
        });
        h.runAfterDelay(58, () -> {
            tick(p);
            h.assertTrue(NativeGunAmmo.read(p.getMainHandItem(), NativeGunDefinition.BR51_01) == 20, "normal reload after interrupt");
            h.assertTrue(!P901Actions.busy(p), "reload lock clears");h.succeed();
        });
    }
    public static void cancelIdentitySwapAndUnsupported(GameTestHelper h) {
        var p = player(h);long id = GeoItem.getId(p.getMainHandItem());
        h.assertTrue(P901Actions.operation(p, "inspect"), "start");
        P901Actions.cancelInspect(p, id + 1);
        h.assertTrue(P901Actions.busy(p), "wrong stack id cannot cancel");
        P901Actions.cancelInspect(p, id);
        h.assertTrue(!P901Actions.busy(p), "cancel releases lock");
        h.assertTrue(P901Actions.operation(p, "inspect"), "restart after cancel");
        p.getInventory().selected = 2;tick(p);
        h.assertTrue(!P901Actions.busy(p), "swap to empty cancels");
        p.getInventory().setItem(2, new ItemStack(AflItems.P9_01.get()));
        h.assertTrue(!P901Actions.operation(p, "inspect"), "P9 without inspect is noop");
        h.assertTrue(!NativeGunAnimations.hasClip("br51_01", "missing_probe"), "missing clip safe");
        h.succeed();
    }
}
