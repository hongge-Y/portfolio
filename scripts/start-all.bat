@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0.."

echo ============================================
echo   Campus Platform - one click start
echo ============================================
echo.

call :portup 3307
if errorlevel 1 (
    echo [WARN] MySQL on 3307 is not listening.
    echo        Start MySQL 8 first, then run this script again.
    echo.
    pause
    exit /b 1
)
echo [ OK ] MySQL 3307 is up.

call :portup 8106
if errorlevel 1 (
    echo [ .. ] starting backend ...
    start "backend 8106" cmd /k "%~dp0start-backend.bat"
) else (
    echo [SKIP] backend already running.
)

call :portup 5173
if errorlevel 1 (
    echo [ .. ] starting frontend ...
    start "frontend 5173" cmd /k "%~dp0start-frontend.bat"
) else (
    echo [SKIP] frontend already running.
)

echo.
echo [wait] backend health check (max 120s) ...
set /a n=0
:waitbe
call :portup 8106
if not errorlevel 1 goto beok
set /a n+=1
if !n! geq 60 goto betimeout
timeout /t 2 /nobreak >nul
goto waitbe

:betimeout
echo [WARN] backend not ready yet, check the "backend 8106" window.
goto waitfe

:beok
echo [ OK ] backend http://localhost:8106/api/health

:waitfe
echo [wait] frontend (max 90s) ...
set /a m=0
:waitfe_loop
call :portup 5173
if not errorlevel 1 goto feok
set /a m+=1
if !m! geq 45 goto fefail
timeout /t 2 /nobreak >nul
goto waitfe_loop

:feok
echo [ OK ] frontend http://localhost:5173
if /I "%NO_BROWSER%"=="1" goto done
start "" http://localhost:5173
goto done

:fefail
echo [WARN] frontend not ready yet, check the "frontend 5173" window.

:done
echo.
echo ============================================
echo   demo accounts (password 123456)
echo     2025000001  student
echo     1001001     counselor
echo     1003001     secretary
echo     13900001111 parent
echo     admin       admin  (/#/admin)
echo   register a new student at /#/register
echo ============================================
echo   keep this window and the two service windows open.
echo   close a service window to stop it.
pause
exit /b 0

:portup
rem returns 0 when the TCP port is open, 1 otherwise
powershell -NoProfile -Command "try{$c=New-Object Net.Sockets.TcpClient;$t=$c.ConnectAsync('127.0.0.1',%~1);if($t.Wait(800) -and $c.Connected){$c.Close();exit 0}else{$c.Close();exit 1}}catch{exit 1}"
exit /b %errorlevel%
