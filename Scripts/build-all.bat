@echo off
setlocal

chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\build.ps1" -Command build-all %*
set "buildExitCode=%ERRORLEVEL%"

echo.
if not "%buildExitCode%"=="0" (
    echo Build or upload failed with exit code %buildExitCode%.
) else (
    echo APK is ready in Google Drive.
)
pause
exit /b %buildExitCode%