package com.antaurora.apofirstlight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Shared attachment-hotspot typography, plate, offsets and fade duration. */
public final class AttachmentHintStyle {
    public static final float FADE_SECONDS=.15f;
    public static void draw(GuiGraphics g,Component label,int anchorX,int anchorY,int width,float fade) {
        var font=Minecraft.getInstance().font;
        int x=Math.max(4,Math.min(width-font.width(label)-8,anchorX+14));int y=Math.max(4,anchorY-16);
        g.fill(x-3,y-3,x+font.width(label)+3,y+12,((int)(fade*190)<<24)|0x272b2e);
        g.drawString(font,label,x,y,((int)(fade*255)<<24)|0xdddddd,false);
    }
}
