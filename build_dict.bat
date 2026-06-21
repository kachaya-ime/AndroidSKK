@echo off
setlocal
cd /d %~dp0

echo ========================================
echo  AndroidSKK Dictionary Build Tool
echo ========================================

rem 1. Run SudachiDictConverter via Gradle
echo [1/2] Converting Sudachi dictionary to SKK format...
call gradlew.bat :tool:runSudachiDictConverter
if %ERRORLEVEL% neq 0 (
    echo Error: SudachiDictConverter failed.
    pause
    exit /b %ERRORLEVEL%
)

rem 2. Run DictBuilder via Gradle
echo [2/2] Building final SKK binary dictionary...
call gradlew.bat :tool:runDictBuilder
if %ERRORLEVEL% neq 0 (
    echo Error: DictBuilder failed.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ========================================
echo  Success!
echo  Dictionary is built in app/src/main/res/raw/
echo ========================================
pause
