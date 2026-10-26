package com.antaurora.apofirstlight.liquid;

import java.util.Set;

/**
 * One liquid of the liquid framework (docs/gameplay/water_v1_design.md 13.3). Everything every liquid shares (capacity,
 * ground geometry, chunk storage, finite transfers, the surface) reads only these numbers; the behaviours are optional and
 * a liquid that does not list one does without it (13.2). V1 registers water alone (Liquids); gasoline, diesel, industrial
 * waste and lava keep their present code until their own versions (13.5).
 *
 * @param id          registry name, what saves and packets carry
 * @param density     kg/m3: the order of layers, buoyancy
 * @param viscosity   dynamic, Pa s: the flow model's friction
 * @param temperature its default, deg C
 * @param flow        how it moves between cells
 * @param miscibility liquids of one group mix, of different groups layer (layering is later, 13.1 item 4)
 * @param behaviours  the optional behaviours it has
 */
public record Liquid(String id, double density, double viscosity, double temperature, FlowModel flow, String miscibility,
                     Set<Behaviour> behaviours) {
    public enum FlowModel {
        /** Shallow water between cells: the pipe model (LiquidEngine). */
        SHALLOW_PIPES,
        /** Creeping flow with a yield stress (lava, later). */
        VISCOUS
    }

    public enum Behaviour { LAYERING, DISSOLUTION, EVAPORATION, INFILTRATION, BURNING, HEAT, SOLIDIFYING, ENTITY_EFFECTS, CONTAINERS }

    public Liquid {
        behaviours = Set.copyOf(behaviours);
    }

    public boolean has(Behaviour behaviour) {
        return behaviours.contains(behaviour);
    }
}
