@echo off
REM ============================================================
REM  PTK / Shizuku one-click recovery
REM  Run this after a phone reboot (USB connected) to:
REM    1. Start the Shizuku privileged server
REM    2. Re-enable wireless ADB on port 5555 (for Automate)
REM
REM  Background: Shizuku does NOT survive a reboot on a non-rooted
REM  device, and Android disables wireless debugging at every boot.
REM  This script restores both in one shot.
REM ============================================================

setlocal

REM --- Locate adb ---
set "ADB=D:\AndroidSdk\platform-tools\adb.exe"
if not exist "%ADB%" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB%" (
  echo [ERROR] adb.exe not found. Edit ADB path at top of this script.
  pause
  exit /b 1
)

set "START_SH=/storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh"

echo.
echo === Waiting for USB device ===
"%ADB%" wait-for-device

echo.
echo === Enabling wireless ADB on 5555 (for Automate auto-revive) ===
REM Do this FIRST: 'adb tcpip' restarts the adb daemon and can kill a
REM freshly-started shizuku_server, so start Shizuku LAST.
"%ADB%" tcpip 5555
timeout /t 4 /nobreak >nul
"%ADB%" connect 192.168.0.132:5555

echo.
echo === Starting Shizuku server ===
"%ADB%" wait-for-device
"%ADB%" shell "sh %START_SH%"

echo.
echo === Verifying server is alive ===
timeout /t 2 /nobreak >nul
"%ADB%" shell "ps -A -o PID,PPID,USER,NAME | grep shizuku_server"

echo.
echo Done. If a "shizuku_server" line appeared above (PPID 1), Shizuku is running.
echo You can unplug USB now.
pause
endlocal
