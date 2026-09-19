package com.antaurora.apofirstlight.client.hudlayout;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HudLayoutRegistry {
    private final Map<String, HudLayoutDescriptor> layouts = new LinkedHashMap<>();
    public void register(HudLayoutDescriptor descriptor) {
        if (layouts.putIfAbsent(descriptor.id(), descriptor) != null)
            throw new IllegalArgumentException("Duplicate HUD layout: " + descriptor.id());
    }
    public HudLayoutDescriptor get(String id) { return layouts.get(id); }
    public Collection<HudLayoutDescriptor> all() { return Collections.unmodifiableCollection(layouts.values()); }
}
