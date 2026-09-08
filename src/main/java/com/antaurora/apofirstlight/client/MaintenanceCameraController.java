package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.block.StaticWorkstationBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** North-authored coordinates shared by camera, BER, and picking; never moves the player. */
public final class MaintenanceCameraController {
    public static final Vec3 MAT = new Vec3(1.0375,16.62/16,.45625);
    public static final Vec3 LOOK = MAT.add(0,.04,0);
    public static final float PITCH=67.5f;
    public static final double FOV=75;
    // Below the existing lamp hood: the real geometry must not occlude the lens.
    public static final Vec3 CAMERA=LOOK.add(0,.82,-.82/Math.tan(Math.toRadians(PITCH)));
    public static float rotation(Direction f){return switch(f){case EAST->-90;case SOUTH->180;case WEST->90;default->0;};}
    public static Vec3 world(BlockPos root,Direction f,Vec3 local){
        double a=Math.toRadians(rotation(f)),x=local.x-.5,z=local.z-.5;
        return new Vec3(root.getX()+.5+x*Math.cos(a)+z*Math.sin(a),root.getY()+local.y,root.getZ()+.5-x*Math.sin(a)+z*Math.cos(a));
    }
    public static void benchTransform(PoseStack p,Direction facing){
        p.translate(.5,0,.5);p.mulPose(Axis.YP.rotationDegrees(rotation(facing)));p.translate(-.5,0,-.5);
        p.translate(MAT.x,MAT.y,MAT.z);
    }
    public static float yaw(Direction f){return switch(f){case EAST->90;case SOUTH->180;case WEST->-90;default->0;};}
}
