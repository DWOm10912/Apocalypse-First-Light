package com.antaurora.apofirstlight.mixin.client;
import com.antaurora.apofirstlight.block.RestroomDoorRaycast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public abstract class RestroomDoorPickMixin {
    @Inject(method="pick",at=@At("TAIL"))
    private void afl$openStallLeaf(float partial,CallbackInfo ci){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.gameMode==null||mc.getCameraEntity()!=mc.player)return;
        var eye=mc.player.getEyePosition(partial);var hit=RestroomDoorRaycast.find(mc.level,mc.player,eye,eye.add(mc.player.getViewVector(partial).scale(mc.gameMode.getPickRange())));
        if(hit!=null&&(mc.hitResult==null||mc.hitResult.getType()==net.minecraft.world.phys.HitResult.Type.MISS||eye.distanceToSqr(hit.getLocation())<eye.distanceToSqr(mc.hitResult.getLocation())))mc.hitResult=hit;
    }
}
