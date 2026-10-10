"""Fetch the Terrain V2 reference rasters (research only; never committed, never pasted into the game).
  python -I tools/terrain-v2-research/fetch.py preview|dem|nlcd|lidar|lidar2 OUT_DIR [tile_id ...]
- preview: the 3DEP service's own grey hillshade of a tile at 20 m (to check the place before downloading)
- dem:     the tile at 10 m, float32 metres (NAVD88), EPSG:5070, from the 3DEP 1/3 arc-second seamless DEM only
           (mosaic rule: Resolution_Y = 1/3 arc-second rasters), bilinear
- nlcd:    NLCD 2021 Land Cover (L48), 30 m class codes, EPSG:5070, the same bbox (MRLC GeoServer WCS 2.0.1)
- lidar:   a 1000 m window at 1 m from 3DEP lidar-derived DEMs only (mosaic rule: LowPS < 5, finest first), for the
           500 m detail comparison; the window is the tile's 'detail' entry (offset from its centre, metres)
- lidar2:  a 2000 m window at 2 m, same source and centre, for the 2 km local comparison
Each download writes <tile>/<kind>.tif and <tile>/<kind>.json (request URL, UTC time, grid, source query)."""
import json
import os
import sys
import time
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import geo  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
IMAGE = 'https://elevation.nationalmap.gov/arcgis/rest/services/3DEPElevation/ImageServer'
WCS = 'https://www.mrlc.gov/geoserver/ows'
LIDAR = {"mosaicMethod": "esriMosaicAttribute", "sortField": "LowPS", "sortValue": "0", "ascending": True,
         "where": "LowPS < 5"}   # lidar-derived 1 m (or 1/9 arc-second 3 m) rasters only, finest first
THIRD = {"mosaicMethod": "esriMosaicAttribute", "sortField": "Best", "sortValue": "0", "ascending": True,
         "where": "LowPS > 10 AND LowPS < 11"}   # the 1/3 arc-second rasters (10.31 m); Resolution_Y is negative in newer ones


def bbox(tile, size):
    cx, cy = geo.forward(tile['lat'], tile['lon'])
    x0 = round((cx - size / 2 - 15) / 30) * 30 + 15
    y0 = round((cy - size / 2 - 15) / 30) * 30 + 15
    return x0, y0, x0 + size, y0 + size


def get(url, path, tries=4):
    for k in range(tries):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'AFL-terrain-research/1 (offline study)'})
            with urllib.request.urlopen(req, timeout=180) as r:
                data = r.read()
                ctype = r.headers.get('Content-Type', '')
            if b'"error"' in data[:200] or 'xml' in ctype or 'json' in ctype and not path.endswith('.json'):
                raise RuntimeError('server answered %s: %s' % (ctype, data[:400]))
            open(path, 'wb').write(data)
            return len(data), ctype
        except Exception as e:  # noqa: BLE001
            if k == tries - 1:
                raise
            print('  retry', k + 1, e)
            time.sleep(5 * (k + 1))


def sources(x, y):
    q = urllib.parse.urlencode({'where': '1=1', 'geometry': '%f,%f' % (x, y), 'geometryType': 'esriGeometryPoint',
                                'inSR': 5070, 'spatialRel': 'esriSpatialRelIntersects', 'returnGeometry': 'false', 'f': 'json',
                                'outFields': 'Name,LowPS,Best,AcquisitionDate,VerticalDatum,Resolution_Y'})
    with urllib.request.urlopen(IMAGE + '/query?' + q, timeout=120) as r:
        d = json.load(r)
    rows = [f['attributes'] for f in d.get('features', []) if f['attributes'].get('Best') is not None]
    return sorted(rows, key=lambda a: a['Best'])


