param(
    [string]$CemuExe,
    [string]$GameFile
)

$ErrorActionPreference = 'Stop'
$installRoot = $PSScriptRoot
$layerDir = Join-Path $installRoot 'layer'
$packSource = Join-Path $installRoot 'graphicPacks\MarioKart8_VR'
$layerDll = Join-Path $layerDir 'cemuvr_layer.dll'
$layerJson = Join-Path $layerDir 'VK_LAYER_CEMUVR_core.json'

function Select-File([string]$Title, [string]$Filter) {
    Add-Type -AssemblyName System.Windows.Forms
    $dialog = New-Object System.Windows.Forms.OpenFileDialog
    $dialog.Title = $Title
    $dialog.Filter = $Filter
    if ($dialog.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) {
        throw "No file selected for: $Title"
    }
    return $dialog.FileName
}

if (-not (Test-Path -LiteralPath $layerDll)) { throw "Layer DLL missing: $layerDll. Install the release package or build it from source." }
if (-not (Test-Path -LiteralPath $layerJson)) { throw "Vulkan layer manifest missing: $layerJson" }
if (-not (Test-Path -LiteralPath (Join-Path $packSource 'rules.txt'))) { throw "MK8 graphic pack missing: $packSource" }

if (-not $CemuExe) { $CemuExe = Select-File 'Select Cemu.exe' 'Cemu executable (Cemu.exe)|Cemu.exe' }
if (-not $GameFile) { $GameFile = Select-File 'Select your Mario Kart 8 v4.2 RPX' 'Wii U RPX (Turbo.rpx)|*.rpx|All files|*.*' }
if (-not (Test-Path -LiteralPath $CemuExe)) { throw "Cemu executable not found: $CemuExe" }
if (-not (Test-Path -LiteralPath $GameFile)) { throw "Game file not found: $GameFile" }

$cemuDir = Split-Path -Parent (Resolve-Path -LiteralPath $CemuExe).Path
$portableSettings = Join-Path $cemuDir 'settings.xml'
$appData = Join-Path $env:APPDATA 'Cemu'
$dataDir = if (Test-Path -LiteralPath $portableSettings) { $cemuDir } else { $appData }
$settingsFile = Join-Path $dataDir 'settings.xml'
if (-not (Test-Path -LiteralPath $settingsFile)) { throw "Cemu settings not found: $settingsFile. Start Cemu once, then retry." }

$packDestination = Join-Path (Join-Path $dataDir 'graphicPacks') 'MarioKart8_VR'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $packDestination) | Out-Null
New-Item -ItemType Directory -Force -Path $packDestination | Out-Null
Copy-Item -Path (Join-Path $packSource '*') -Destination $packDestination -Recurse -Force

$backup = "$settingsFile.mk8vr-backup"
Copy-Item -LiteralPath $settingsFile -Destination $backup -Force
$previousLayerPath = $env:VK_LAYER_PATH
$previousInstanceLayers = $env:VK_INSTANCE_LAYERS
$previousEnableLayers = $env:VK_LOADER_LAYERS_ENABLE
$previousCemuVrEnable = $env:CEMUVR_ENABLE
$restoreSettings = $false

try {
    [xml]$xml = Get-Content -LiteralPath $settingsFile
    $graphicPack = $xml.SelectSingleNode('//GraphicPack')
    if (-not $graphicPack) { throw 'Cemu settings.xml has no GraphicPack node. Run Cemu once to initialize settings.' }
    foreach ($entry in @($graphicPack.SelectNodes('Entry'))) {
        if ($entry.GetAttribute('filename') -like '*/MarioKart8_VR/rules.txt') { [void]$graphicPack.RemoveChild($entry) }
    }
    $entry = $xml.CreateElement('Entry')
    $entry.SetAttribute('filename', 'graphicPacks/MarioKart8_VR/rules.txt')
    [void]$graphicPack.AppendChild($entry)
    $xml.Save($settingsFile)
    $restoreSettings = $true

    $env:VK_LAYER_PATH = $layerDir
    $env:VK_INSTANCE_LAYERS = 'VK_LAYER_CEMUVR_core'
    $env:VK_LOADER_LAYERS_ENABLE = 'VK_LAYER_CEMUVR_core'
    $env:CEMUVR_ENABLE = '1'
    Write-Host 'Starting Cemu with the MK8 VR layer. Close Cemu normally to finish.' -ForegroundColor Cyan
    $process = Start-Process -FilePath $CemuExe -ArgumentList @('-g', ('"{0}"' -f $GameFile)) -WorkingDirectory $cemuDir -PassThru
    $process.WaitForExit()
}
finally {
    if ($restoreSettings -and (Test-Path -LiteralPath $backup)) {
        Copy-Item -LiteralPath $backup -Destination $settingsFile -Force
    }
    $env:VK_LAYER_PATH = $previousLayerPath
    $env:VK_INSTANCE_LAYERS = $previousInstanceLayers
    $env:VK_LOADER_LAYERS_ENABLE = $previousEnableLayers
    $env:CEMUVR_ENABLE = $previousCemuVrEnable
}
