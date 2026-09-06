param([switch]$PrepareAssets, [string]$SoundSourceDirectory)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskAssets = Join-Path $taskRoot 'src/main/resources/assets/apocalypse_firstlight'
$taskSource = Get-Content (Join-Path $taskRoot 'src/main/blockbench/service_pistol_v03_8_fire_slide_cleanup.bbmodel') -Raw | ConvertFrom-Json
$taskTexture = $taskSource.textures | Where-Object name -eq 'service_pistol.png'
if (@($taskTexture).Count -ne 1 -or $taskTexture.source -notmatch '^data:image/png;base64,') { throw 'Missing embedded pistol PNG' }
$taskPng = [Convert]::FromBase64String(($taskTexture.source -split ',',2)[1])
if ([BitConverter]::ToString($taskPng[16..23]) -ne '00-00-00-80-00-00-00-80') { throw 'Expected 128x128 PNG' }
$taskTexturePath = Join-Path $taskAssets 'textures/item/service_pistol.png'
$taskSounds = @{ 'fire'='9mm_fire.ogg'; 'magazine_out'='9mm_magazine_out.ogg'; 'magazine_in'='9mm_magazine_in.ogg' }
if ($PrepareAssets) {
    if (!$SoundSourceDirectory) { throw 'Provide -SoundSourceDirectory for the three original OGG files' }
    foreach ($taskName in $taskSounds.Values) {
        $taskBytes = [IO.File]::ReadAllBytes((Join-Path $SoundSourceDirectory $taskName))
        if ([Text.Encoding]::ASCII.GetString($taskBytes,0,4) -ne 'OggS') { throw "Invalid OGG: $taskName" }
    }
    [IO.File]::WriteAllBytes($taskTexturePath, $taskPng)
    $taskSoundDir = Join-Path $taskAssets 'sounds/weapons/service_pistol'
    New-Item -ItemType Directory -Force -Path $taskSoundDir | Out-Null
    foreach ($taskAction in $taskSounds.Keys) {
        Copy-Item -LiteralPath (Join-Path $SoundSourceDirectory $taskSounds[$taskAction]) -Destination (Join-Path $taskSoundDir "service_pistol_$taskAction.ogg")
    }
}
if ([Convert]::ToBase64String([IO.File]::ReadAllBytes($taskTexturePath)) -cne [Convert]::ToBase64String($taskPng)) { throw 'Texture differs from accepted source' }
$taskGeo = Get-Content (Join-Path $taskAssets 'geo/service_pistol.geo.json') -Raw | ConvertFrom-Json
$taskAnimation = Get-Content (Join-Path $taskAssets 'animations/service_pistol.animation.json') -Raw | ConvertFrom-Json
$taskDisplay = Get-Content (Join-Path $taskAssets 'models/item/service_pistol_in_hand.json') -Raw | ConvertFrom-Json
$taskRoute = Get-Content (Join-Path $taskAssets 'models/item/service_pistol.json') -Raw | ConvertFrom-Json
if ($taskRoute.loader -ne 'forge:separate_transforms' -or $taskRoute.perspectives.gui.textures.layer0 -ne 'apocalypse_firstlight:item/service_pistol_icon') { throw 'Static icon routing missing' }
$taskBones = $taskGeo.'minecraft:geometry'[0].bones
$taskCubeCount = ($taskBones | ForEach-Object { @($_.cubes).Where({ $null -ne $_ }).Count } | Measure-Object -Sum).Sum
if ($taskCubeCount -ne 77 -or @($taskBones | Where-Object name -like '*arm_reference*').Count -ne 0) { throw 'Runtime geometry/reference exclusion failed' }
foreach ($taskAnchor in @('right_hand_anchor','left_hand_anchor','muzzle_anchor','sight_anchor','ejection_anchor')) {
    if (!($taskBones | Where-Object name -eq $taskAnchor)) { throw "Missing $taskAnchor" }
}
foreach ($taskContext in $taskSource.display.PSObject.Properties.Name) {
    # Source GUI edits do not change V0.4.1's independent static icon route.
    if ($taskContext -eq 'gui') { continue }
    if (($taskDisplay.display.$taskContext | ConvertTo-Json -Depth 20 -Compress) -cne ($taskSource.display.$taskContext | ConvertTo-Json -Depth 20 -Compress)) { throw "Display changed: $taskContext" }
}
$taskKeysChecked = 0
foreach ($taskAnim in $taskSource.animations) {
    $taskExport = $taskAnimation.animations.($taskAnim.name)
    if ($taskExport.animation_length -ne $taskAnim.length) { throw 'Animation length changed' }
    foreach ($taskTrack in $taskAnim.animators.PSObject.Properties.Value) {
        foreach ($taskChannel in @('position','rotation','scale')) {
            $taskKeys = @($taskTrack.keyframes | Where-Object channel -eq $taskChannel | Sort-Object time)
            for ($taskIndex=0; $taskIndex -lt $taskKeys.Count; $taskIndex++) {
                $taskKey = $taskKeys[$taskIndex]
                $taskTime = ([double]$taskKey.time).ToString('0.###',[Globalization.CultureInfo]::InvariantCulture)
                $taskExportKey = $taskExport.bones.($taskTrack.name).($taskChannel).($taskTime)
                if (!$taskExportKey) { throw "Missing exact key $($taskTrack.name)/$taskChannel/$taskTime" }
                $taskExpected = @([double]$taskKey.data_points[0].x,[double]$taskKey.data_points[0].y,[double]$taskKey.data_points[0].z)
                if ($taskChannel -eq 'position' -or $taskChannel -eq 'rotation') { $taskExpected[0] *= -1 }
                if ($taskChannel -eq 'rotation') { $taskExpected[1] *= -1 }
                for ($taskAxis=0; $taskAxis -lt 3; $taskAxis++) {
                    if ([Math]::Abs($taskExportKey.vector[$taskAxis]-$taskExpected[$taskAxis]) -gt 0.0000001) { throw 'Animation value differs' }
                }
                if ($taskIndex -gt 0 -and $taskKeys[$taskIndex-1].interpolation -eq 'step' -and $taskExportKey.easing -ne 'afl_hold') { throw 'Step interpolation lost' }
                $taskKeysChecked++
            }
        }
    }
}
foreach ($taskAction in $taskSounds.Keys) {
    $taskPath = Join-Path $taskAssets "sounds/weapons/service_pistol/service_pistol_$taskAction.ogg"
    $taskBytes = [IO.File]::ReadAllBytes($taskPath)
    if ([Text.Encoding]::ASCII.GetString($taskBytes,0,4) -ne 'OggS') { throw "Invalid runtime OGG $taskAction" }
    if ($SoundSourceDirectory -and (Get-FileHash $taskPath).Hash -cne (Get-FileHash (Join-Path $SoundSourceDirectory $taskSounds[$taskAction])).Hash) { throw 'OGG source mismatch' }
}
"PASS: $taskCubeCount runtime cubes; $($taskBones.Count) bones; $taskKeysChecked exact animation keys; source texture, display, anchors and OGG verified."
