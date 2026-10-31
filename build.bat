@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

rem ============================================================
rem  radiomod / radioplugin のビルド用バッチ
rem
rem    build.bat            plugin と mod を両方ビルドする(テストも実行)
rem    build.bat plugin     plugin だけ
rem    build.bat mod        mod だけ
rem    build.bat deploy     両方ビルドして、下の配置先へ jar をコピーする
rem    build.bat skiptests  テストを飛ばして両方ビルドする
rem  (plugin / mod と deploy / skiptests は組み合わせ可: build.bat mod deploy)
rem ============================================================

rem ---- 環境に合わせてここを直す ----
set "JAVA_HOME=C:\Users\4jskk\.jdks\corretto-17.0.16"
set "SERVER_PLUGINS=F:\minecraftserver\servers\単発1\plugins"
set "CLIENT1_MODS=F:\minecraftserver\instances\自作-test-radiomod\mods"
set "CLIENT2_MODS=F:\minecraftserver\instances\自作-test-radiomod-2\mods"
rem Maven は、PATH にあればそれを使い、なければ IntelliJ 同梱のものを探す
set "MVN="
rem ----------------------------------

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
    echo [エラー] JAVA_HOME が見つかりません: %JAVA_HOME%
    goto :fail
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

if "%DO_PLUGIN%"=="1" (
    if not defined MVN (
        where mvn >nul 2>nul && set "MVN=mvn"
    )
    if not defined MVN (
        for /d %%D in ("C:\Program Files\JetBrains\*") do (
            if exist "%%D\plugins\maven\lib\maven3\bin\mvn.cmd" set "MVN=%%D\plugins\maven\lib\maven3\bin\mvn.cmd"
        )
    )
    if not defined MVN (
        echo [エラー] Maven が見つかりません。このファイルの MVN に mvn.cmd のパスを書いてください。
        goto :fail
    )
)

if "%DO_PLUGIN%"=="1" (
    echo.
    echo ===== plugin をビルド =====
    pushd "%ROOT%plugin"
    if "%SKIP_TESTS%"=="1" (
        call "%MVN%" -B clean package -DskipTests
    ) else (
        call "%MVN%" -B clean package
    )
    set "RC=!errorlevel!"
    popd
    if not "!RC!"=="0" (
        echo [エラー] plugin のビルドに失敗しました。
        goto :fail
    )
)

if "%DO_MOD%"=="1" (
    echo.
    echo ===== mod をビルド =====
    pushd "%ROOT%mod"
    if "%SKIP_TESTS%"=="1" (
        call gradlew.bat build -x test --console=plain
    ) else (
        call gradlew.bat build --console=plain
    )
    set "RC=!errorlevel!"
    popd
    if not "!RC!"=="0" (
        echo [エラー] mod のビルドに失敗しました。
        goto :fail
    )
)

echo.
echo ===== できた jar =====
if "%DO_PLUGIN%"=="1" dir /b "%ROOT%plugin\target\radioplugin-*.jar"
if "%DO_MOD%"=="1" dir /b "%ROOT%mod\build\libs\radiomod-*.jar"

if "%DO_DEPLOY%"=="1" (
    echo.
    echo ===== 配置 =====
    if "%DO_PLUGIN%"=="1" (
        call :copyjar "%ROOT%plugin\target\radioplugin-0.1.0.jar" "%SERVER_PLUGINS%"
    )
    if "%DO_MOD%"=="1" (
        call :copyjar "%ROOT%mod\build\libs\radiomod-0.1.0.jar" "%CLIENT1_MODS%"
        call :copyjar "%ROOT%mod\build\libs\radiomod-0.1.0.jar" "%CLIENT2_MODS%"
    )
)

echo.
echo 完了しました。
endlocal
exit /b 0

:copyjar
if not exist "%~1" (
    echo [警告] jar が見つかりません: %~1
    exit /b 0
)
if not exist "%~2\" (
    echo [警告] 配置先がありません: %~2
    exit /b 0
)
copy /y "%~1" "%~2\" >nul && echo コピー: %~nx1 -^> %~2
exit /b 0

:fail
echo.
echo ビルドを中止しました。
endlocal
exit /b 1
