@echo off
echo Creating Redis Utils JAR using Java tools...
cd /d "%~dp0"

REM Create build directories
if not exist "build\classes" mkdir build\classes
if not exist "build\libs" mkdir build\libs

REM Set paths
set SRC_DIR=src\main\groovy
set BUILD_DIR=build\classes
set JAR_FILE=build\libs\redis-utils-1.0.0.jar

echo Compiling Groovy files...

REM Find Groovy installation (check common locations)
set GROOVY_HOME=
if exist "C:\Program Files\Groovy" set GROOVY_HOME=C:\Program Files\Groovy
if exist "C:\groovy" set GROOVY_HOME=C:\groovy
if exist "%USERPROFILE%\.groovy" set GROOVY_HOME=%USERPROFILE%\.groovy

if "%GROOVY_HOME%"=="" (
    echo Groovy not found. Creating JAR with source files only...
    goto CREATE_SOURCE_JAR
)

REM Compile with Groovy if available
"%GROOVY_HOME%\bin\groovyc" -d "%BUILD_DIR%" "%SRC_DIR%\com\example\utils\RedisConnectionManager.groovy"

if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed. Creating source JAR instead...
    goto CREATE_SOURCE_JAR
)

REM Create compiled JAR
echo Creating compiled JAR...
jar -cf "%JAR_FILE%" -C "%BUILD_DIR%" .
goto SUCCESS

:CREATE_SOURCE_JAR
echo Creating source-only JAR...
REM Create JAR with source files
jar -cf "%JAR_FILE%" -C "%SRC_DIR%" .

:SUCCESS
if exist "%JAR_FILE%" (
    echo.
    echo JAR created successfully: %JAR_FILE%
    echo File size: 
    dir "%JAR_FILE%"
    echo.
    echo To use in NiFi Groovy scripts:
    echo 1. Copy %JAR_FILE% to a location accessible by NiFi
    echo 2. Add to classpath or use @Grab in your scripts
) else (
    echo Failed to create JAR file
    exit /b 1
)

pause