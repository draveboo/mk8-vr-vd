# Mario Kart 8 VR for Cemu

This repository contains the current Cemu Vulkan layer, MK8 graphic pack, configurable launcher, and reverse-engineering documentation for playing the Wii U version of Mario Kart 8 in a Quest headset through Virtual Desktop.

> **Status:** the current build can transport MK8’s TV/DRC images through the Cemu Vulkan layer and submit them through the Virtual Desktop OpenXR projection path. It is an experimental stereo/reference build, not a finished head-tracked 6DOF mod. Review [status and known limits](docs/STATUS.md).

## Try the current build

Download the versioned **Windows x64 install ZIP** from the project’s GitHub Releases page and extract it. The ZIP should contain:

```
Start-MK8-VR.cmd
Start-MK8-VR.ps1
layer/
  cemuvr_layer.dll
  VK_LAYER_CEMUVR_core.json
graphicPacks/MarioKart8_VR/
```

Then:

1. Install and run Cemu once. Use a legally obtained Mario Kart 8 Wii U v4.2 game and update.
2. In Virtual Desktop Streamer, select its VDXR OpenXR runtime. Connect the Quest and make sure ordinary OpenXR apps work.
3. Run `Start-MK8-VR.cmd`. On first use, select `Cemu.exe`, then your own `Turbo.rpx`.
4. The launcher installs/enables the included graphic pack for the session, loads the Vulkan layer for that Cemu process, and restores the prior Cemu settings when Cemu exits normally.

The release ZIP is the intended out-of-the-box route. If no ZIP is attached to a GitHub Release yet, use the source build instructions in [docs/INSTALL.md](docs/INSTALL.md); source alone does not include the compiled DLL.

## Build from source

See [docs/BUILD.md](docs/BUILD.md) for Windows prerequisites and the CMake build command. A maintainer with the required SDK dependencies can also package a release ZIP using the repo’s packaging instructions.

## What is included

- `mk8-vr/graphicPacks/MarioKart8_VR`: guest-side PPC patch and marker protocol.
- `mk8-vr/src-fork/core`: Cemu Vulkan/OpenXR layer source, forked from the MIT-licensed Super Mario 3D World VR project with MK8-specific work.
- `mk8-vr/Start-MK8-VR.*`: configurable Windows launcher.
- `docs/`: architecture, setup, evidence, current status, and the full reverse-engineering log.
- `research/`: Ghidra scripts and generated analysis excerpts.

## Rights and third-party notices

Do not include Nintendo game files, keys, or user Cemu data. Users provide their own game files. The layer is MIT licensed; retain [its license](mk8-vr/src-fork/LICENSE), third-party notices, and upstream attributions when redistributing. The embedded upstream source has no Git metadata in this export, so repository history is not preserved; see [upstream and license notes](docs/UPSTREAM.md).

## Research starting points

- [Current status](docs/STATUS.md)
- [Pipeline architecture](docs/ARCHITECTURE.md)
- [Installation and requirements](docs/INSTALL.md)
- [Build instructions](docs/BUILD.md)
- [Evidence guide](docs/EVIDENCE.md)
- [Full reverse-engineering log](docs/reverse-engineering-log.md)