def main():
    kind, out = sys.argv[1], sys.argv[2]
    cfg = json.load(open(os.path.join(HERE, 'tiles.json'), encoding='utf-8'))
    want = set(sys.argv[3:])
    for t in cfg['tiles']:
        if want and t['id'] not in want:
            continue
        d = os.path.join(out, t['id'])
        os.makedirs(d, exist_ok=True)
        x0, y0, x1, y1 = bbox(t, cfg['size_m'])
        meta = {'tile': t, 'crs': 'EPSG:5070', 'bbox': [x0, y0, x1, y1], 'utc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}
        if kind == 'preview':
            n = int(cfg['size_m'] / 20)
            p = {'bbox': '%d,%d,%d,%d' % (x0, y0, x1, y1), 'bboxSR': 5070, 'imageSR': 5070, 'size': '%d,%d' % (n, n),
                 'format': 'png', 'renderingRule': json.dumps({'rasterFunction': 'Hillshade Gray'}),
                 'mosaicRule': json.dumps(THIRD), 'f': 'image'}
            url, file = IMAGE + '/exportImage?' + urllib.parse.urlencode(p), 'preview.png'
        elif kind == 'dem':
            n = int(cfg['size_m'] / 10)
            p = {'bbox': '%d,%d,%d,%d' % (x0, y0, x1, y1), 'bboxSR': 5070, 'imageSR': 5070, 'size': '%d,%d' % (n, n),
                 'format': 'tiff', 'pixelType': 'F32', 'noData': -9999, 'interpolation': 'RSP_BilinearInterpolation',
                 'compression': 'None', 'mosaicRule': json.dumps(THIRD), 'f': 'image'}
            url, file = IMAGE + '/exportImage?' + urllib.parse.urlencode(p), 'dem10.tif'
            meta['grid'] = {'cell_m': 10, 'width': n, 'height': n}
            meta['sources_at_centre'] = sources((x0 + x1) / 2, (y0 + y1) / 2)
        elif kind in ('lidar', 'lidar2'):
            # lidar: 1000 m at 1 m (the 500 m detail scale); lidar2: 2000 m at 2 m (the 2 km local scale), same centre
            span, cell = (1000, 1) if kind == 'lidar' else (2000, 2)
            ox, oy = t.get('detail', [0, 0])
            cx, cy = (x0 + x1) / 2 + ox, (y0 + y1) / 2 + oy
            x0, y0 = round(cx - span / 2), round(cy - span / 2)
            x1, y1 = x0 + span, y0 + span
            meta['bbox'] = [x0, y0, x1, y1]
            p = {'bbox': '%d,%d,%d,%d' % (x0, y0, x1, y1), 'bboxSR': 5070, 'imageSR': 5070, 'size': '1000,1000',
                 'format': 'tiff', 'pixelType': 'F32', 'noData': -9999, 'interpolation': 'RSP_BilinearInterpolation',
                 'compression': 'None', 'mosaicRule': json.dumps(LIDAR), 'f': 'image'}
            url, file = IMAGE + '/exportImage?' + urllib.parse.urlencode(p), 'lidar%d.tif' % cell
            meta['grid'] = {'cell_m': cell, 'width': 1000, 'height': 1000}
            meta['sources_at_centre'] = sources(cx, cy)
        elif kind == 'nlcd':
            q = [('service', 'WCS'), ('version', '2.0.1'), ('request', 'GetCoverage'),
                 ('coverageId', 'mrlc_download__NLCD_2021_Land_Cover_L48'),
                 ('subset', 'X(%d,%d)' % (x0, x1)), ('subset', 'Y(%d,%d)' % (y0, y1)), ('format', 'image/geotiff')]
            url, file = WCS + '?' + urllib.parse.urlencode(q), 'nlcd2021.tif'
            meta['grid'] = {'cell_m': 30}
        else:
            raise SystemExit('kind?')
        meta['url'] = url
        size, ctype = get(url, os.path.join(d, file))
        meta['bytes'], meta['content_type'] = size, ctype
        json.dump(meta, open(os.path.join(d, file.rsplit('.', 1)[0] + '.json'), 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
        print(t['id'], file, size, 'bytes', ctype)


if __name__ == '__main__':
    main()
