@echo off
setlocal enabledelayedexpansion
rem ============================================================================
rem  Guess Market - EX3  :  acceptance tests
rem    run-tests.bat            engine tests (no server needed) + HTTP smoke + edge + robust tests
rem    run-tests.bat ui         ... plus the automated two-client JavaFX UI test
rem  Run build.bat first. HTTP/UI tests expect the WAR deployed on
rem  http://localhost:8080/guessmarket (see tomcat-start.bat).
rem ============================================================================

set "HERE=%~dp0"
cd /d "%HERE%"
set "OUT=%HERE%out"
set "LIB=%HERE%lib"
set "SDK=%HERE%javafx-sdk-25\lib"
set "CLIENT=%HERE%dist\client"
set "FAILED=0"

if not exist "%OUT%\engine.jar" (
    echo [ERROR] out\engine.jar not found. Run build.bat first.
    exit /b 1
)

echo(
echo ===== Engine tests =====
if exist "%OUT%\test-engine" rmdir /s /q "%OUT%\test-engine"
mkdir "%OUT%\test-engine"
javac -d "%OUT%\test-engine" -cp "%OUT%\dto.jar;%OUT%\engine.jar;%LIB%\*" "%HERE%_test\engine\enginetest\EngineTest.java"
if errorlevel 1 (echo [ERROR] engine test compilation failed & exit /b 1)
java -cp "%OUT%\test-engine;%OUT%\dto.jar;%OUT%\engine.jar;%LIB%\*" enginetest.EngineTest "%HERE%samples"
if errorlevel 1 set "FAILED=1"

echo(
echo ===== HTTP tests (server must be running) =====
if exist "%OUT%\test-http" rmdir /s /q "%OUT%\test-http"
mkdir "%OUT%\test-http"
javac -d "%OUT%\test-http" "%HERE%_test\http\httptest\HttpSmokeTest.java" "%HERE%_test\http\httptest\HttpEdgeTest.java" "%HERE%_test\http\httptest\HttpRobustTest.java"
if errorlevel 1 (echo [ERROR] http test compilation failed & exit /b 1)
java -cp "%OUT%\test-http" httptest.HttpSmokeTest "%HERE%samples"
if errorlevel 1 set "FAILED=1"

echo(
echo ===== HTTP edge tests (validation, market math, blocked user, concurrency, sessions) =====
java -cp "%OUT%\test-http" httptest.HttpEdgeTest
if errorlevel 1 set "FAILED=1"

echo(
echo ===== HTTP robust tests (web host + proxy, hostile URLs, verbs and parameters, schema errors, ledger) =====
echo NOTE: starts two temporary Node hosts on ports 3105 and 3106 (needs node on the PATH).
java -cp "%OUT%\test-http" httptest.HttpRobustTest http://localhost:8080/guessmarket "%HERE%web-client"
if errorlevel 1 set "FAILED=1"

if /i "%~1"=="ui" (
    echo(
    echo ===== UI test: two JavaFX clients drive the real client off-screen =====
    if exist "%OUT%\test-ui" rmdir /s /q "%OUT%\test-ui"
    mkdir "%OUT%\test-ui\shots\guy" "%OUT%\test-ui\shots\tomer"
    set "CP=%CLIENT%\client.jar;%CLIENT%\dto.jar;%CLIENT%\lib\*"
    javac -d "%OUT%\test-ui" --module-path "%SDK%" --add-modules javafx.controls -cp "!CP!" "%HERE%_test\ui\uitest\UiDriver.java"
    if errorlevel 1 (echo [ERROR] ui test compilation failed & exit /b 1)
    echo NOTE: the UI test needs a freshly deployed server ^(no events yet^).
    start "ui-test-guy" /b java --module-path "%SDK%" --add-modules javafx.controls --enable-native-access=javafx.graphics -cp "%OUT%\test-ui;!CP!" uitest.UiDriver Guy mm "%OUT%\test-ui\shots\guy" "%HERE%samples"
    timeout /t 6 /nobreak >nul
    java --module-path "%SDK%" --add-modules javafx.controls --enable-native-access=javafx.graphics -cp "%OUT%\test-ui;!CP!" uitest.UiDriver Tomer trader "%OUT%\test-ui\shots\tomer" "%HERE%samples"
    if errorlevel 1 set "FAILED=1"
    echo Screenshots: %OUT%\test-ui\shots
)

echo(
if "%FAILED%"=="1" (
    echo ===== SOME TESTS FAILED =====
    exit /b 1
)
echo ===== ALL TESTS PASSED =====
exit /b 0
