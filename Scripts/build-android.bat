@echo off
setlocal

rem Double-click launcher for the PowerShell build pipeline.
chcp 65001 >nul
rem All optional arguments are forwarded, for example:
rem   build-android.bat -NativeOnly
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\build.ps1" -Command build-android %*
set "buildExitCode=%ERRORLEVEL%"

echo.
if not "%buildExitCode%"=="0" (
    echo Build failed with exit code %buildExitCode%.
) else (
    echo Build finished successfully.
)
pause
exit /b %buildExitCode%