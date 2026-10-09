package com.antaurora.apofirstlight.energy;

/**
 * A receiving endpoint's storage that wants to know how much its cable network could give it (Building Power V1 screen,
 * docs/models/building_power_v1.md): {@link PowerCableTransfer} tells every such endpoint, each settlement, what the
 * network's sources could deliver that tick in total, whether or not the endpoint takes any of it. The Distribution
 * Panel shows it as its capacity; the Service Meter Box passes it on to its panel.
 */
public interface SupplyProbe {
    void networkSupply(long gameTime, int available);
}
