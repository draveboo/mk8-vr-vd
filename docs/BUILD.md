# Build and package

## Windows build requirements

- Windows x64 and Visual Studio 2022 C++ build tools.
- CMake 3.20 or later.
- Vulkan SDK or Vulkan headers and loader library.
- Khronos OpenXR headers and x64 OpenXR loader library.
- Python 3.9+ for release packaging tools.

The source build does not require a game image. The runtime install ZIP does require the compiled DLL.

## Configure and build

From the repository root in a Visual Studio developer PowerShell, set the SDK paths for your machine:

```powershell
cmake -S mk8-vr/src-fork -B build -A x64 `
  -DOPENXR_INCLUDE_DIR="C:/path/to/OpenXR/include" `
  -DOPENXR_LOADER_LIBRARY="C:/path/to/openxr_loader.lib" `
  -DVulkan_INCLUDE_DIR="C:/path/to/Vulkan/Include" `
  -DVulkan_LIBRARY="C:/path/to/Vulkan/Lib/vulkan-1.lib"
cmake --build build --config Release
```

The resulting `cemuvr_layer.dll` is under `build/Release` (or the generator’s corresponding output directory). Copy it beside `VK_LAYER_CEMUVR_core.json` in the install package’s `layer/` directory.

## Prepare an install ZIP

Package only these files:

```
Start-MK8-VR.cmd
Start-MK8-VR.ps1
layer/cemuvr_layer.dll
layer/VK_LAYER_CEMUVR_core.json
graphicPacks/MarioKart8_VR/**
LICENSE and third-party notices
README.md
docs/INSTALL.md
docs/STATUS.md
```

Name the archive with the version and platform, for example `MarioKart8-VR-v0.1.0-windows-x64.zip`. Upload that ZIP as a GitHub Release asset. Do not commit the DLL or ZIP into the source tree; the repo ignore rules exclude compiled outputs.

Before publishing, verify the DLL was built from the same source revision and that the archive has no game data, credentials, local paths, or runtime logs.
