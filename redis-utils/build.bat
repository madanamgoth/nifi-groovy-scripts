@echo off
echo Building Redis Utils JAR...
cd /d "%~dp0"

if not exist gradlew.bat (
    echo Gradle wrapper not found. Installing...
    gradle wrapper --gradle-version 8.4
)

echo Running Gradle build...
gradlew.bat clean build fatJar

if %ERRORLEVEL% EQU 0 (
    echo.
    echo Build successful!
    echo JAR files created in build\libs\:
    dir build\libs\*.jar
    echo.
    echo To use in NiFi:
    echo 1. Copy redis-utils-1.0.0-all.jar to NiFi's lib directory
    echo 2. Restart NiFi
    echo 3. Use @Grab^('file://path/to/redis-utils-1.0.0-all.jar'^) in your scripts
) else (
    echo Build failed with error code %ERRORLEVEL%
)

pause