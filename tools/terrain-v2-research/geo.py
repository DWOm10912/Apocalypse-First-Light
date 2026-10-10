"""EPSG:5070 (NAD83 / Conus Albers) forward and inverse, from Snyder, "Map Projections: A Working Manual" (USGS PP 1395),
equations 3-12, 14-3..14-28. GRS80 ellipsoid, standard parallels 29.5 / 45.5 N, origin 23 N, 96 W, no false origin.
Used to place the research tiles on the NLCD 30 m grid (the DEM is requested in the same projection, so the two align).
The Albers scale factor along a parallel is k = rho n / (a m); along the meridian it is 1 / k (equal-area)."""
import math

A = 6378137.0
F = 1 / 298.257222101
E2 = 2 * F - F * F
E = math.sqrt(E2)
LAT0, LON0, SP1, SP2 = map(math.radians, (23.0, -96.0, 29.5, 45.5))


def _q(phi):
    s = math.sin(phi)
    return (1 - E2) * (s / (1 - E2 * s * s) - (1 / (2 * E)) * math.log((1 - E * s) / (1 + E * s)))


def _m(phi):
    s = math.sin(phi)
    return math.cos(phi) / math.sqrt(1 - E2 * s * s)


_M1, _M2 = _m(SP1), _m(SP2)
_Q0, _Q1, _Q2 = _q(LAT0), _q(SP1), _q(SP2)
N = (_M1 * _M1 - _M2 * _M2) / (_Q2 - _Q1)
C = _M1 * _M1 + N * _Q1
RHO0 = A * math.sqrt(C - N * _Q0) / N


def forward(lat, lon):
    """Degrees (NAD83) -> EPSG:5070 metres (x east, y north)."""
    phi, lam = math.radians(lat), math.radians(lon)
    rho = A * math.sqrt(C - N * _q(phi)) / N
    theta = N * (lam - LON0)
    return rho * math.sin(theta), RHO0 - rho * math.cos(theta)


def inverse(x, y):
    rho = math.hypot(x, RHO0 - y)
    theta = math.atan2(x, RHO0 - y)
    q = (C - rho * rho * N * N / (A * A)) / N
    phi = math.asin(q / 2)
    for _ in range(12):
        s = math.sin(phi)
        phi += (1 - E2 * s * s) ** 2 / (2 * math.cos(phi)) * (
            q / (1 - E2) - s / (1 - E2 * s * s) + (1 / (2 * E)) * math.log((1 - E * s) / (1 + E * s)))
    return math.degrees(phi), math.degrees(LON0 + theta / N)


def scale_along_parallel(lat):
    phi = math.radians(lat)
    rho = A * math.sqrt(C - N * _q(phi)) / N
    return rho * N / (A * _m(phi))
