package com.antaurora.apofirstlight.client.hudlayout;

import com.antaurora.apofirstlight.client.GeigerHudOverlay;
import com.antaurora.apofirstlight.client.config.GeigerHudConfig;
import com.antaurora.apofirstlight.client.config.GeigerHudConfigManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Geiger counter V2 HUD in the layout editor: one panel, moved from the bottom-right corner and scaled. */
public final class GeigerHudLayoutAdapter implements ClientHudLayout {
    private static final Gson JSON=new Gson();
    public String id(){return "geiger_counter";}
    public String fileName(){return "geiger_hud.json";}
    public ResourceLocation overlayId(){return new ResourceLocation("apocalypse_firstlight","geiger_counter");}
    public JsonObject defaults(){return JSON.toJsonTree(GeigerHudConfig.defaults()).getAsJsonObject();}
    public JsonObject current(){return JSON.toJsonTree(GeigerHudConfigManager.get()).getAsJsonObject();}
    public void apply(JsonObject json){GeigerHudConfigManager.apply(GeigerHudConfigManager.sanitize(json));}
    public void render(GuiGraphics g,JsonObject json,int width,int height){GeigerHudOverlay.preview(g,width,height,GeigerHudConfigManager.sanitize(json));}
    public List<HudEditableElement> elements(JsonObject json,int width,int height){
        var c=GeigerHudConfigManager.sanitize(json);float scale=c.hudScale();float[] o=GeigerHudOverlay.origin(width,height,c);
        return List.of(new HudEditableElement("global",new HudBounds(o[0],o[1],GeigerHudOverlay.WIDTH*scale,GeigerHudOverlay.HEIGHT*scale),
                "offsetX","offsetY",c.offsetX(),c.offsetY(),-1,true,1000,List.of(new HudEditableElement.Scale("hudScale",scale,.5F,2))));
    }
    public HudBounds occupied(JsonObject json,int width,int height){return elements(json,width,height).get(0).bounds();}
}
