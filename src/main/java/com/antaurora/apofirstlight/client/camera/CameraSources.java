package com.antaurora.apofirstlight.client.camera;

import com.antaurora.apofirstlight.client.CrowbarFirstPerson;
import com.antaurora.apofirstlight.client.JerryCanFirstPerson;
import com.antaurora.apofirstlight.client.PortableGeneratorPull;
import com.antaurora.apofirstlight.client.skeleton.SkeletonActions;
import com.antaurora.apofirstlight.temperature.ClientShiver;
import com.antaurora.apofirstlight.weapon.client.NativeCameraBoneConsumer;

import java.util.List;

/**
 * Camera director V1 (docs/dev/camera_director_v1_plan.md 3, steps D2 and D3): the systems that feed the director's layers.
 * Each source keeps its own conditions and values, moved over unchanged from its own ComputeCameraAngles handler / shadow hook.
 */
public final class CameraSources {
    /** The shake layer (CameraDirector#angles), summed. */
    public static final List<CameraShake.Source> SHAKES = List.of(
            PortableGeneratorPull::cameraShake,        // recoil start: the yank, the rebound, the catch's shudder
            CrowbarFirstPerson::cameraShake,           // crowbar smash: the knock of the impact
            JerryCanFirstPerson::cameraShake,          // jerry can view model: its camera bone
            NativeCameraBoneConsumer::cameraShake,     // guns: the clips' camera bone (reload, inspect, draw, put away)
            ClientShiver::cameraShake,                 // cold: shivering bursts
            com.antaurora.apofirstlight.health.WoundScreenEffect::cameraShake);   // infected wound: a fever's dizzy sway and chills

    /** Bodies drawn away from their entity (CameraDirector#bodyOffset), summed. */
    public static final List<CameraDirector.BodyOffset> BODY_OFFSETS = List.of(
            SkeletonActions::bodyOffset);              // player actions: the render-only approach

    private CameraSources() {
    }
}
