@echo off
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat" >nul 2>&1
if errorlevel 1 echo VCVARS_FAILED & exit /b 1
cmake -S "D:\mk8-VR\mk8-vr\src-fork" -B "D:\mk8-VR\build\mk8vr" -G Ninja -DCMAKE_BUILD_TYPE=Release "-DOPENXR_INCLUDE_DIR=D:/mk8-VR/deps/OpenXR-SDK/OpenXR-SDK-main/include" "-DOPENXR_LOADER_LIBRARY=D:/mk8-VR/build/openxr/src/loader/openxr_loader.lib" "-DVulkan_INCLUDE_DIR=D:/mk8-VR/deps/Vulkan-Headers/Vulkan-Headers-main/include" "-DVulkan_LIBRARY=D:/mk8-VR/deps/vulkan-1.lib"
if errorlevel 1 echo CONFIGURE_FAILED & exit /b 1
cmake --build "D:\mk8-VR\build\mk8vr"
if errorlevel 1 echo BUILD_FAILED & exit /b 1
echo LAYER_BUILD_DONE
dir /b "D:\mk8-VR\build\mk8vr\*.dll"
