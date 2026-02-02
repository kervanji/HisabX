@echo off
cd /d "%~dp0"

set APP_JAR=%~dp0HisabX.jar

runtime\bin\java.exe ^
 --module-path "%~dp0runtime\lib" ^
 --add-modules javafx.controls,javafx.fxml,javafx.swing ^
 -cp "%APP_JAR%" ^
 com.hisabx.MainApp

pause
