import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Read-only decoded waveform audit using the project's existing Vorbis decoder. */
class InspectSlideAudio {
    public static void main(String[] args) {
        try (var stack = MemoryStack.stackPush()) {
            var channels = stack.mallocInt(1); var rate = stack.mallocInt(1);
            var pcm = STBVorbis.stb_vorbis_decode_filename(args[0], channels, rate);
            if (pcm == null) throw new IllegalArgumentException("Cannot decode OGG");
            int sr = rate.get(0), ch = channels.get(0), count = pcm.remaining();
            System.out.printf("rate=%d channels=%d duration=%.6fs%n", sr, ch, count/(double)(sr*ch));
            int step = (int)(sr*.02)*ch;
            for (int i=0; i<count; i+=step) {
                double sum=0, peak=0; int end=Math.min(count,i+step);
                for (int j=i; j<end; j++) { double v=pcm.get(j)/32768.0; sum+=v*v; peak=Math.max(peak,Math.abs(v)); }
                System.out.printf("%.3f rms=%.5f peak=%.5f%n", i/(double)(sr*ch),Math.sqrt(sum/(end-i)),peak);
            }
            MemoryUtil.memFree(pcm);
        }
    }
}
