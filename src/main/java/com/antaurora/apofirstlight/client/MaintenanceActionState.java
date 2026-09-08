package com.antaurora.apofirstlight.client;

/** Reserved only: V2 runs IDLE. Future animation completion must precede server attachment commit. */
public enum MaintenanceActionState { IDLE, INSTALLING_SIGHT, REMOVING_SIGHT, INSTALLING_MUZZLE, REMOVING_MUZZLE, REPAIRING }
