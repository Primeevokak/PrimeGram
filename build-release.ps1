# PrimeGram Release Build Script for Windows
# Run this script to compile the application locally.

$ErrorActionPreference = "Stop"

Write-Host "====== PrimeGram Release Compiler ======" -ForegroundColor Cyan

# 1. Check Java JDK 17+ version
Write-Host "[*] Checking Java version..." -ForegroundColor Gray
if (Get-Command java -ErrorAction SilentlyContinue) {
    $javaVer = java -version 2>&1 | Out-String
    Write-Host "Found Java version: $javaVer" -ForegroundColor DarkGray
} else {
    Write-Error "Java JDK is not installed or not in PATH! Please install JDK 17+ and try again."
}

# 2. Check local.properties and Android SDK
Write-Host "[*] Checking Android SDK path..." -ForegroundColor Gray
if (Test-Path "local.properties") {
    $sdkDirLine = Get-Content "local.properties" | Where-Object { $_ -match "^sdk\.dir=" }
    if ($sdkDirLine) {
        Write-Host "SDK Configured: $sdkDirLine" -ForegroundColor DarkGray
    } else {
        Write-Warning "sdk.dir is missing in local.properties. The build might fail."
    }
} else {
    Write-Warning "local.properties is missing. Gradle will look for ANDROID_HOME environment variable."
}

# 3. Clean and build the release APK
Write-Host "[*] Compiling PrimeGram (Release Build)..." -ForegroundColor Yellow

# Using Standalone variant for release build
cmd.exe /c ".\gradlew.bat clean :TMessagesProj_AppStandalone:assembleRelease --no-daemon"

# 4. Locate the generated APK file
Write-Host "[*] Locating generated APK files..." -ForegroundColor Gray
$apkPath = "TMessagesProj_AppStandalone\build\outputs\apk\release"
if (Test-Path $apkPath) {
    $apks = Get-ChildItem -Path $apkPath -Filter "*.apk" -Recurse
    if ($apks) {
        Write-Host "`n[+] Compilation Success!" -ForegroundColor Green
        Write-Host "Generated APKs:" -ForegroundColor Green
        foreach ($apk in $apks) {
            Write-Host "-> $($apk.FullName)" -ForegroundColor Cyan
        }
    } else {
        Write-Warning "Compilation succeeded but no APK files were found in output directory."
    }
} else {
    Write-Error "Build finished, but output directory does not exist. Check compile errors above."
}
