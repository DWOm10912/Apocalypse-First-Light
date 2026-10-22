package com.antaurora.apofirstlight.weather;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * Regional weather V1: what was set by hand (/afl weather), kept with the overworld; everything else follows from the
 * seed and the day time and is not saved.
 */
public final class WeatherData extends SavedData {
    private static final String NAME = "afl_weather";
    private final List<RegionalWeather.Force> forces = new ArrayList<>();
    private final List<RegionalWeather.Cell> cells = new ArrayList<>();

    public static WeatherData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(WeatherData::load, WeatherData::new, NAME);
    }

    /** Puts what was set by hand into a fresh model. */
    void applyTo(RegionalWeather model) {
        for (RegionalWeather.Force f : forces) model.force(f);
        for (RegionalWeather.Cell c : cells) model.add(c);
    }

    /** Keeps the model's hand-set weather. */
    void keep(RegionalWeather model, long t) {
        forces.clear();
        forces.addAll(model.forces());
        cells.clear();
        cells.addAll(model.extra(t));
        setDirty();
    }

    private static WeatherData load(CompoundTag tag) {
        WeatherData d = new WeatherData();
        for (Tag t : tag.getList("forces", Tag.TAG_COMPOUND)) {
            CompoundTag f = (CompoundTag) t;
            int g = f.getInt("regime");
            if (g >= 0 && g < WeatherRegime.values().length)
                d.forces.add(new RegionalWeather.Force(WeatherRegime.values()[g], f.getLong("start"), f.getLong("end")));
        }
        for (Tag t : tag.getList("cells", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.cells.add(new RegionalWeather.Cell(c.getLong("id"), c.getBoolean("storm") ? RegionalWeather.Kind.STORM : RegionalWeather.Kind.SHOWER,
                    c.getDouble("x"), c.getDouble("z"), c.getDouble("vx"), c.getDouble("vz"), c.getLong("born"), c.getInt("life"),
                    c.getDouble("radius"), c.getDouble("peak"), c.getDouble("strength")));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag fl = new ListTag();
        for (RegionalWeather.Force f : forces) {
            CompoundTag t = new CompoundTag();
            t.putInt("regime", f.regime().ordinal());
            t.putLong("start", f.start());
            t.putLong("end", f.end());
            fl.add(t);
        }
        tag.put("forces", fl);
        ListTag cl = new ListTag();
        for (RegionalWeather.Cell c : cells) {
            CompoundTag t = new CompoundTag();
            t.putLong("id", c.id());
            t.putBoolean("storm", c.kind() == RegionalWeather.Kind.STORM);
            t.putDouble("x", c.x0());
            t.putDouble("z", c.z0());
            t.putDouble("vx", c.vx());
            t.putDouble("vz", c.vz());
            t.putLong("born", c.born());
            t.putInt("life", c.life());
            t.putDouble("radius", c.radius());
            t.putDouble("peak", c.peak());
            t.putDouble("strength", c.strength());
            cl.add(t);
        }
        tag.put("cells", cl);
        return tag;
    }
}
