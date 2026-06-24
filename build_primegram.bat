@echo off
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo ===================================================
echo Starting PrimeGram APK Build (Release, ARM64)
echo ===================================================

java -classpath gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :TMessagesProj_AppStandalone:assembleAfatRelease --rerun-tasks

if %ERRORLEVEL% equ 0 (
    echo.
    echo ===================================================
    echo BUILD SUCCESSFUL!
    if exist "TMessagesProj_AppStandalone\build\outputs\apk\afat\release\TMessagesProj_AppStandalone-afat-release.apk" (
        move /y "TMessagesProj_AppStandalone\build\outputs\apk\afat\release\TMessagesProj_AppStandalone-afat-release.apk" "PrimeGram.apk" >nul
        echo APK is ready at: D:\Telegram-P\PrimeGram.apk
    ) else (
        echo Error: APK file was not found in outputs!
    )
    echo ===================================================
) else (
    echo.
    echo ===================================================
    echo BUILD FAILED with error code %ERRORLEVEL%
    echo ===================================================
)
