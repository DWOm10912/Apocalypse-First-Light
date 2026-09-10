package com.antaurora.apofirstlight.weapon.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Standalone pure-math regression; does not bootstrap Minecraft or claim pixel correctness. */
public final class FirstPersonProjectionSanitizerTest {
    private static int cases;
    private static float minDirection = Float.POSITIVE_INFINITY;
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static Matrix4f resolve(Matrix4f view, Matrix4f world, Matrix4f hand, Matrix4f anchor) {
        return new Matrix4f(view).invert().mul(new Matrix4f(world).invert()).mul(hand).mul(anchor);
    }
    private static void near(Vector3f a, Vector3f b) {
        require(a.distance(b) < 0.00002f, "coordinate mismatch: " + a + " vs " + b);
    }
    private static void run(Matrix4f view, Matrix4f world, Matrix4f hand, Matrix4f anchor, float offset) {
        Matrix4f originalHand = new Matrix4f(hand), originalWorld = new Matrix4f(world);
        Matrix4f clean = FirstPersonProjectionSanitizer.sanitize(hand, world);
        require(clean.equals(hand), "OFF must be exact no-op");
        Matrix4f baseline = resolve(view, world, hand, anchor);
        require(baseline.equals(resolve(view, world, clean, anchor)), "OFF resolver changed");
        for (float compression : new float[]{0.125f, 0.3f, 0.6f}) {
            Matrix4f dirty = new Matrix4f(hand).m22(hand.m22()*compression).m32(hand.m32()*compression);
            Matrix4f dirtyCopy = new Matrix4f(dirty);
            Matrix4f fixed = FirstPersonProjectionSanitizer.sanitize(dirty, world);
            require(dirty.equals(dirtyCopy) && hand.equals(originalHand) && world.equals(originalWorld), "input mutated");
            require(fixed.equals(hand), "depth not restored exactly");
            float[] before = dirty.get(new float[16]), after = fixed.get(new float[16]);
            for (int i=0;i<16;i++) if(i!=10 && i!=14)
                require(Float.floatToIntBits(before[i])==Float.floatToIntBits(after[i]), "non-depth term changed: "+i);
            Matrix4f result = resolve(view, world, fixed, anchor);
            Vector3f exit = result.transformProject(new Vector3f(0,0,-offset));
            near(exit, baseline.transformProject(new Vector3f(0,0,-offset)));
            // Ejection uses the same conversion at its local origin.
            near(result.transformProject(new Vector3f()), baseline.transformProject(new Vector3f()));
            Vector3f delta = result.transformProject(new Vector3f(0,0,-offset-0.01f)).sub(exit);
            float length=delta.length(); minDirection=Math.min(minDirection,length);
            require(length>0.001f, "direction too close to Vanilla normalize cutoff: "+length);
            Vector3f unit=delta.normalize();
            require(unit.isFinite() && Math.abs(unit.length()-1)<0.00001f, "invalid unit direction");
            cases++;
        }
    }
    public static void main(String[] args) {
        float[][] cameras={{0,0},{45,0},{0,35},{-130,-55}};
        // HIP, ADS, crouch ADS, sway, recoil, reload, sprint/transition.
        float[][] poses={{.1f,-.12f,-.85f,0,0},{0,-.05f,-.9f,0,0},{0,-.08f,-.95f,0,0},
                {.12f,-.11f,-.84f,.03f,.05f},{.1f,-.1f,-.8f,.12f,0},
                {.2f,-.3f,-.75f,.5f,.2f},{.3f,-.25f,-.7f,-.4f,.6f}};
        for(float fov:new float[]{70,90,110}) for(float aspect:new float[]{16f/9,21f/9,4f/3})
            for(float[] camera:cameras) for(float[] pose:poses) for(float scale:new float[]{.41f,.45f}) {
                Matrix4f world=new Matrix4f().perspective((float)Math.toRadians(fov),aspect,.05f,700);
                Matrix4f hand=new Matrix4f(world);
                // Separate hand FOV/aspect terms must survive, not just equal projections.
                for(boolean distinctXY:new boolean[]{false,true}) {
                    if(distinctXY) hand.m00(hand.m00()*.9f).m11(hand.m11()*1.1f);
                    Matrix4f view=new Matrix4f().rotateX((float)Math.toRadians(camera[1])).rotateY((float)Math.toRadians(camera[0]));
                    Matrix4f anchor=new Matrix4f().translate(pose[0],pose[1],pose[2]).rotateX(pose[3]).rotateZ(pose[4]).scale(scale);
                    run(view,world,hand,anchor,0);
                    run(view,world,hand,anchor,4.8125f/16); // Existing bare BR51 exit.
                    // Synthetic composed accessory exit, as supplied by the existing bone traversal.
                    run(view,world,hand,new Matrix4f(anchor).translate(0,0,-.6f),0);
                }
            }
        // Captured ON P9/BR51 anchor matrices: exercise the exact previous collapse case.
        Matrix4f world=new Matrix4f().perspective((float)Math.toRadians(70),2560f/1417,.05f,700);
        Matrix4f p9=new Matrix4f().translation(.08890416f,-.123518854f,-.84451425f).scale(.41f);
        Matrix4f br=new Matrix4f().set(new float[]{.4489046f,-7.22666E-5f,-.03137966f,0,7.233598E-5f,.45f,-1.5303435E-6f,0,.03137966f,-3.5175553E-6f,.4489046f,0,.19197606f,-.12399086f,-1.3703684f,1});
        run(new Matrix4f(),world,world,p9,0);
        run(new Matrix4f(),world,world,br,4.8125f/16);
        System.out.println("PASS projection cases="+cases+" minCorrectedDirectionLength="+minDirection);
    }
}
