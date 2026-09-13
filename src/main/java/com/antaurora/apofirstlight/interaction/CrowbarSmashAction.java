package com.antaurora.apofirstlight.interaction;

import com.antaurora.apofirstlight.block.VendingMachineBlock;
import com.antaurora.apofirstlight.blockentity.VendingMachineBlockEntity;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Server-owned reservation; the client cannot submit a hit or a broken block state. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class CrowbarSmashAction {
    public static final int START=0, IMPACT=1, CANCEL=2, END=3;
    private static final Map<UUID,Pending> ACTIVE=new HashMap<>();
    private static final class Pending {
        final UUID id=UUID.randomUUID();
        final ServerPlayer player;final VendingMachineBlockEntity machine;final ItemStack tool;
        final int slot;final long start;
        boolean committed;
        Pending(ServerPlayer p,VendingMachineBlockEntity be){player=p;machine=be;tool=p.getMainHandItem();slot=p.getInventory().selected;start=p.serverLevel().getGameTime();}
    }
    public static boolean active(ServerPlayer p){return ACTIVE.containsKey(p.getUUID());}
    public static BlockHitResult target(ServerPlayer p) {
        Vec3 eye=p.getEyePosition();
        var hit=p.level().clip(new ClipContext(eye,eye.add(p.getLookAngle().scale(Math.min(6,p.getBlockReach()))),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,p));
        if(hit.getType()!=HitResult.Type.BLOCK)return null;
        var state=p.level().getBlockState(hit.getBlockPos());
        return state.getBlock() instanceof VendingMachineBlock&&!state.getValue(VendingMachineBlock.BROKEN)
                &&VendingMachineBlock.frontPoint(state,hit.getBlockPos(),eye,hit)!=null?hit:null;
    }
    public static boolean begin(ServerPlayer p,BlockPos base,InteractionHand hand) {
        if(active(p))return false;
        if(hand!=InteractionHand.MAIN_HAND||!p.isAlive()||p.isSpectator()||p.isSleeping()
                ||!p.getMainHandItem().is(AflItems.CROWBAR.get())||p.getCooldowns().isOnCooldown(AflItems.CROWBAR.get())
                ||p.containerMenu!=p.inventoryMenu)return false;
        var hit=target(p);
        if(hit==null||!VendingMachineBlock.lower(p.level().getBlockState(hit.getBlockPos()),hit.getBlockPos()).equals(base))return false;
        if(!(p.level().getBlockEntity(base) instanceof VendingMachineBlockEntity be))return false;
        if(ACTIVE.values().stream().anyMatch(a->a.machine==be))return false;
        var action=new Pending(p,be);ACTIVE.put(p.getUUID(),action);notify(action,START);return true;
    }
    private static void notify(Pending a,int phase){AflNetwork.crowbarSmash(a.player,a.id,a.machine.getBlockPos(),phase);}
    public static void cancel(ServerPlayer player,UUID id){
        var a=ACTIVE.get(player.getUUID());
        if(a!=null&&a.id.equals(id)){ACTIVE.remove(player.getUUID());notify(a,CANCEL);}
    }
    private static boolean valid(Pending a) {
        var p=a.player;
        if(!p.isAlive()||p.isSpectator()||p.isSleeping()||p.hasDisconnected()||p.containerMenu!=p.inventoryMenu
                ||p.getInventory().selected!=a.slot||p.getMainHandItem()!=a.tool||!a.tool.is(AflItems.CROWBAR.get())
                ||p.level()!=a.machine.getLevel()||a.machine.isRemoved())return false;
        if(a.committed)return true;
        var hit=target(p);
        return hit!=null&&VendingMachineBlock.lower(p.level().getBlockState(hit.getBlockPos()),hit.getBlockPos()).equals(a.machine.getBlockPos())
                &&p.level().getBlockEntity(a.machine.getBlockPos())==a.machine;
    }
    /** Public for the deterministic server integration probe; normal execution is END server tick. */
    public static void advance(ServerPlayer p) {
        var a=ACTIVE.get(p.getUUID());if(a==null)return;
        if(!valid(a)){ACTIVE.remove(p.getUUID());notify(a,CANCEL);return;}
        long elapsed=p.serverLevel().getGameTime()-a.start;
        if(!a.committed&&elapsed>=CrowbarSmashTimeline.IMPACT){
            var level=p.serverLevel();var base=a.machine.getBlockPos();var state=level.getBlockState(base);
            var hit=target(p);
            // valid() has rechecked the same BE, reach, line of sight, tool and intact glass.
            level.setBlock(base,state.setValue(VendingMachineBlock.BROKEN,true),3);
            var upper=level.getBlockState(base.above());
            if(upper.getBlock()==state.getBlock())level.setBlock(base.above(),upper.setValue(VendingMachineBlock.BROKEN,true),3);
            a.committed=true;
            var at=hit.getLocation();
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,Blocks.GLASS.defaultBlockState()),at.x,at.y,at.z,12,.12,.18,.05,.04);
            notify(a,IMPACT);
        }
        if(elapsed>=CrowbarSmashTimeline.DURATION){ACTIVE.remove(p.getUUID());notify(a,END);}
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){if(e.phase==TickEvent.Phase.END)for(var a:List.copyOf(ACTIVE.values()))advance(a.player);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){var a=ACTIVE.remove(e.getEntity().getUUID());if(a!=null)notify(a,CANCEL);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){ACTIVE.clear();}
    private CrowbarSmashAction(){}
}
