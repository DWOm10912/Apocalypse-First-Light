package com.antaurora.apofirstlight.blockmesh;

import java.util.HashMap;
import java.util.Map;

/** Per-instance client visual state. No serialization, gameplay mutation, packets or tick loop. */
public final class AflBlockMeshAnimationState {
    private final Map<String, Transition> channels = new HashMap<>();
    private AflBlockMeshProfile profile;
    /** Bumped whenever a channel's target or the profile changes (client/blockmesh/AflMeshChunking re-checks then). */
    private int version;

    public void configure(AflBlockMeshProfile next) {
        if (profile == next) return;
        profile = next;
        version++;
        channels.clear(); // Initial load/reload snaps to authority; never keeps stale definitions.
    }

    public int version() {
        return version;
    }

    public void target(String channel, boolean active, double tick) {
        var animation = profile == null ? null : profile.animations().get(channel);
        if (animation == null) return;
        double target = active ? 1 : 0;
        var transition = channels.get(channel);
        if (transition == null) {
            channels.put(channel, new Transition(animation, target, tick));
            version++;
        } else if (transition.target != target) {
            version++;
            // State packets can arrive between frames within the same client tick.
            // Never reverse from a time earlier than the pose already shown to the player.
            tick = Math.max(tick, transition.lastSample);
            double current = transition.sample(tick);
            transition.from = current;
            transition.target = target;
            transition.start = tick;
            transition.duration = animation.durationTicks() * Math.abs(target - current);
        }
    }

    public double sample(String channel, double tick) {
        var transition = channels.get(channel);
        return transition == null ? 0 : transition.sample(tick);
    }

    /** The channel's resting value at tick: its target (0 or 1) once the transition has finished, -1 while it moves. */
    public double settled(String channel, double tick) {
        var transition = channels.get(channel);
        if (transition == null) return 0;
        return transition.duration <= 0 || Math.max(tick, transition.lastSample) - transition.start >= transition.duration ? transition.target : -1;
    }

    /** Where the channel is heading or resting (0 or 1). */
    public double targetValue(String channel) {
        var transition = channels.get(channel);
        return transition == null ? 0 : transition.target;
    }

    private static final class Transition {
        final AflBlockMeshProfile.Animation animation;
        double from, target, start, duration, lastSample;
        Transition(AflBlockMeshProfile.Animation animation, double target, double tick) {
            this.animation = animation;
            this.from = this.target = target;
            this.start = tick;
            this.lastSample = tick;
        }
        double sample(double tick) {
            tick = Math.max(tick, lastSample);
            lastSample = tick;
            if (duration <= 0) return target;
            double t = Math.max(0, Math.min(1, (tick - start) / duration));
            return from + (target - from) * animation.easing().apply(t);
        }
    }
}
