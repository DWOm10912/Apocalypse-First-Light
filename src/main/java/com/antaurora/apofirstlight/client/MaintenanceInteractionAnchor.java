package com.antaurora.apofirstlight.client;

import net.minecraft.world.phys.Vec3;

/** Optional future hand-work target in gun-local space, NOT the attachment's installation sight_anchor. */
public record MaintenanceInteractionAnchor(String name,Vec3 position,Vec3 rotationDegrees) { }
