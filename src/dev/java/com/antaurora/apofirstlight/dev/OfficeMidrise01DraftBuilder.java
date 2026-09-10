package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.authoring.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import static net.minecraft.commands.Commands.literal;

/** PASS 1 only, authoring scaffold. Entire dev package is excluded from the release jar. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class OfficeMidrise01DraftBuilder {
    public static final String ID="office_midrise_01";
    private final Map<BlockPos,BlockState> plan=new LinkedHashMap<>();
    private OfficeMidrise01DraftBuilder() {}
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        if(net.minecraftforge.fml.loading.FMLEnvironment.production)return;
        event.getDispatcher().register(literal("afl_author").requires(BuildingAuthoringCommands::allowed)
                .then(literal("build").then(literal(ID).executes(c->{
                    try {
                        var s=BuildingAuthoringCommands.active(c.getSource());
                        int count=build(c.getSource().getLevel(),s);
                        c.getSource().sendSuccess(()->Component.literal("OFFICE_MIDRISE_01_PASS1_READY_FOR_USER_REVIEW: "+count
                                +" blocks; origin="+s.origin.toShortString()+" SOUTH front Z="+s.max().getZ()
                                +". Six floors + shell; stairs/interior/HVAC detail pending approval. NOT EXPORTED."),false);
                        return 1;
                    }catch(Exception e){c.getSource().sendFailure(Component.literal("Draft: "+e.getMessage()));return 0;}
                })))
                .then(literal("upgrade").then(literal(ID).executes(c->{
                    try {
                        var s=BuildingAuthoringCommands.active(c.getSource());
                        int count=OfficeMidrise01DetailBuilder.upgrade(c.getSource().getLevel(),s);
                        c.getSource().sendSuccess(()->Component.literal("OFFICE_MIDRISE_01_DRAFT_READY_FOR_USER_REVIEW: updated="+count
                                +"; BASIC interior, stairs and roof access. NOT EXPORTED."),false);return 1;
                    }catch(Exception e){c.getSource().sendFailure(Component.literal("Draft upgrade: "+e.getMessage()));return 0;}
                }))));
    }
    static Map<BlockPos,BlockState> massing() {
        var b=new OfficeMidrise01DraftBuilder();b.buildFoundation();b.buildFloorPlates();b.buildExteriorFrame();
        b.buildWindows();b.buildGroundFloor();b.buildRoof();return b.plan;
    }
    public static int build(ServerLevel level,BuildingAuthoringSession session) {
        if(!BuildingAuthoringConfig.ENABLED.get())throw new IllegalArgumentException("Authoring disabled");
        var m=session.metadata;
        if(!ID.equals(m.id())||m.width()!=28||m.depth()!=34||m.height()!=40||m.surfaceOffset()!=1)
            throw new IllegalArgumentException("Requires /afl_author create office_midrise_01 28 34 40 1");
        // Never overwrite a user's draft. Intentional rebuild requires the existing explicit clear-token workflow.
        BuildingAuthoringService.vacant(level,session);
        var blueprint=new OfficeMidrise01DraftBuilder();
        blueprint.buildFoundation();blueprint.buildFloorPlates();blueprint.buildExteriorFrame();
        blueprint.buildWindows();blueprint.buildGroundFloor();blueprint.buildRoof();
        // The complete plan is bounds checked before the first world write.
        for(var entry:blueprint.plan.entrySet())level.setBlock(session.origin.offset(entry.getKey()),entry.getValue(),2);
        session.metadata=new BuildingMetadata(ID,28,34,40,1,BuildingMetadata.Category.MIDRISE_OFFICE,
                Set.of(BuildingMetadata.Zone.CORE,BuildingMetadata.Zone.MIXED),true,true);
        session.changed();session.boundsUntil=level.getGameTime()+1200;
        return blueprint.plan.size();
    }
    private void put(int x,int y,int z,Block block) {
        if(x<0||x>=28||y<0||y>=40||z<0||z>=34)throw new IllegalStateException("Blueprint outside plot");
        plan.put(new BlockPos(x,y,z),block.defaultBlockState());
    }
    private void box(int x0,int y0,int z0,int x1,int y1,int z1,Block block) {
        for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)put(x,y,z,block);
    }
    private void ring(int y,Block block) {
        box(0,y,0,27,y,0,block);box(0,y,33,27,y,33,block);
        box(0,y,1,0,y,32,block);box(27,y,1,27,y,32,block);
    }
    private void buildFoundation() {box(0,0,0,27,0,33,Blocks.STONE_BRICKS);}
    private void buildFloorPlates() {
        for(int y:new int[]{1,6,11,16,21,26})box(0,y,0,27,y,33,Blocks.SMOOTH_STONE);
    }
    private void buildExteriorFrame() {
        for(int y=2;y<=30;y++)ring(y,y<6?Blocks.BRICKS:Blocks.LIGHT_GRAY_CONCRETE);
        // Three front bays, strongly readable brick piers and light horizontal bands.
        for(int x:new int[]{0,1,8,9,18,19,26,27}) {
            box(x,2,33,x,30,33,Blocks.BRICKS);
            box(x,2,0,x,30,0,Blocks.BRICKS);
        }
        for(int z:new int[]{0,1,11,12,22,23,32,33}) {
            box(0,2,z,0,30,z,Blocks.BRICKS);box(27,2,z,27,30,z,Blocks.BRICKS);
        }
        for(int y:new int[]{6,11,16,21,26,31}) {
            // Keep brick columns legible; inset bands span bays, not a monochrome cube.
            for(int[] bay:new int[][]{{2,7},{10,17},{20,25}})box(bay[0],y,33,bay[1],y,33,Blocks.SMOOTH_STONE);
            for(int[] bay:new int[][]{{2,10},{13,21},{24,31}}) {
                box(0,y,bay[0],0,y,bay[1],Blocks.SMOOTH_STONE);
                box(27,y,bay[0],27,y,bay[1],Blocks.SMOOTH_STONE);
            }
        }
    }
    private void buildWindows() {
        for(int floor:new int[]{6,11,16,21,26}) {
            for(int[] bay:new int[][]{{2,7},{10,17},{20,25}}) {
                box(bay[0],floor+1,33,bay[1],floor+3,33,Blocks.BLACK_STAINED_GLASS);
                box(bay[0],floor+1,32,bay[1],floor+1,32,Blocks.POLISHED_BLACKSTONE);
            }
            for(int[] bay:new int[][]{{3,9},{14,20},{25,30}}) {
                box(0,floor+1,bay[0],0,floor+3,bay[1],Blocks.BLACK_STAINED_GLASS);
                box(27,floor+1,bay[0],27,floor+3,bay[1],Blocks.BLACK_STAINED_GLASS);
            }
            // Rear is deliberately quieter/service-oriented, not another lobby facade.
            for(int[] bay:new int[][]{{3,6},{11,16},{21,24}})
                box(bay[0],floor+1,0,bay[1],floor+2,0,Blocks.BLACK_STAINED_GLASS);
        }
    }
    private void buildGroundFloor() {
        box(2,2,33,6,4,33,Blocks.BLACK_STAINED_GLASS);
        box(21,2,33,25,4,33,Blocks.BLACK_STAINED_GLASS);
        box(9,2,33,18,4,33,Blocks.BLACK_STAINED_GLASS);
        box(12,2,33,15,4,33,Blocks.AIR); // Four-wide lobby passage, not a domestic front door.
        box(8,5,30,19,5,33,Blocks.POLISHED_BLACKSTONE);
        box(8,1,24,19,1,33,Blocks.POLISHED_ANDESITE);
        // Inset entrance frame, entirely inside capture volume.
        box(9,2,32,9,4,32,Blocks.POLISHED_BLACKSTONE);
        box(18,2,32,18,4,32,Blocks.POLISHED_BLACKSTONE);
    }
    private void buildRoof() {
        box(0,31,0,27,31,33,Blocks.SMOOTH_STONE);
        ring(32,Blocks.LIGHT_GRAY_CONCRETE);ring(33,Blocks.SMOOTH_STONE);
        box(1,32,1,26,32,32,Blocks.GRAVEL);
        // Roof massing only: two HVAC volumes + access headhouse, no functional machines.
        box(16,33,6,23,35,12,Blocks.GRAY_CONCRETE);
        box(5,33,19,10,34,24,Blocks.GRAY_CONCRETE);
        box(2,33,3,7,36,11,Blocks.LIGHT_GRAY_CONCRETE);
        box(2,37,3,7,37,11,Blocks.SMOOTH_STONE);
    }
}
