package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.fluid.FluidPipeVisualManager;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import java.util.List;
import java.util.Map;

public final class FluidVisualHoldGameTest {
    // Inspect private visual state in dev tests, without adding runtime/debug APIs.
    private static Object state(GameTestHelper h, BlockPos p) {
        try {
            var field=FluidPipeVisualManager.class.getDeclaredField("ACTIVE_BY_LEVEL");field.setAccessible(true);
            var levels=(Map<?,?>)field.get(null);var states=(Map<?,?>)levels.get(h.getLevel());
            return states==null?null:states.get(p);
        } catch(ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    private static boolean flowing(GameTestHelper h, BlockPos p) {
        try {
            var s=state(h,p);if(s==null)return false;
            var method=s.getClass().getDeclaredMethod("isFlowing");method.setAccessible(true);
            return (boolean)method.invoke(s);
        } catch(ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    private static void mark(GameTestHelper h, BlockPos p, boolean flow, boolean water) {
        FluidPipeVisualManager.markRoute(h.getLevel(),List.of(p),p.west(),p.east(),
                new FluidStack(water?Fluids.WATER:Fluids.LAVA,1),flow);
    }
    public static void run(GameTestHelper h) {
        var local=new BlockPos(3,2,3);h.setBlock(local,AflBlocks.FLUID_PIPE.get());
        var p=h.absolutePos(local);
        mark(h,p,false,false);h.assertTrue(!flowing(h,p),"initial blocked route stays static");
        mark(h,p,true,false);mark(h,p,false,false);
        h.assertTrue(flowing(h,p),"same-tick blocked attempt does not flicker");
        h.runAfterDelay(5,()->{
            mark(h,p,false,false);h.assertTrue(flowing(h,p),"short pause held");
            mark(h,p,true,false);
        });
        h.runAfterDelay(12,()->{
            mark(h,p,false,false);h.assertTrue(flowing(h,p),"actual flow renews hold");
        });
        h.runAfterDelay(16,()->{
            mark(h,p,false,false);h.assertTrue(!flowing(h,p),"blocked attempts cannot extend flow forever");
            mark(h,p,true,false);mark(h,p,false,true);
            h.assertTrue(!flowing(h,p),"different fluid cannot inherit flow");
            mark(h,p,true,true);
        });
        h.runAfterDelay(28,()->h.assertTrue(state(h,p)!=null && !flowing(h,p),"silent route downgrades before presence expires"));
        h.runAfterDelay(38,()->{
            h.assertTrue(state(h,p)==null,"existing presence timeout preserved");
            h.succeed();
        });
    }
}
