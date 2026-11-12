@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0.."

echo ============================================
echo   Campus Platform - backend (port 8106)
echo ============================================
echo.

if "%JAVA_HOME%"=="" (
    echo [ERROR] JAVA_HOME is not set. Point it to a JDK 17 directory.
    pause
    exit /b 1
)
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] java.exe not found under JAVA_HOME=%JAVA_HOME%
    pause
    exit /b 1
)

call :portup 8106
if not errorlevel 1 (
    echo [SKIP] port 8106 is already in use - backend seems to be running.
    echo        health: http://localhost:8106/api/health
    exit /b 0
)

if "%DB_PASSWORD%"=="" set "DB_PASSWORD=123456"

set "JAR=server\portfolio-server\target\portfolio-server-1.0.0.jar"
set "SZ=0"
if exist "%JAR%" for %%A in ("%JAR%") do set "SZ=%%~zA"
if "%SZ%"=="0" goto build
if %SZ% LSS 5000000 goto build
goto runjar

:build
echo [1/2] jar missing or incomplete, building with Maven ...
where mvn >nul 2>nul
if errorlevel 1 (
    echo [ERROR] mvn not found in PATH. Install Maven or use your IDE to build.
    pause
    exit /b 1
)
pushd server
call mvn -q -DskipTests package
if errorlevel 1 (
    popd
    echo [ERROR] build failed. Check the Maven output above.
    pause
    exit /b 1
)
popd
if not exist "%JAR%" (
    echo [ERROR] build finished but jar not found: %JAR%
    pause
    exit /b 1
)

:runjar
echo [2/2] starting backend ...
echo     api    http://localhost:8106/api
echo     health http://localhost:8106/api/health
echo     press Ctrl+C to stop
echo.
"%JAVA_HOME%\bin\java.exe" -Duser.timezone=Asia/Shanghai -jar "%JAR%"
echo.
echo backend stopped.
pause

:portup
rem returns 0 when the TCP port is open, 1 otherwise
powershell -NoProfile -Command "try{$c=New-Object Net.Sockets.TcpClient;$t=$c.ConnectAsync('127.0.0.1',%~1);if($t.Wait(800) -and $c.Connected){$c.Close();exit 0}else{$c.Close();exit 1}}catch{exit 1}"
exit /b %errorlevel%
