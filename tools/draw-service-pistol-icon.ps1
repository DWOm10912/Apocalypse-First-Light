# Original AFL pixel artwork, drawn directly at native resolution. No screenshots,
# external images, model renders, resampling or preview files are used.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$iconRoot = Split-Path -Parent $PSScriptRoot
$iconPath = Join-Path $iconRoot 'src/main/resources/assets/apocalypse_firstlight/textures/item/service_pistol_icon.png'
$icon = [Drawing.Bitmap]::new(32,32,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
function PixelRect([int]$x,[int]$y,[int]$w,[int]$h,[string]$color) {
    $ink = [Drawing.ColorTranslator]::FromHtml($color)
    for($row=$y;$row -lt $y+$h;$row++) { for($col=$x;$col -lt $x+$w;$col++) { $icon.SetPixel($col,$row,$ink) } }
}
try {
    # Long rectangular slide, short dust cover, open trigger guard, raked grip.
    PixelRect 3 7 25 7 '#171a1d'
    PixelRect 4 8 23 4 '#454c52'
    PixelRect 5 8 20 1 '#697278'
    PixelRect 4 11 23 1 '#363b40'
    PixelRect 2 9 1 3 '#292e32'
    PixelRect 5 6 2 1 '#292e32'
    PixelRect 25 6 2 1 '#292e32'
    foreach($serration in @(22,24,26)) { PixelRect $serration 9 1 3 '#24292d' }
    PixelRect 3 14 24 1 '#171a1d'
    PixelRect 4 13 17 1 '#343a3c'
    PixelRect 13 15 2 4 '#252a2c'
    PixelRect 14 19 7 1 '#171a1d'
    PixelRect 15 18 5 1 '#444b4d'
    PixelRect 18 15 1 3 '#292e32'
    PixelRect 19 17 1 1 '#171a1d'
    for($gripRow=15;$gripRow -le 25;$gripRow++) {
        $rake=[int][Math]::Floor(($gripRow-15)/3)
        PixelRect (20+$rake) $gripRow 7 1 '#1c2022'
        PixelRect (21+$rake) $gripRow 4 1 '#353b3d'
        if(($gripRow % 2) -eq 0) { PixelRect (22+$rake) $gripRow 2 1 '#2a3032' }
    }
    PixelRect 23 26 7 1 '#141719'
    PixelRect 23 25 6 1 '#4a5153'
    PixelRect 21 13 2 1 '#71787b'
    $icon.Save($iconPath,[Drawing.Imaging.ImageFormat]::Png)
    "Created original 32x32 RGBA AFL icon: $iconPath"
} finally { $icon.Dispose() }
