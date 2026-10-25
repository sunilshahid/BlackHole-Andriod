@echo off
setlocal

echo ========================================================
echo   Building BlackHole Android Release APK
echo ========================================================

REM Locate JDK 17
if defined JAVA_HOME (
    echo Using existing JAVA_HOME: %JAVA_HOME%
) else if exist "C:\Users\Sunil Shahid\Desktop\jdk-17\jdk17.0.20_12" (
    set "JAVA_HOME=C:\Users\Sunil Shahid\Desktop\jdk-17\jdk17.0.20_12"
    echo Using Desktop JDK: %JAVA_HOME%
) else if exist "C:\Users\Sunil Shahid\Desktop\jdk-17" (
    set "JAVA_HOME=C:\Users\Sunil Shahid\Desktop\jdk-17"
    echo Using Desktop JDK: %JAVA_HOME%
) else (
    echo [WARNING] JAVA_HOME is not set. Assuming java is available in PATH.
)

if defined JAVA_HOME (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
)

echo.
echo Running Gradle assembleRelease...
call gradlew.bat assembleRelease

if errorlevel 1 (
    echo.
    echo [ERROR] Gradle release build failed.
    exit /b 1
)

echo.
echo ========================================================
echo   Build Successful!
echo   Output APK: app\build\outputs\apk\release\app-release.apk
echo ========================================================
