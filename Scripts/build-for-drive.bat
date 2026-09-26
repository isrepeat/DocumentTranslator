@echo off
setlocal

chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\build.ps1" -Command build-for-drive %*
set "buildExitCode=%ERRORLEVEL%"

echo.
if not "%buildExitCode%"=="0" (
    echo Build failed with exit code %buildExitCode%.
) else (
    echo APK is ready for Google Drive.
)
pause
exit /b %buildExitCode%