package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.weapon.NativeRecoilProfile;
import com.antaurora.apofirstlight.weapon.NativeRecoilState;

/** Deterministic behavior checks; not a substitute for mouse/visual acceptance. */
public final class NativeRecoilChecks {
    private static final NativeRecoilProfile P = new NativeRecoilProfile(.8,1.1,-.12,.12,4.5,1,.05,.18,5,.04,.75,.75,.1);
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
        System.out.println("PASS: single/fast/slow fire, caps, pitch limit, mouse delta, FPS independence, residual/reset");
    }
}
