package com.antaurora.apofirstlight.weather;

/**
 * Regional weather V1 (docs/gameplay/regional_weather_v1_plan.md 4): the weather over the whole country for a stretch of
 * days. What each brings: how often it comes (weight), how long (game days), the cloud it leaves over clear places, the
 * wind (m/s, the direction it blows toward: 0 east, 90 south, give or take the spread), how gusty, the air temperature
 * change (°C), and how many thunderstorms (in the afternoon peak and otherwise) and showers start per game hour.
 * A front's rain band and long rain are their own fields (RegionalWeather).
 */
public enum WeatherRegime {
    //     weight days     cloud  wind     gust  temp storms     showers dir spread
    CLEAR(30, 1.0, 3.0, 0.10, 2, 5, 1.3, 0, 0.2, 0.0, 0.3, 0, 70),
    UNSTABLE(22, 1.0, 3.0, 0.30, 3, 6, 1.4, 1, 3.0, 0.3, 0.8, 0, 60),
    FRONT(15, 1.5, 2.0, 0.35, 4, 7, 1.5, -2, 0.0, 0.0, 0.0, 10, 30),
    OVERCAST(10, 1.0, 3.0, 0.80, 3, 7, 1.3, -2, 0.0, 0.0, 0.0, 30, 60),
    WINDY(10, 0.5, 2.0, 0.15, 10, 15, 1.6, 0, 0.0, 0.0, 0.0, 20, 25),
    COLD_SNAP(6, 1.0, 3.0, 0.40, 6, 9, 1.5, -8, 0.0, 0.0, 0.6, 40, 25),
    HEAT(7, 1.0, 3.0, 0.05, 1, 3, 1.2, 6, 0.8, 0.0, 0.0, 0, 90);

    public final int weight;
    public final double minDays, maxDays, cloud, windMin, windMax, gust, temperature, stormsPeak, stormsOff, showers, direction, spread;

    WeatherRegime(int weight, double minDays, double maxDays, double cloud, double windMin, double windMax, double gust,
                  double temperature, double stormsPeak, double stormsOff, double showers, double direction, double spread) {
        this.weight = weight;
        this.minDays = minDays;
        this.maxDays = maxDays;
        this.cloud = cloud;
        this.windMin = windMin;
        this.windMax = windMax;
        this.gust = gust;
        this.temperature = temperature;
        this.stormsPeak = stormsPeak;
        this.stormsOff = stormsOff;
        this.showers = showers;
        this.direction = direction;
        this.spread = spread;
    }

    /** How likely this one follows {@code previous} (0: never). */
    double weightAfter(WeatherRegime previous) {
        if (previous == this && this != CLEAR) return 0;
        double w = weight;
        if (previous == FRONT && (this == COLD_SNAP || this == WINDY || this == CLEAR)) w *= 2;
        if (previous == HEAT && this == UNSTABLE) w *= 2;
        if (previous == WINDY && this == WINDY) w = 0;
        return w;
    }
}
