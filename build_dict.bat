@echo off
setlocal
cd /d %~dp0

echo ========================================
echo  AndroidSKK Dictionary Build Tool
echo ========================================

rem Clean work directory
if exist work (
    echo Cleaning work directory...
    rmdir /s /q work
)

rem Run SudachiDictConverter via Gradle (Converts Sudachi dictionary and builds SKK binary dictionary)
echo Converting Sudachi dictionary and building SKK binary dictionary...
call gradlew.bat :tool:runSudachiDictConverter
if %ERRORLEVEL% neq 0 (
    echo Error: SudachiDictConverter failed.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ========================================
echo  Success!
  echo  Dictionary is built in app/src/main/assets/
echo ========================================
pause
