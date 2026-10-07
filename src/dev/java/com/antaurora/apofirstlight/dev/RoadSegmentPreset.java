package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.worldgen.roads.*;
import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** Pure topology adapter; all world inspection and construction remain in V1-B. */
final class RoadSegmentPreset {
    static final String VERSION="road_segment_debug_1";
    record Request(RoadType type,int length,Direction direction,int x,int z) {
        Request {
            if(length<16||length>64||!direction.getAxis().isHorizontal()
                    ||Math.abs((long)x)>29_998_000||Math.abs((long)z)>29_998_000)
                throw new IllegalArgumentException("INVALID_SEGMENT_REQUEST");
        }
        int endX(){return x+direction.getStepX()*length;}
        int endZ(){return z+direction.getStepZ()*length;}
        BoundsXZ corridor(){return RoadCrossSection.of(x,z,endX(),endZ(),type).corridorBounds();}
        String command(String mode){return "/afl roads "+mode+" segment "+type.name().toLowerCase(java.util.Locale.ROOT)
                +" "+length+" "+direction.getName()+" at "+x+" "+z+(mode.equals("prepare")?" confirm_unbuilt":"");}
    }
    static RoadPlan plan(ServerLevel level,Request request) {
        String candidate=VERSION+":"+request.type()+":"+request.length()+":"+request.direction()
                +":"+request.x()+":"+request.z();
        String id;
        try {
            id="segment:"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    (candidate+":"+RoadPlanner.VERSION+":"+level.getSeed()+":"+level.dimension().location())
                            .getBytes(StandardCharsets.UTF_8))).substring(0,24);
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        String edgeId=id+":edge",start=id+":start",end=id+":end";
        var a=List.of(new RoadPlan.Arm(request.direction(),request.type(),edgeId));
        var b=List.of(new RoadPlan.Arm(request.direction().getOpposite(),request.type(),edgeId));
        var nodes=List.of(
                new RoadPlan.Node(start,request.x(),request.z(),64,RoadPlan.NodeKind.DEVELOPMENT_TERMINAL,a,
                        RoadJunction.developmentTerminal(request.x(),request.z(),a).footprint()),
                new RoadPlan.Node(end,request.endX(),request.endZ(),64,RoadPlan.NodeKind.DEVELOPMENT_TERMINAL,b,
                        RoadJunction.developmentTerminal(request.endX(),request.endZ(),b).footprint()));
        var edge=new RoadPlan.Edge(edgeId,start,end,request.type(),request.x(),request.z(),request.endX(),request.endZ(),64,request.corridor());
        return new RoadPlan(RoadPlanner.VERSION,id,candidate,RoadPlan.Layout.SEGMENT,RoadPlan.Status.PLANNED,
                request.corridor().expand(4),nodes,List.of(edge),List.of(),List.of(),
                List.of("DEVELOPMENT_SEGMENT_ONLY;NO_LOTS;XZ_ONLY;ACTUAL_HEIGHT_REQUIRES_V1B_PREFLIGHT",
                        "ENDS=ONE_STATION_CROSS_SECTION_WITH_V1B_FOUNDATION_AND_SHOULDERS"),0,1,true);
    }
}
