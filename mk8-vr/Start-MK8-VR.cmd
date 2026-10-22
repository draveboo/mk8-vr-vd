@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Start-MK8-VR.ps1" %*
if errorlevel 1 pause
