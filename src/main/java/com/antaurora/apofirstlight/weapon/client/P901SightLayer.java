package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.weapon.P901Item;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** P9 adapter: the renderer/slot/asset contract itself is shared. */
public final class P901SightLayer extends GeoRenderLayer<P901Item> {
    private final P901Renderer owner;
    public P901SightLayer(P901Renderer owner){super(owner);this.owner=owner;}
    @Override public void renderForBone(PoseStack pose,P901Item item,GeoBone bone,RenderType type,
            MultiBufferSource buffers,VertexConsumer buffer,float partial,int light,int overlay){
        try{NativeSightRendering.render(owner.sightStack(),bone,pose,buffers,light,overlay);}
        finally{buffers.getBuffer(type);}
    }
}
