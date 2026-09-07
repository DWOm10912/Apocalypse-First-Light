package com.antaurora.apofirstlight.weapon;

/** Local scalar accumulator. Never stores a player's original or absolute aim. */
public final class NativeRecoilState {
    private NativeRecoilProfile profile;
    private double vertical, horizontal, delay;
    private double pitch, back, yaw, roll;
    private int horizontalDirection;

    public void kick(NativeRecoilProfile p, double verticalSample, double horizontalSample,
                     double yawSample, double rollSample, double upwardRoom) {
        profile = p;
        vertical += Math.min(Math.max(0, upwardRoom), Math.max(0,
                Math.min(p.maxVertical() - vertical, sample(p.verticalMin(), p.verticalMax(), verticalSample))));
        double u = Math.max(0, Math.min(1, horizontalSample));
        if (horizontalDirection == 0) {
            horizontalDirection = u < .5 ? -1 : 1;
            u = u < .5 ? u * 2 : (u - .5) * 2;
        } else {
            double flip = 1 - p.horizontalContinueChance();
            if (u < flip) { horizontalDirection = -horizontalDirection; u /= flip; }
            else u = flip == 1 ? 0 : (u - flip) / (1 - flip);
        }
        double limit = horizontalDirection < 0 ? Math.abs(p.horizontalMin()) : p.horizontalMax();
        horizontal = clamp(horizontal + horizontalDirection * sample(limit / 3, limit, u), p.maxHorizontal());
        delay = p.recoveryDelay();
        pitch = Math.min(p.modelPitch() * 3, pitch + p.modelPitch());
        back = Math.min(p.modelBack() * 3, back + p.modelBack());
        yaw = clamp(yaw + sample(-p.modelYaw(), p.modelYaw(), yawSample), p.modelYaw() * 3);
        roll = clamp(roll + sample(-p.modelRoll(), p.modelRoll(), rollSample), p.modelRoll() * 3);
    }

    public void advance(double seconds) {
        if (profile == null || !Double.isFinite(seconds) || seconds <= 0) return;
        double recovering = Math.max(0, seconds - delay);
        delay = Math.max(0, delay - seconds);
        double camera = Math.exp(-recovering / profile.cameraRecoveryTime());
        vertical *= camera;
        horizontal *= Math.exp(-recovering / profile.horizontalRecoveryTime());
        if (Math.abs(horizontal) < 1e-5 && delay == 0) horizontalDirection = 0;
        double model = Math.exp(-seconds / profile.modelRecoveryTime());
        pitch *= model; back *= model; yaw *= model; roll *= model;
    }

    public void clear() {
        profile = null;
        vertical = horizontal = delay = pitch = back = yaw = roll = 0;
        horizontalDirection = 0;
    }

    private static double sample(double min, double max, double value) { return min + (max - min) * Math.max(0, Math.min(1, value)); }
    private static double clamp(double value, double limit) { return Math.max(-limit, Math.min(limit, value)); }
    public double vertical() { return vertical; }
    public double horizontal() { return horizontal; }
    public double pitch() { return pitch; }
    public double back() { return back; }
    public double yaw() { return yaw; }
    public double roll() { return roll; }
}
