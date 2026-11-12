@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0..\web"

echo ============================================
echo   Campus Platform - frontend (port 5173)
echo ============================================
echo.

where node >nul 2>nul
if errorlevel 1 (
    echo [ERROR] node not found in PATH. Install Node.js 18+ first.
    pause
    exit /b 1
)

call :portup 5173
if not errorlevel 1 (
    echo [SKIP] port 5173 is already in use - frontend seems to be running.
    echo        open http://localhost:5173
    exit /b 0
)

if not exist node_modules (
    echo [1/2] installing dependencies ...
    call npm install
    if errorlevel 1 (
        echo [ERROR] npm install failed.
        pause
        exit /b 1
    )
) else (
    echo [1/2] node_modules found, skip install.
)

echo [2/2] starting dev server, press Ctrl+C to stop
echo     open http://localhost:5173
echo.
call npm run dev -- --port 5173 --strictPort
echo.
echo frontend stopped.
pause

:portup
rem returns 0 when the TCP port is open, 1 otherwise
powershell -NoProfile -Command "try{$c=New-Object Net.Sockets.TcpClient;$t=$c.ConnectAsync('127.0.0.1',%~1);if($t.Wait(800) -and $c.Connected){$c.Close();exit 0}else{$c.Close();exit 1}}catch{exit 1}"
exit /b %errorlevel%
