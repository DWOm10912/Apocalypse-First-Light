package com.antaurora.apofirstlight.worldgen.terrain.v2;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;

/** PNG to JPEG for the docs (optional integer downscale by averaging). <pre>java ToJpg IN.png OUT.jpg [factor]</pre> */
public final class ToJpg {
    public static void main(String[] args) throws Exception {
        BufferedImage in = ImageIO.read(new File(args[0]));
        int f = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        int w = in.getWidth() / f, h = in.getHeight() / f;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int r = 0, g = 0, b = 0;
            for (int j = 0; j < f; j++) for (int i = 0; i < f; i++) {
                int c = in.getRGB(x * f + i, y * f + j);
                r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255;
            }
            int n = f * f;
            out.setRGB(x, y, ((r / n) << 16) | ((g / n) << 8) | (b / n));
        }
        ImageWriter wr = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam p = wr.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(0.88f);
        try (ImageOutputStream os = ImageIO.createImageOutputStream(new File(args[1]))) {
            wr.setOutput(os);
            wr.write(null, new IIOImage(out, null, null), p);
        }
        wr.dispose();
    }
}
