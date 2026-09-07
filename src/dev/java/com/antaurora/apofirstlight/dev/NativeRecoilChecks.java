package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.weapon.NativeRecoilProfile;
import com.antaurora.apofirstlight.weapon.NativeRecoilState;

/** Deterministic behavior checks; not a substitute for mouse/visual acceptance. */
public final class NativeRecoilChecks {
    private static final NativeRecoilProfile P = new NativeRecoilProfile(.8,1.1,-.12,.18,4.5,1,.05,.18,5,.04,.75,.75,.1,.70,.16);
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void shot(NativeRecoilState s) { s.kick(P,.5,.5,.5,.5,90); }
    public static void main(String[] args) {
        var s = new NativeRecoilState();
        check(s.vertical() == 0 && s.pitch() == 0, "No success: no recoil");
        shot(s);
        check(Math.abs(s.vertical()-.95)<1e-12 && s.back()==.04 && s.pitch()==5, "Single shot");
        s.advance(.05);
        check(Math.abs(s.vertical()-.95)<1e-12, "Delay retains aim kick");
        s.advance(.1);
        double remaining=s.vertical();
        shot(s);
        check(s.vertical() > .95 && Math.abs(s.vertical() - remaining - .95) < 1e-12, "Fast fire accumulates");
        s.advance(1);
        check(s.vertical()<.02 && s.back()<.00001, "Slow fire recovers");
        s.clear();
        for(int i=0;i<1000;i++)s.kick(P,1,1,1,1,90);
        check(s.vertical()==4.5 && s.horizontal()==1 && s.pitch()==15 && s.back()==.12, "Bound delayed burst");
        s.clear();
        s.kick(P,1,0,0,0,.1);
        check(Math.abs(s.vertical()-.1)<1e-12, "At sky limit only actual applied kick is owned");
        s.clear();
        double aim=0;
        shot(s); aim-=s.vertical();
        aim+=2; // independent mouse input
        double previous=s.vertical();
        s.advance(10); aim-=s.vertical()-previous;
        check(Math.abs(aim-2)<1e-9, "Recovery preserves mouse displacement, never resets original aim");
        var a=new NativeRecoilState(); var b=new NativeRecoilState(); shot(a);shot(b);
        for(int i=0;i<30;i++)a.advance(1.0/30);
        for(int i=0;i<144;i++)b.advance(1.0/144);
        check(Math.abs(a.vertical()-b.vertical())<1e-12 && Math.abs(a.back()-b.back())<1e-12,"Frame rate independent decay");
        double model=a.back(); a.advance(.001);
        check(a.back()>0 && a.back()<model,"Residual decays continuously, including reload");
        a.clear(); check(a.vertical()==0 && a.back()==0,"Lifecycle reset");
        s.clear();
        s.kick(P,.5,.75,.5,.5,90);
        double h=s.horizontal();
        s.kick(P,.5,.8,.5,.5,90);
        check(s.horizontal()>h,"Direction continues above flip threshold");
        h=s.horizontal();
        s.kick(P,.5,.1,.5,.5,90);
        check(s.horizontal()<h,"Direction flips below threshold");
        double mouseYaw=123+s.horizontal();
        mouseYaw-=37;
        h=s.horizontal();s.advance(10);mouseYaw+=s.horizontal()-h;
        check(Math.abs(mouseYaw-86)<1e-9,"Yaw recovery preserves independent mouse input");
        s.clear();for(int i=0;i<1000;i++)s.kick(P,.5,i==0?0:1,.5,.5,90);
        check(s.horizontal()==-1,"Negative horizontal cap");
        s.clear();
        double expectedV=0,expectedDelay=0;
        var rng=new java.util.Random(631);
        for(int i=0;i<10000;i++){
            double dt=rng.nextDouble()*.2;
            expectedV*=Math.exp(-Math.max(0,dt-expectedDelay)/.18);expectedDelay=Math.max(0,expectedDelay-dt);
            s.advance(dt);
            double v=rng.nextDouble();expectedV=Math.min(4.5,expectedV+.8+.3*v);expectedDelay=.05;
            h=s.horizontal();s.kick(P,v,rng.nextDouble(),.5,.5,90);
            check(Math.abs(s.vertical()-expectedV)<1e-12,"Vertical golden behavior unchanged");
            check(s.horizontal()-h>=-.120000001&&s.horizontal()-h<=.180000001,"Horizontal kick bounded");
        }
        System.out.println("PASS: recoil caps, direction persistence/flip, horizontal mouse delta, vertical golden sequence, recovery/FPS/reset");
    }
}
