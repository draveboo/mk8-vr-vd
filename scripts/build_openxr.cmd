@echo off
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat" >nul 2>&1
if errorlevel 1 echo VCVARS_FAILED & exit /b 1
echo dummy> "D:\mk8-VR\deps\vulkan-1.lib"
cmake -S "D:\mk8-VR\deps\OpenXR-SDK\OpenXR-SDK-main" -B "D:\mk8-VR\build\openxr" -G Ninja -DCMAKE_BUILD_TYPE=Release -DBUILD_TESTS=OFF -DBUILD_CONFORMANCE_TESTS=OFF -DBUILD_API_LAYERS=OFF -DBUILD_JSONCPP=OFF
if errorlevel 1 echo CONFIGURE_FAILED & exit /b 1
cmake --build "D:\mk8-VR\build\openxr"
if errorlevel 1 echo BUILD_FAILED & exit /b 1
echo OPENXR_DONE
dir /b "D:\mk8-VR\build\openxr\src\loader\*.lib"
