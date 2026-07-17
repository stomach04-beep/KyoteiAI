@echo off
cd /d "%~dp0"

REM Set Java from Android Studio
if exist "C:\Program Files\Android\Android Studio\jbr" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
) else if exist "C:\Program Files\Android\Android Studio\jre" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jre"
) else (
    echo ERROR: Android Studio Java not found.
    if not defined AUTO_BUILD pause
    exit /b 1
)

echo =====================================
echo   KyoteiAI  Build and Install
echo =====================================
echo.

echo Building APK...
call "%~dp0gradlew.bat" assembleDebug
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo =====================================
    echo   BUILD FAILED. Check error above.
    echo =====================================
    if not defined AUTO_BUILD pause
    exit /b 1
)

echo.
echo Installing APK to device...
call "C:\Users\81905\AndroidStudioProjects\install_apk_wifi_or_usb.bat" "%~dp0app\build\outputs\apk\debug\app-debug.apk"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo =====================================
    echo   INSTALL FAILED. APK was built but not installed.
    echo   APK: %~dp0app\build\outputs\apk\debug\app-debug.apk
    echo =====================================
    if not defined AUTO_BUILD pause
    exit /b 1
)

echo.
echo =====================================
echo   SUCCESS! Check your phone.
echo =====================================
if not defined AUTO_BUILD pause
