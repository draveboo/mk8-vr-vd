# Reproduction notes

For end-user setup, use [INSTALL.md](INSTALL.md). This file summarizes the original research environment and reproduction workflow.

## Required inputs

- A legally obtained Mario Kart 8 Wii U v4.2 game/update installed for Cemu.
- Cemu with the matching Cemu Vulkan layer support.
- Virtual Desktop Streamer on the PC and Virtual Desktop on a Quest headset, using an OpenXR runtime that Cemu can select.
- Visual Studio C++ build tools, CMake, and the dependencies listed by the layer source tree.
- Ghidra 12.1.4 for the reverse-engineering workflow.

Do not commit game dumps, keys, Cemu user data, headset logs containing private identifiers, or built game-specific binaries.

## Recorded setup

The original machine used Windows, a Cemu 2.6 build, Visual Studio 2022 Build Tools, CMake, and Ghidra 12.1.4. The analyzed Ghidra program is `/Turbo_v42_plain.rpx`; `/Turbo_v42_fixed.rpx` is truncated and is the wrong program for the cited address map.

The original source tree was under `D:\mk8-VR\mk8-vr`; reverse-engineering artifacts were under `D:\mk8-VR\work`. Scripts and launchers contain machine-specific absolute paths. Inspect and adapt them before running.

## Build and run

Use the repository’s maintained build and launch scripts after checking their contents and paths. The research log records `scripts\build_mk8vr.cmd` and `runtime\launch-mk8-vr.cmd` as the tested setup. Set the VR layer environment variables using that launcher; avoid launching Cemu through a second shell that loses those variables.

The working VD path used:

```text
CEMUVR_REFERENCE_UNPOSED_SURFACE=0
CEMUVR_REFERENCE_POSE_STAMP=0
```

The log also records disabling Cemu `GX2DrawdoneSync` after it caused severe synchronization cost. This is environment-specific; verify behavior in the target Cemu version.

## Reverse-engineering workflow

1. Confirm the RPX checksum and version before using any address.
2. Analyze `/Turbo_v42_plain.rpx`; decompress the text section first.
3. In Ghidra scripts, disassemble and create functions at target addresses before decompiling.
4. Resolve imports from relocation records. Import stubs are loader-filled zeros in the file, so identify GX2 calls by RELA entries and call-site instruction patterns.
5. Instrument candidate hooks with counters that increment at entry, before logging gates.
6. Verify counters during an actual race. Installed bytes alone do not prove the live path executes.
7. Change one hook or protocol behavior at a time and keep a rollback build.

## Evidence files

The chronological findings document names Ghidra output files (`re-out`, `re-out2`) and scripts (`ghidra-scripts`). Those artifacts may not be included in a documentation-only export. Keep the research workspace private and add selected small, redistributable excerpts to a public repo only after review.

