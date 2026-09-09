package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.block.StaticWorkstationBlock;
import com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** One owner for all maintenance input/camera decisions. No player position/rotation writes. */
public final class MaintenanceModeClientState {
    public static final MaintenanceModeClientState INSTANCE=new MaintenanceModeClientState();
    private GunMaintenanceScreen owner;
    private BlockPos target;
    private ResourceKey<Level> dimension;
    private CameraType originalCamera;
    private long transitionStart;
    private boolean leaving;
    private double exitWeight;
    public static final double ENTER_SECONDS=.22, EXIT_SECONDS=.16;
    public double blend(){
        double t=Math.min(1,(System.nanoTime()-transitionStart)/1e9/(leaving?EXIT_SECONDS:ENTER_SECONDS));
        double eased=t*t*(3-2*t);
        return leaving?exitWeight*(1-eased):eased;
    }
    public boolean ready(){return active()&&!leaving&&blend()>=1;}
    public boolean leaving(){return leaving;}
    public boolean exitFinished(){return leaving&&blend()<=0;}
    public void beginExit(){if(!leaving){exitWeight=blend();leaving=true;transitionStart=System.nanoTime();}}
    public int hoveredHotbarSlot=-1;
    public boolean hoveredGun;
    public MaintenanceActionState action=MaintenanceActionState.IDLE;
    public void enter(GunMaintenanceScreen screen,BlockPos pos){
        if(owner==screen)return;
        exit();var mc=Minecraft.getInstance();owner=screen;target=pos.immutable();dimension=mc.level.dimension();originalCamera=mc.options.getCameraType();
        leaving=false;transitionStart=System.nanoTime();
    }
    public boolean active(){return owner!=null&&Minecraft.getInstance().screen==owner&&valid();}
    public GunMaintenanceBenchBlockEntity bench(){var mc=Minecraft.getInstance();return mc.level!=null&&target!=null&&mc.level.getBlockEntity(target) instanceof GunMaintenanceBenchBlockEntity b?b:null;}
    public boolean valid(){var mc=Minecraft.getInstance();var b=bench();return b!=null&&mc.player!=null&&mc.level.dimension()==dimension&&b.stillValid(mc.player);}
    public Direction facing(){return bench().getBlockState().getValue(StaticWorkstationBlock.FACING);}
    public Vec3 cameraPosition(){return MaintenanceCameraController.world(target,facing(),MaintenanceCameraController.CAMERA);}
    public void exit(){
        if(owner==null)return;
        Minecraft.getInstance().options.setCameraType(originalCamera);
        owner=null;target=null;dimension=null;hoveredHotbarSlot=-1;hoveredGun=false;action=MaintenanceActionState.IDLE;
    }
    public void removed(GunMaintenanceScreen screen){if(owner==screen)exit();}
}
