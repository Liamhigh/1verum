@echo off
cd /d "%~dp0"
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot
echo JAVA_HOME=%JAVA_HOME%
echo Building APK...
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo BUILD FAILED
    pause
    exit /b 1
)
echo BUILD SUCCESSFUL
echo Running tests...
call gradlew.bat testDebugUnitTest
if errorlevel 1 (
    echo TESTS FAILED
    pause
    exit /b 1
)
echo TESTS SUCCESSFUL
pause
