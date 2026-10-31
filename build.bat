@echo off
setlocal enabledelayedexpansion

rem Usage:
rem   build.bat              build plugin and mod (with tests)
rem   build.bat plugin       plugin only
rem   build.bat mod          mod only
rem   build.bat deploy       build, then copy the jars to the test server/clients
rem   build.bat skiptests    build without running tests
rem plugin / mod can be combined with deploy / skiptests.
rem This file is ASCII only on purpose (cmd breaks on UTF-8 in batch files).

rem ---- edit for your environment ----
set "JAVA_HOME=C:\Users\4jskk\.jdks\corretto-17.0.16"
rem Folder names with Japanese characters are matched with ? (one ? per character).
rem server folder "(3 chars)1" -> ???   /   client folders "(2 chars)-test-radiomod" -> ??-test-radiomod
rem (client game folders are under the launcher: ...\.numapotelauncher\instances\<name>\mods)
set "SERVER_PATTERN=F:\minecraftserver\servers\???"
set "CLIENT1_PATTERN=C:\Users\4jskk\AppData\Roaming\.numapotelauncher\instances\??-test-radiomod"
set "CLIENT2_PATTERN=C:\Users\4jskk\AppData\Roaming\.numapotelauncher\instances\??-test-radiomod-2"
set "CLIENT3_PATTERN=C:\Users\4jskk\AppData\Roaming\.numapotelauncher\instances\numahub-1.20.1"
rem Set MVN to the full path of mvn.cmd to override the automatic search.
set "MVN="
rem -----------------------------------

set "ROOT=%~dp0"
set "DO_PLUGIN=0"
set "DO_MOD=0"
set "DO_DEPLOY=0"
set "SKIP_TESTS=0"
for %%A in (%*) do (
    if /i "%%A"=="plugin" set "DO_PLUGIN=1"
    if /i "%%A"=="mod" set "DO_MOD=1"
    if /i "%%A"=="deploy" set "DO_DEPLOY=1"
    if /i "%%A"=="skiptests" set "SKIP_TESTS=1"
)
if "%DO_PLUGIN%%DO_MOD%"=="00" (
    set "DO_PLUGIN=1"
    set "DO_MOD=1"
)

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] JAVA_HOME not found: %JAVA_HOME%
    goto :fail
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

if "%DO_PLUGIN%"=="1" (
    if not defined MVN (
        for /f "delims=" %%M in ('where mvn.cmd 2^>nul') do if not defined MVN set "MVN=%%M"
    )
    if not defined MVN (
        for /d %%D in ("C:\Program Files\JetBrains\*") do (
            if exist "%%D\plugins\maven\lib\maven3\bin\mvn.cmd" set "MVN=%%D\plugins\maven\lib\maven3\bin\mvn.cmd"
        )
    )
    if not defined MVN (
        echo [ERROR] Maven not found. Set MVN at the top of this file to the path of mvn.cmd.
        goto :fail
    )
    echo Maven: !MVN!
)

if "%DO_PLUGIN%"=="1" (
    echo.
    echo ===== building plugin =====
    pushd "%ROOT%plugin"
    if "%SKIP_TESTS%"=="1" (
        call "!MVN!" -B clean package -DskipTests
    ) else (
        call "!MVN!" -B clean package
    )
    set "RC=!errorlevel!"
    popd
    if not "!RC!"=="0" (
        echo [ERROR] plugin build failed.
        goto :fail
    )
)

if "%DO_MOD%"=="1" (
    echo.
    echo ===== building mod =====
    pushd "%ROOT%mod"
    if "%SKIP_TESTS%"=="1" (
        call "%ROOT%mod\gradlew.bat" build -x test --console=plain
    ) else (
        call "%ROOT%mod\gradlew.bat" build --console=plain
    )
    set "RC=!errorlevel!"
    popd
    if not "!RC!"=="0" (
        echo [ERROR] mod build failed.
        goto :fail
    )
)

echo.
echo ===== built jars =====
if "%DO_PLUGIN%"=="1" dir /b "%ROOT%plugin\target\radioplugin-*.jar"
if "%DO_MOD%"=="1" dir /b "%ROOT%mod\build\libs\radiomod-*.jar"

if "%DO_DEPLOY%"=="1" (
    echo.
    echo ===== deploy =====
    if "%DO_PLUGIN%"=="1" call :deploy "%ROOT%plugin\target\radioplugin-0.1.0.jar" "%SERVER_PATTERN%" plugins
    if "%DO_MOD%"=="1" call :deploy "%ROOT%mod\build\libs\radiomod-0.1.0.jar" "%CLIENT1_PATTERN%" mods
    if "%DO_MOD%"=="1" call :deploy "%ROOT%mod\build\libs\radiomod-0.1.0.jar" "%CLIENT2_PATTERN%" mods
    if "%DO_MOD%"=="1" call :deploy "%ROOT%mod\build\libs\radiomod-0.1.0.jar" "%CLIENT3_PATTERN%" mods
)

echo.
echo Done.
endlocal
exit /b 0

:deploy
rem %1 = jar, %2 = folder pattern, %3 = sub folder (plugins or mods)
if not exist "%~1" (
    echo [WARN] jar not found: %~1
    exit /b 0
)
set "FOUND=0"
for /d %%S in (%2) do (
    if exist "%%S\%~3\" (
        copy /y "%~1" "%%S\%~3\" >nul
        echo copied %~nx1 to %%S\%~3
        set "FOUND=1"
    )
)
if "!FOUND!"=="0" echo [WARN] no target folder matches %~2\%~3
exit /b 0

:fail
echo.
echo Build aborted.
endlocal
exit /b 1
