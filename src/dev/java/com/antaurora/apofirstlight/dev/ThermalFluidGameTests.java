package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.block.*;
import com.antaurora.apofirstlight.blockentity.*;
import com.antaurora.apofirstlight.energy.*;
import com.antaurora.apofirstlight.fluid.FluidPipeTransfer;
import com.antaurora.apofirstlight.compat.jade.MachineJadeServerDataProvider;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public final class ThermalFluidGameTests {
    private static final IFluidHandler.FluidAction EXECUTE = IFluidHandler.FluidAction.EXECUTE;
    private static final IFluidHandler.FluidAction SIMULATE = IFluidHandler.FluidAction.SIMULATE;
    private static FluidStack lava(int mb) { return new FluidStack(Fluids.LAVA, mb); }
    private static ThermalGeneratorBlockEntity generator(GameTestHelper h, BlockPos p, Direction facing) {
        h.setBlock(p, AflBlocks.THERMAL_GENERATOR.get().defaultBlockState().setValue(ThermalGeneratorBlock.FACING, facing));
        return (ThermalGeneratorBlockEntity) h.getBlockEntity(p);
    }
    private static void tick(GameTestHelper h, ThermalGeneratorBlockEntity g) {
        ThermalGeneratorBlockEntity.serverTick(h.getLevel(), g.getBlockPos(), g.getBlockState(), g);
    }
    private static IFluidHandler handler(ThermalGeneratorBlockEntity g, Direction face) {
        return g.getCapability(ForgeCapabilities.FLUID_HANDLER, face).resolve().orElseThrow();
    }
    public static void energyPriority(GameTestHelper h) {
        var g = generator(h, new BlockPos(4,2,4), Direction.NORTH);
        g.restoreLiquid(lava(1000));
        for (int i=0;i<1250;i++) tick(h,g);
        h.assertTrue(g.getStoredEnergy()==20000 && g.getLiquidAmount()==0 && g.getFuelEnergyRemaining()==0, "1000mB exact 20000FE / 1250 ticks");
        g.load(new CompoundTag());g.restoreLiquid(lava(20));g.setItem(0,new ItemStack(Items.COAL));
        tick(h,g);
        h.assertTrue(g.getLiquidAmount()==20 && g.getItem(0).isEmpty() && g.getFuelEnergyRemaining()==484,"solid first, no liquid debit");
        for(int i=0;i<31;i++)tick(h,g);
        h.assertTrue(g.getStoredEnergy()==500 && g.getLiquidAmount()==20,"finish solid first");
        tick(h,g);g.setItem(0,new ItemStack(Items.COAL));tick(h,g);
        h.assertTrue(g.getStoredEnergy()==520 && g.getItem(0).is(Items.COAL) && g.getLiquidAmount()==19,"finish liquid remainder before solid");
        tick(h,g);
        h.assertTrue(g.getItem(0).isEmpty() && g.getLiquidAmount()==19,"next cycle solid priority");
        var full = new CompoundTag();full.putInt("EnergyStored",100000);g.load(full);g.restoreLiquid(lava(25));g.setItem(0,new ItemStack(Items.COAL));
        tick(h,g);h.assertTrue(g.getLiquidAmount()==25 && g.getItem(0).getCount()==1,"full buffer consumes neither source");
        h.succeed();
    }
    public static void ports(GameTestHelper h) {
        int n=0;
        for(var f:Direction.Plane.HORIZONTAL) {
            var g=generator(h,new BlockPos(2+2*n++,2,3),f);
            var in=handler(g,f.getClockWise());var out=handler(g,f.getCounterClockWise());
            h.assertTrue(in.fill(new FluidStack(Fluids.WATER,100),EXECUTE)==0,"reject water");
            h.assertTrue(in.fill(lava(100),SIMULATE)==25 && g.getLiquidAmount()==0,"simulate no mutation");
            h.assertTrue(in.fill(lava(100),EXECUTE)==25 && in.fill(lava(100),EXECUTE)==0,"25mB input budget");
            h.assertTrue(in.drain(25,EXECUTE).isEmpty() && out.fill(lava(25),EXECUTE)==0,"one-way ports");
            h.assertTrue(out.drain(25,SIMULATE).getAmount()==25 && out.drain(25,EXECUTE).getAmount()==25,"independent drain budget");
            for(var side:new Direction[]{f,f.getOpposite(),Direction.UP,Direction.DOWN})
                h.assertTrue(!g.getCapability(ForgeCapabilities.FLUID_HANDLER,side).isPresent(),"wrong fluid face");
            h.assertTrue(!g.getCapability(ForgeCapabilities.FLUID_HANDLER,null).isPresent(),"null bypass");
            h.assertTrue(g.getCapability(ForgeCapabilities.ENERGY,f.getOpposite()).isPresent(),"FE back");
            g.restoreLiquid(lava(5000));h.assertTrue(g.getLiquidAmount()==4000,"tank clamp");
            var old=g.getCapability(ForgeCapabilities.FLUID_HANDLER,f.getClockWise());g.invalidateCaps();
            h.assertTrue(!old.isPresent(),"invalidate");g.reviveCaps();
            h.assertTrue(g.getCapability(ForgeCapabilities.FLUID_HANDLER,f.getClockWise()).isPresent(),"revive");
        }
        h.succeed();
    }
    public static void persistence(GameTestHelper h) {
        var g=generator(h,new BlockPos(3,2,3),Direction.NORTH);
        var data=new CompoundTag();MachineJadeServerDataProvider.appendThermalData(data,g);
        h.assertTrue(!data.contains(MachineJadeServerDataProvider.INPUT_FLUID),"empty Jade hidden");
        g.restoreLiquid(lava(1000));tick(h,g);
        MachineJadeServerDataProvider.appendThermalData(data,g);
        h.assertTrue(FluidStack.loadFluidStackFromNBT(data.getCompound(MachineJadeServerDataProvider.INPUT_FLUID)).getAmount()==999,"Jade liquid amount");
        var saved=g.saveWithoutMetadata();g.load(saved);
        h.assertTrue(g.getLiquidAmount()==999 && g.getFuelEnergyRemaining()==4 && g.getStoredEnergy()==16,"save-load exact");
        var drop=new ItemStack(AflBlocks.THERMAL_GENERATOR.get());g.writeDropData(drop);
        g.load(BlockItem.getBlockEntityData(drop));
        h.assertTrue(g.getLiquidAmount()==999 && g.getFuelEnergyRemaining()==4 && g.getStoredEnergy()==16,"drop preserves tank and pending energy");
        var client=new ThermalGeneratorBlockEntity(g.getBlockPos(),g.getBlockState());client.handleUpdateTag(g.getUpdateTag());
        h.assertTrue(client.getLiquidAmount()==999 && client.getActiveFuelSource()==ThermalGeneratorBlockEntity.FuelSource.LIQUID,"client snapshot");
        var display=ThermalFuelDefinitions.displayFuels(MachineBalanceManager.thermalGeneratorFuelEnergies());
        h.assertTrue(display.size()==5,"all current item/liquid JEI fuels");
        h.assertTrue(display.stream().anyMatch(f->f.item().is(Items.COAL)&&f.energyFe()==500),"JEI coal shared");
        h.assertTrue(display.stream().anyMatch(f->f.item().is(Items.COAL_BLOCK)&&f.energyFe()==5000),"JEI coal block shared");
        h.assertTrue(display.stream().anyMatch(f->!f.fluid().isEmpty()&&f.fluid().getAmount()==1000&&f.energyFe()==20000),"JEI lava shared");
        var changed=new java.util.HashMap<>(MachineBalanceManager.thermalGeneratorFuelEnergies());changed.put(new net.minecraft.resources.ResourceLocation("minecraft","lava_bucket"),21001);
        h.assertTrue(ThermalFuelDefinitions.displayFuels(changed).stream().anyMatch(f->!f.fluid().isEmpty()&&f.energyFe()==21001),"JEI follows synced balance");
        h.succeed();
    }
    public static void pipes(GameTestHelper h, Direction facing) {
        var pos=new BlockPos(5,2,5);var g=generator(h,pos,facing);
        var full=new CompoundTag();full.putInt("EnergyStored",100000);g.load(full);
        var left=facing.getClockWise();var right=facing.getCounterClockWise();
        var sourcePos=pos.relative(left,2).above();var sinkPos=pos.relative(right,2).below();
        h.setBlock(sourcePos,AflBlocks.FLUID_TANK.get());h.setBlock(sinkPos,AflBlocks.FLUID_TANK.get());
        var source=(FluidTankBlockEntity)h.getBlockEntity(sourcePos);var sink=(FluidTankBlockEntity)h.getBlockEntity(sinkPos);
        source.restoreControllerFluid(lava(1000));
        var path=java.util.List.of(pos.relative(left),pos.relative(left,2),pos.relative(right),pos.relative(right,2));
        for(var p:path)h.setBlock(p,AflBlocks.FLUID_PIPE.get());
        for(var p:path)h.setBlock(p,FluidPipeBlock.withStructuralConnections(h.getLevel(),h.absolutePos(p),AflBlocks.FLUID_PIPE.get().defaultBlockState()));
        int filled=FluidPipeTransfer.transferFrom(h.getLevel(),source,Direction.DOWN,source::restoreControllerFluid);
        h.assertTrue(filled==25 && g.getLiquidAmount()==25,"actual pipe fill "+facing);
        int drained=FluidPipeTransfer.transferFrom(h.getLevel(),g,right,g::restoreLiquid);
        h.assertTrue(drained==25 && sink.getFluidAmount()==25 && g.getLiquidAmount()==0,"actual pipe drain "+facing);
        h.runAfterDelay(45,()->{
            h.assertTrue(source.getFluidAmount()==0 && g.getLiquidAmount()==0 && sink.getFluidAmount()==1000,"pipe conservation "+facing);
            h.succeed();
        });
    }
}
