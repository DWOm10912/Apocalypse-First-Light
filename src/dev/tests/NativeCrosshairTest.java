import com.antaurora.apofirstlight.client.config.NativeCrosshairConfig;
import com.antaurora.apofirstlight.weapon.client.NativeCrosshairMotion;

public final class NativeCrosshairTest {
    private static void check(boolean v) { if(!v)throw new AssertionError(); }
    public static void main(String[] args) {
        var c=NativeCrosshairConfig.defaults();
        check(c.equals(NativeCrosshairConfig.parse("broken")));
        check(c.equals(NativeCrosshairConfig.parse("{\"thickness\":-1,\"alpha\":null,\"color\":\"bad\",\"outline\":\"false\",\"line_length\":\"10\"}")));
        check(NativeCrosshairConfig.parse("{\"line_length\":6}").lineLength()==6);
        check(NativeCrosshairConfig.parse("{\"alpha\":1e999}").alpha()==c.alpha());
        var a=new NativeCrosshairMotion();var b=new NativeCrosshairMotion();
        a.reset(5);b.reset(5);a.shot(c);b.shot(c);
        check(a.gap(c)>5);
        for(int i=0;i<60;i++)a.advance(1.0/60,10,c);
        for(int i=0;i<144;i++)b.advance(1.0/144,10,c);
        check(Math.abs(a.gap(c)-b.gap(c))<1e-9);
        a.reset(5);a.shot(c);double last=a.gap(c);
        for(int i=0;i<100;i++) {a.advance(.02,5,c);check(a.gap(c)<=last);last=a.gap(c);}
        for(int i=0;i<100;i++)a.shot(c);
        check(a.gap(c)<=5+c.maxBloom());a.reset(5);check(a.gap(c)==5);
        a.advance(10,100,c);check(a.gap(c)==c.maxGap());
        System.out.println("PASS: config fallback, valid override, finite bounds, impulse, frame-rate independence, decay, cap, reset");
    }
}
