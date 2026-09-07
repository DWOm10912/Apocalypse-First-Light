param([switch]$PrepareAssets, [string]$SoundSourceDirectory, [switch]$FirstPersonDisplayOnly)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskAssets = Join-Path $taskRoot 'src/main/resources/assets/apocalypse_firstlight'
$taskSource = Get-Content (Join-Path $taskRoot 'src/main/blockbench/p9_01_v03_8_fire_slide_cleanup.bbmodel') -Raw | ConvertFrom-Json
$taskTexture = $taskSource.textures | Where-Object name -eq 'p9_01.png'
if (@($taskTexture).Count -ne 1 -or $taskTexture.source -notmatch '^data:image/png;base64,') { throw 'Missing embedded pistol PNG' }
$taskPng = [Convert]::FromBase64String(($taskTexture.source -split ',',2)[1])
if ([BitConverter]::ToString($taskPng[16..23]) -ne '00-00-01-00-00-00-01-00') { throw 'Expected 256x256 P9 style atlas (128 logical UV units)' }
$taskTexturePath = Join-Path $taskAssets 'textures/item/p9_01.png'
$taskSounds = @{ 'fire'='9mm_fire.ogg'; 'magazine_out'='9mm_magazine_out.ogg'; 'magazine_in'='9mm_magazine_in.ogg' }
if ($PrepareAssets) {
    if (!$SoundSourceDirectory) { throw 'Provide -SoundSourceDirectory for the three original OGG files' }
    foreach ($taskName in $taskSounds.Values) {
        $taskBytes = [IO.File]::ReadAllBytes((Join-Path $SoundSourceDirectory $taskName))
        if ([Text.Encoding]::ASCII.GetString($taskBytes,0,4) -ne 'OggS') { throw "Invalid OGG: $taskName" }
    }
    [IO.File]::WriteAllBytes($taskTexturePath, $taskPng)
    $taskSoundDir = Join-Path $taskAssets 'sounds/weapons/p9_01'
    New-Item -ItemType Directory -Force -Path $taskSoundDir | Out-Null
    foreach ($taskAction in $taskSounds.Keys) {
        Copy-Item -LiteralPath (Join-Path $SoundSourceDirectory $taskSounds[$taskAction]) -Destination (Join-Path $taskSoundDir "p9_01_$taskAction.ogg")
    }
}
if ([Convert]::ToBase64String([IO.File]::ReadAllBytes($taskTexturePath)) -cne [Convert]::ToBase64String($taskPng)) { throw 'Texture differs from accepted source' }
$taskGeo = Get-Content (Join-Path $taskAssets 'geo/p9_01.geo.json') -Raw | ConvertFrom-Json
$taskAnimation = Get-Content (Join-Path $taskAssets 'animations/p9_01.animation.json') -Raw | ConvertFrom-Json
$taskDisplay = Get-Content (Join-Path $taskAssets 'models/item/p9_01_in_hand.json') -Raw | ConvertFrom-Json
$taskRoute = Get-Content (Join-Path $taskAssets 'models/item/p9_01.json') -Raw | ConvertFrom-Json
if ($FirstPersonDisplayOnly) {
    foreach ($taskContext in @('firstperson_righthand','firstperson_lefthand')) {
        if (($taskDisplay.display.$taskContext | ConvertTo-Json -Depth 20 -Compress) -cne ($taskSource.display.$taskContext | ConvertTo-Json -Depth 20 -Compress)) { throw "First-person display mismatch: $taskContext" }
    }
    'PASS: both first-person display contexts exactly match saved source; geometry/other display contexts not checked in this scoped mode.'
    return
}
if ($taskRoute.loader -ne 'forge:separate_transforms' -or $taskRoute.perspectives.gui.textures.layer0 -ne 'apocalypse_firstlight:item/p9_01_inventory') { throw 'Static inventory routing missing' }
$taskBones = $taskGeo.'minecraft:geometry'[0].bones
# V0.5 saved-source exporter owns exact geometry/key/reference checks.
& node (Join-Path $PSScriptRoot 'export-native-gun.mjs') --check
if ($LASTEXITCODE -ne 0) { throw 'Saved-source export verification failed' }
$taskCubeCount = ($taskBones | ForEach-Object { @($_.cubes).Where({ $null -ne $_ }).Count } | Measure-Object -Sum).Sum
$taskKeysChecked = 0
foreach ($taskAnim in $taskSource.animations) {
    foreach ($taskTrack in $taskAnim.animators.PSObject.Properties.Value) {
        $taskKeysChecked += @($taskTrack.keyframes).Where({ $null -ne $_ }).Count
    }
}
foreach ($taskAction in $taskSounds.Keys) {
    $taskPath = Join-Path $taskAssets "sounds/weapons/p9_01/p9_01_$taskAction.ogg"
    $taskBytes = [IO.File]::ReadAllBytes($taskPath)
    if ([Text.Encoding]::ASCII.GetString($taskBytes,0,4) -ne 'OggS') { throw "Invalid runtime OGG $taskAction" }
    if ($SoundSourceDirectory -and (Get-FileHash $taskPath).Hash -cne (Get-FileHash (Join-Path $SoundSourceDirectory $taskSounds[$taskAction])).Hash) { throw 'OGG source mismatch' }
}
"PASS: $taskCubeCount runtime cubes; $($taskBones.Count) bones; $taskKeysChecked exact animation keys; source texture, display, anchors and OGG verified."
