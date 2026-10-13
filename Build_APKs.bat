@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo Building Gander Debug and Release APKs...
echo ===================================================

:: Ensure working directory is the script directory
cd /d "%~dp0"

:: Prevent AGP AndroidLocationsException caused by conflicting env vars
set "ANDROID_PREFS_ROOT="

:: Set ANDROID_USER_HOME dynamically based on USERPROFILE
if not defined ANDROID_USER_HOME (
    set "ANDROID_USER_HOME=%USERPROFILE%\.android"
)

:: Detect ANDROID_HOME if not already defined
if not defined ANDROID_HOME (
    if not defined ANDROID_SDK_ROOT (
        if exist "%LOCALAPPDATA%\Android\Sdk" (
            set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
            set "ANDROID_SDK_ROOT=%LOCALAPPDATA%\Android\Sdk"
        )
    ) else (
        set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
    )
)

:: Detect JAVA_HOME if not already defined
if not defined JAVA_HOME (
    if exist "%ProgramFiles%\Android\Android Studio\jbr" (
        set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
    ) else if exist "%LOCALAPPDATA%\Android\Android Studio\jbr" (
        set "JAVA_HOME=%LOCALAPPDATA%\Android\Android Studio\jbr"
    )
)

call gradlew.bat assembleDebug assembleRelease

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ===================================================
    echo Build Successful!
    echo Output APKs:
    echo Debug:   app\build\outputs\apk\debug\
    echo Release: app\build\outputs\apk\release\
    echo ===================================================
) else (
    echo.
    echo ===================================================
    echo Build Failed with error code %ERRORLEVEL%.
    echo ===================================================
)

pause
