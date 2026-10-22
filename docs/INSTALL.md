# Install and run the current layer

## What “out of the box” requires

The Windows x64 release ZIP should include the prebuilt Vulkan layer DLL, its loader manifest, the MK8 Cemu graphic pack, and the launcher. A user still needs their own Cemu installation, a legally obtained compatible Mario Kart 8 Wii U v4.2 game/update, and a working OpenXR runtime. No game image or keys are included.

If you have only cloned/downloaded the source archive, it does not contain the compiled layer DLL. Build it using [BUILD.md](BUILD.md), or download the install ZIP from GitHub Releases.

## Requirements

- Windows x64.
- Cemu 2.6, initialized once so its settings file exists.
- Mario Kart 8 v4.2 for Wii U, with the matching update installed in Cemu.
- Quest with Virtual Desktop Streamer and Virtual Desktop connected.
- Virtual Desktop’s VDXR runtime selected as the active OpenXR runtime.
- Microsoft Visual C++ x64 runtime if Windows reports a missing runtime DLL.

The address patches target game checksum `0x9F0A90B7`; other versions are unsupported.

## Install and launch

1. Download and extract the current Windows x64 install ZIP.
2. Connect the Quest through Virtual Desktop and confirm VDXR is the active OpenXR runtime.
3. Double-click `Start-MK8-VR.cmd`.
4. On the first launch, select `Cemu.exe` and your own `Turbo.rpx` when prompted.
5. Leave the launcher open while Cemu runs. Exit Cemu normally; the launcher restores the original `settings.xml`.

The launcher installs the included `MarioKart8_VR` graphic pack into the detected Cemu data folder, enables it for this launch, sets Vulkan layer variables only for the child Cemu process, and waits for Cemu to exit. It does not install a system-wide Vulkan layer.

## Current behavior and limits

The current layer demonstrates marker transport and stereo projection submission using MK8’s existing TV and DRC scan-target images. It is experimental: frame pairing can be imperfect, and full headset-driven game-camera rotation/6DOF is not yet established. See [STATUS.md](STATUS.md).

## Troubleshooting

- **No VR session:** check that Virtual Desktop is connected and VDXR is selected as the active OpenXR runtime.
- **Layer not loaded:** keep the launcher open, check that `layer/cemuvr_layer.dll` and the adjacent JSON manifest are present, and check Cemu’s log for Vulkan layer loading errors.
- **Pack not applied:** confirm the selected RPX is the supported v4.2 game and that the Cemu settings file contains a `GraphicPack` section.
- **Settings not restored after forced termination:** restore the sibling `settings.xml.mk8vr-backup` manually after closing Cemu.
- **Cemu/game crashes:** close Cemu, restore the backup settings if needed, and report the Cemu version, game checksum, and relevant log excerpt. Do not include game files or keys in bug reports.
