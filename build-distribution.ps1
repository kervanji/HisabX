# HisabX Distribution Build Script
# This script creates a complete distributable package with custom JRE

$ErrorActionPreference = "Stop"

# Configuration
$APP_NAME = "HisabX"
$APP_VERSION = "1.0.0"
$JAVAFX_VERSION = "17.0.10"
$JAVA_VERSION = "17"

# Directories
$PROJECT_DIR = $PSScriptRoot
$TARGET_DIR = "$PROJECT_DIR\target"
$DIST_DIR = "$PROJECT_DIR\distribution"
$RUNTIME_DIR = "$DIST_DIR\runtime"
$JAVAFX_JMODS_DIR = "$PROJECT_DIR\javafx-jmods-$JAVAFX_VERSION"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Building $APP_NAME Distribution Package" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Step 1: Clean previous builds
Write-Host "`n[1/6] Cleaning previous builds..." -ForegroundColor Yellow
if (Test-Path $DIST_DIR) {
    Remove-Item -Recurse -Force $DIST_DIR
}
New-Item -ItemType Directory -Force -Path $DIST_DIR | Out-Null

# Step 2: Build the application with Maven
Write-Host "`n[2/6] Building application with Maven..." -ForegroundColor Yellow
mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "Maven build failed!" -ForegroundColor Red
    exit 1
}

# Step 3: Check for JavaFX jmods
Write-Host "`n[3/6] Checking JavaFX jmods..." -ForegroundColor Yellow
if (-not (Test-Path $JAVAFX_JMODS_DIR)) {
    Write-Host "JavaFX jmods not found!" -ForegroundColor Red
    Write-Host "Please download JavaFX jmods from: https://gluonhq.com/products/javafx/" -ForegroundColor Yellow
    Write-Host "Extract to: $JAVAFX_JMODS_DIR" -ForegroundColor Yellow
    Write-Host "`nAlternatively, run this command to download:" -ForegroundColor Cyan
    Write-Host "Invoke-WebRequest -Uri 'https://download2.gluonhq.com/openjfx/$JAVAFX_VERSION/openjfx-$JAVAFX_VERSION`_windows-x64_bin-jmods.zip' -OutFile 'javafx-jmods.zip'" -ForegroundColor Gray
    exit 1
}

# Step 4: Create custom runtime with jlink
Write-Host "`n[4/6] Creating custom Java runtime..." -ForegroundColor Yellow
$modulePath = "$env:JAVA_HOME\jmods;$JAVAFX_JMODS_DIR"
$modules = "java.base,java.desktop,java.sql,java.naming,java.xml,java.logging,java.management,java.instrument,java.prefs,jdk.unsupported,javafx.controls,javafx.fxml,javafx.swing,javafx.graphics"

jlink --module-path $modulePath `
      --add-modules $modules `
      --output $RUNTIME_DIR `
      --strip-debug `
      --no-header-files `
      --no-man-pages `
      --compress=2

if ($LASTEXITCODE -ne 0) {
    Write-Host "jlink failed!" -ForegroundColor Red
    exit 1
}

# Step 5: Copy application files
Write-Host "`n[5/6] Copying application files..." -ForegroundColor Yellow
Copy-Item "$TARGET_DIR\inventory-management-$APP_VERSION.jar" "$DIST_DIR\$APP_NAME.jar"
Copy-Item "$PROJECT_DIR\hisabx.db" "$DIST_DIR\" -ErrorAction SilentlyContinue

# Create lib directory and copy dependencies
New-Item -ItemType Directory -Force -Path "$DIST_DIR\lib" | Out-Null
Copy-Item "$TARGET_DIR\*.jar" "$DIST_DIR\lib\" -Exclude "inventory-management-*.jar"

# Step 6: Create launcher script (VBS for no console window)
Write-Host "`n[6/6] Creating launcher..." -ForegroundColor Yellow

# Create VBS launcher that runs without console window
$vbsLauncherContent = @"
Set objShell = CreateObject("WScript.Shell")
Set objFSO = CreateObject("Scripting.FileSystemObject")

' Get the directory where this script is located
strScriptPath = objFSO.GetParentFolderName(WScript.ScriptFullName)

' Build the command to run with javaw.exe (no console window)
strCommand = """" & strScriptPath & "\runtime\bin\javaw.exe"" -jar """ & strScriptPath & "\$APP_NAME.jar"""

' Run without showing console window
objShell.Run strCommand, 0, False

Set objShell = Nothing
Set objFSO = Nothing
"@
Set-Content -Path "$DIST_DIR\$APP_NAME.vbs" -Value $vbsLauncherContent -Encoding ASCII

# Also keep BAT file for debugging purposes
$launcherContent = @"
@echo off
cd /d "%~dp0"
runtime\bin\javaw.exe -jar $APP_NAME.jar
"@
Set-Content -Path "$DIST_DIR\$APP_NAME.bat" -Value $launcherContent

# Create README
$readmeContent = @"
# $APP_NAME - نظام إدارة المخازن والمبيعات

## التشغيل
انقر مرتين على ملف: $APP_NAME.bat

## المتطلبات
لا يوجد! البرنامج يحتوي على كل ما يحتاجه للعمل.

## الملفات
- $APP_NAME.bat: ملف التشغيل
- $APP_NAME.jar: البرنامج الرئيسي
- runtime/: بيئة Java المخصصة
- hisabx.db: قاعدة البيانات

## الدعم الفني
للمساعدة والدعم، يرجى التواصل معنا.

الإصدار: $APP_VERSION
"@
Set-Content -Path "$DIST_DIR\README.txt" -Value $readmeContent -Encoding UTF8

# Summary
Write-Host "`n========================================" -ForegroundColor Green
Write-Host "Build Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host "`nDistribution package created at:" -ForegroundColor Cyan
Write-Host "$DIST_DIR" -ForegroundColor White
Write-Host "`nPackage size:" -ForegroundColor Cyan
$size = (Get-ChildItem -Path $DIST_DIR -Recurse | Measure-Object -Property Length -Sum).Sum / 1MB
Write-Host "$([math]::Round($size, 2)) MB" -ForegroundColor White
Write-Host "`nTo distribute:" -ForegroundColor Cyan
Write-Host "1. Compress the 'distribution' folder to ZIP" -ForegroundColor White
Write-Host "2. Send to customers" -ForegroundColor White
Write-Host "3. They extract and run $APP_NAME.bat" -ForegroundColor White
