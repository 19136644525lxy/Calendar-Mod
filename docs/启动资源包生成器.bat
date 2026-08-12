@echo off
setlocal enabledelayedexpansion
title Calendar Mod Resource Pack Generator

rem ============================================================
rem Calendar Mod Resource Pack Generator Launcher
rem ============================================================
rem   Checks:
rem     1. Python interpreter available in PATH
rem     2. PySide6 dependency installed (auto install if missing)
rem     3. Launches the GUI
rem ============================================================

echo.
echo ============================================================
echo   Calendar Mod Resource Pack Generator
echo ============================================================
echo.

rem ---------- Step 1: cd to script dir ----------
cd /d "%~dp0"
set SCRIPT_DIR=%CD%
set PY_SCRIPT=%SCRIPT_DIR%\resource_pack_generator.py
set REQ_FILE=%SCRIPT_DIR%\requirements.txt

if not exist "%PY_SCRIPT%" (
    echo [ERROR] Main script not found:
    echo         %PY_SCRIPT%
    echo.
    echo Make sure this .bat is in the same folder as resource_pack_generator.py
    goto :ERROR_PAUSE
)

rem ---------- Step 2: Find Python interpreter ----------
echo [1/3] Checking Python environment...
set PY_EXE=
where python >nul 2>nul
if %ERRORLEVEL%==0 (
    set PY_EXE=python
    goto :PY_CHECKED
)
where python3 >nul 2>nul
if %ERRORLEVEL%==0 (
    set PY_EXE=python3
    goto :PY_CHECKED
)
where py >nul 2>nul
if %ERRORLEVEL%==0 (
    set PY_EXE=py
    goto :PY_CHECKED
)

:PY_NOT_FOUND
echo [ERROR] Python environment not detected.
echo.
echo Please do one of the following:
echo   A. Install Python 3.10+ from https://www.python.org/downloads/
echo      IMPORTANT: Check "Add Python to PATH" during installation.
echo.
echo   B. Set Python path manually at the top of this bat file:
echo      set PY_EXE=C:\Python311\python.exe
echo.
goto :ERROR_PAUSE

:PY_CHECKED
echo        OK - Using %PY_EXE%

rem Show Python version
for /f "usebackq tokens=2 delims= " %%a in (`%PY_EXE% --version 2^>^&1`) do set PY_VER=%%a
echo        Version: %PY_VER%

rem Syntax quick check
%PY_EXE% -c "import ast,sys; ast.parse(open(sys.argv[1], encoding='utf-8').read())" "%PY_SCRIPT%" >nul 2>nul
if not %ERRORLEVEL%==0 (
    echo [ERROR] Main script syntax check failed.
    echo Please verify the file is intact.
    goto :ERROR_PAUSE
)

rem ---------- Step 3: Check PySide6 ----------
echo.
echo [2/3] Checking PySide6 dependency...
%PY_EXE% -c "from PySide6.QtCore import Qt" >nul 2>nul
if %ERRORLEVEL%==0 (
    echo        OK - PySide6 installed
    goto :DEP_CHECKED
)

echo [INFO] PySide6 not installed. Attempting auto-install...
echo This may take a few minutes (first-time download ~100MB+).
echo.
%PY_EXE% -m pip install -r "%REQ_FILE%"
if %ERRORLEVEL%==0 (
    echo.
    echo        OK - Installation successful
    goto :DEP_CHECKED
)

echo.
echo [ERROR] Auto-install failed.
echo Please run these commands manually:
echo.
echo   cd /d "%SCRIPT_DIR%"
echo   pip install -r requirements.txt
echo.
goto :ERROR_PAUSE

:DEP_CHECKED

rem ---------- Step 4: Launch GUI ----------
echo.
echo [3/3] Launching Resource Pack Generator...
echo.

%PY_EXE% "%PY_SCRIPT%"
set EXIT_CODE=%ERRORLEVEL%

if %EXIT_CODE%==0 (
    echo.
    echo [DONE] Program exited normally.
    goto :NORMAL_EXIT
)

echo.
echo [WARN] Exit code: %EXIT_CODE%
echo If this was a crash, check the Python stack trace above.
goto :ERROR_PAUSE

rem ---------- Exit helpers ----------
:ERROR_PAUSE
echo.
pause
exit /b 1

:NORMAL_EXIT
timeout /t 2 >nul
exit /b 0
