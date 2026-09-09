package com.antaurora.apofirstlight.client;

/** V1 records pending server actions only; no arm animation. Future animation can precede request submission. */
public enum MaintenanceActionState { IDLE, INSTALLING_SIGHT, REMOVING_SIGHT, INSTALLING_MUZZLE, REMOVING_MUZZLE, REPAIRING, INSTALLING_MAGAZINE, REMOVING_MAGAZINE }
