package com.antaurora.apofirstlight.dev.highwaymesh;

import com.antaurora.apofirstlight.client.mesh.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import java.util.*;

/** Generic world-space adapter. Shared Pure Mesh renderer, topology and render state are unchanged. */
public final class WorldChunkMeshRenderer {
    public static void render(List<AflMeshPart> parts, Vec3 origin, Vec3 camera, PoseStack pose,
                              VertexConsumer output, ClientLevel level, Map<Long,Integer> lights,
                              AflMeshRenderer.Metrics metrics, boolean preview,List<ChunkMeshGeometry.Normal> sourceNormals) {
        pose.pushPose(); boolean previous=AflMeshRenderer.cullBackFaces(false);
        try {
            pose.translate(origin.x-camera.x,origin.y-camera.y,origin.z-camera.z);
            VertexConsumer lit=new LitVertices(output,level,new Matrix4f(pose.last().pose()).invert(),origin,lights,new Matrix3f(pose.last().normal()),sourceNormals);
            AflMeshRenderer.renderPartsAtCurrentPose(parts,pose,lit,0,OverlayTexture.NO_OVERLAY,
                    preview?.65f:1,1,preview?.75f:1,1,metrics);
        } finally { AflMeshRenderer.cullBackFaces(previous);pose.popPose(); }
    }
    /** Recover model coordinates from the actual traversal pose; never assume the view matrix is identity. */
    private static final class LitVertices implements VertexConsumer {
        private final VertexConsumer out; private final ClientLevel level; private final Matrix4f inverse;
        private final Vec3 origin; private final Map<Long,Integer> cache; private final Vector3f local=new Vector3f();
        private final Matrix3f normalPose;private final List<ChunkMeshGeometry.Normal> normals;private final Vector3f transformedNormal=new Vector3f();private int corner;
        LitVertices(VertexConsumer out,ClientLevel level,Matrix4f inverse,Vec3 origin,Map<Long,Integer> cache,Matrix3f normalPose,List<ChunkMeshGeometry.Normal> normals){this.out=out;this.level=level;this.inverse=inverse;this.origin=origin;this.cache=cache;this.normalPose=normalPose;this.normals=normals;}
        private static int cell(double v){double nearest=Math.rint(v);return (int)Math.floor(Math.abs(v-nearest)<.001?nearest:v);}
        @Override public void vertex(float x,float y,float z,float r,float g,float b,float a,float u,float v,int overlay,int ignored,float nx,float ny,float nz){
            inverse.transformPosition(local.set(x,y,z));
            BlockPos p=new BlockPos(cell(origin.x+local.x),cell(origin.y+local.y+.05),cell(origin.z+local.z));
            int light=cache.computeIfAbsent(p.asLong(),k->LevelRenderer.getLightColor(level,p));
            // Every clipped triangle has independent indices and submits ABCC. The asset adapter verifies this mapping.
            var n=normals.get(corner++/4);normalPose.transform(transformedNormal.set((float)n.x(),(float)n.y(),(float)n.z())).normalize();
            out.vertex(x,y,z,r,g,b,a,u,v,overlay,light,transformedNormal.x,transformedNormal.y,transformedNormal.z);
        }
        @Override public VertexConsumer vertex(double x,double y,double z){out.vertex(x,y,z);return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){out.color(r,g,b,a);return this;}
        @Override public VertexConsumer uv(float u,float v){out.uv(u,v);return this;}
        @Override public VertexConsumer overlayCoords(int u,int v){out.overlayCoords(u,v);return this;}
        @Override public VertexConsumer uv2(int u,int v){out.uv2(u,v);return this;}
        @Override public VertexConsumer normal(float x,float y,float z){out.normal(x,y,z);return this;}
        @Override public void endVertex(){out.endVertex();}
        @Override public void defaultColor(int r,int g,int b,int a){out.defaultColor(r,g,b,a);}
        @Override public void unsetDefaultColor(){out.unsetDefaultColor();}
    }
    private WorldChunkMeshRenderer(){}
}
