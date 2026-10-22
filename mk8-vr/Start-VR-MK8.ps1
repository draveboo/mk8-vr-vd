# Launches Cemu with Mario Kart 8 + the CEMU VR Vulkan layer (Milestone 1).
# Stages the layer, installs/enables the MarioKart8_VR graphic pack,
# sets the loader environment, runs Cemu, and restores settings afterwards.
$ErrorActionPreference = 'Stop'

$root   = 'D:\mk8-VR'
$cemu   = 'D:\cemu-2.6-windows-x64\Cemu_2.6\Cemu.exe'
$game   = 'D:\wiiU\MARIO KART 8 [Game] [000500001010ec00]\code\Turbo.rpx'
$data   = Join-Path $env:APPDATA 'Cemu'
$settingsFile = Join-Path $data 'settings.xml'
$packName = 'MarioKart8_VR'
$runtime = Join-Path $root 'runtime'

function Say($m) { Write-Host ('[MK8-VR] ' + $m) -ForegroundColor Cyan }

if (-not (Test-Path -LiteralPath $cemu))   { throw "Cemu not found: $cemu" }
if (-not (Test-Path -LiteralPath $game))   { throw "Game not found: $game" }
if (-not (Test-Path -LiteralPath $settingsFile)) { throw "settings.xml not found: $settingsFile" }

# 1. Stage the Vulkan layer (JSON + DLL side by side; library_path is relative).
New-Item -ItemType Directory -Force -Path $runtime | Out-Null
Copy-Item (Join-Path $root 'build\sm3dw\cemuvr_layer.dll') $runtime -Force
Copy-Item (Join-Path $root 'mk8-vr\layer\VK_LAYER_CEMUVR_core.json') $runtime -Force
Say "Layer staged in $runtime"

# 2. Install the graphic pack into Cemu's graphicPacks folder.
$packDst = Join-Path (Join-Path $data 'graphicPacks') $packName
New-Item -ItemType Directory -Force -Path $packDst | Out-Null
Copy-Item -Recurse -Force (Join-Path $root "mk8-vr\graphicPacks\$packName\*") $packDst
Say "Pack installed: $packDst"

# 3. Back up settings.xml and enable the pack.
$backup = $settingsFile + '.vr-backup'
Copy-Item -Force $settingsFile $backup
[xml]$xml = Get-Content -LiteralPath $settingsFile
$gp = $xml.SelectSingleNode('//GraphicPack')
if (-not $gp) { throw 'No <GraphicPack> node in settings.xml' }
foreach ($e in @($gp.SelectNodes('Entry'))) {
    if ($e.GetAttribute('filename') -like "*/$packName/rules.txt") { [void]$gp.RemoveChild($e) }
}
$entry = $xml.CreateElement('Entry')
$entry.SetAttribute('filename', "graphicPacks/$packName/rules.txt")
[void]$gp.AppendChild($entry)
$xml.Save($settingsFile)
Say 'Pack enabled in settings.xml (backup at settings.xml.vr-backup)'

# 4. Layer environment.
$env:VK_LAYER_PATH = $runtime
$env:VK_INSTANCE_LAYERS = 'VK_LAYER_CEMUVR_core'
$env:VK_LOADER_LAYERS_ENABLE = 'VK_LAYER_CEMUVR_core'
$env:CEMUVR_ENABLE = '1'
Say 'VK layer environment set'

# 5. Launch Cemu and wait.
Say "Launching Cemu with MK8 (VR session; watch the Cemu log for reference.* lines)"
$proc = Start-Process -FilePath $cemu -ArgumentList "`"$game`"" -PassThru
$proc.WaitForExit()

# 6. Restore settings.xml so normal play is untouched.
Copy-Item -Force $backup $settingsFile
Say 'Settings restored. Done.'
