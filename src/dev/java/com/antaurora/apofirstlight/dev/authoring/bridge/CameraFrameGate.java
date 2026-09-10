package com.antaurora.apofirstlight.dev.authoring.bridge;

/** Immutable handoff from server thread to render thread. No client classes or world access. */
final class CameraFrameGate {
    record Pose(double x,double y,double z,float yaw,float pitch) {}
    private volatile Pose requested;
    private volatile Pose settled;
    private Pose observed;
    private int frames;
    void arm(Pose pose){requested=pose;}
    void clear(){requested=null;}
    boolean ready(){var target=requested;return target==null||target==settled;}
    void observe(Pose client){
        var target=requested;if(target==null||target==settled)return;
        if(observed!=target){observed=target;frames=0;}
        double yawDelta=Math.abs(Math.IEEEremainder((double)client.yaw-target.yaw,360));
        boolean matches=Math.abs(client.x-target.x)<.03&&Math.abs(client.y-target.y)<.03&&Math.abs(client.z-target.z)<.03
                &&yawDelta<.15&&Math.abs(client.pitch-target.pitch)<.15;
        frames=matches?frames+1:0;if(frames>=2)settled=target;
    }
}
