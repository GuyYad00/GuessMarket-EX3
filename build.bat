@echo off
setlocal enabledelayedexpansion
rem ============================================================================
rem  Guess Market - EX3  :  build script
rem  Builds the four modules and packages them:
rem    dist\guessmarket.war        the server (engine + servlets + all deps)
rem    dist\client\                the JavaFX client folder (jars + run.bat)
rem  Works from any directory (paths are anchored to this script's folder).
rem  Requires a JDK 25 on PATH (java / javac). jar.exe is located via java.home.
rem ============================================================================

set "HERE=%~dp0"
cd /d "%HERE%"

echo(
echo === Locating the JDK ===
set "JHOME="
for /f "tokens=1,* delims==" %%a in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr /C:"java.home"') do (
    set "JHOME=%%b"
)
if defined JHOME set "JHOME=!JHOME:~1!"
if not defined JHOME (
    echo [ERROR] Could not determine java.home. Is a JDK 25 on your PATH?
    exit /b 1
)
set "JAR=!JHOME!\bin\jar.exe"
echo java.home = !JHOME!

set "SDK=%HERE%javafx-sdk-25\lib"
set "LIB=%HERE%lib"
set "OUT=%HERE%out"
set "DIST=%HERE%dist"

echo(
echo === Cleaning previous build ===
if exist "%OUT%" rmdir /s /q "%OUT%" 2>nul
if exist "%DIST%" rmdir /s /q "%DIST%" 2>nul
mkdir "%OUT%\dto" "%OUT%\engine" "%OUT%\server" "%OUT%\client" "%OUT%\war\WEB-INF\classes" "%OUT%\war\WEB-INF\lib" 2>nul
mkdir "%DIST%\client\lib" 2>nul

rem ---------------------------------------------------------------- dto ----
echo(
echo === Compiling dto ===
call :sources "%HERE%dto\src" "%OUT%\dto_sources.txt"
javac -d "%OUT%\dto" "@%OUT%\dto_sources.txt"
if errorlevel 1 (echo [ERROR] dto compilation failed & exit /b 1)
"%JAR%" --create --file "%OUT%\dto.jar" -C "%OUT%\dto" .
if errorlevel 1 (echo [ERROR] dto.jar packaging failed & exit /b 1)

rem ------------------------------------------------------------- engine ----
echo(
echo === Compiling engine ===
call :sources "%HERE%engine\src" "%OUT%\engine_sources.txt"
javac -d "%OUT%\engine" -cp "%OUT%\dto.jar;%LIB%\*" "@%OUT%\engine_sources.txt"
if errorlevel 1 (echo [ERROR] engine compilation failed & exit /b 1)
"%JAR%" --create --file "%OUT%\engine.jar" -C "%OUT%\engine" .
if errorlevel 1 (echo [ERROR] engine.jar packaging failed & exit /b 1)

rem ------------------------------------------------------------- server ----
echo(
echo === Compiling server (servlets) ===
call :sources "%HERE%server\src" "%OUT%\server_sources.txt"
javac -d "%OUT%\war\WEB-INF\classes" -cp "%OUT%\dto.jar;%OUT%\engine.jar;%LIB%\*;%HERE%lib-compile-only\*" "@%OUT%\server_sources.txt"
if errorlevel 1 (echo [ERROR] server compilation failed & exit /b 1)

echo === Assembling guessmarket.war ===
xcopy /e /y /q "%HERE%server\web\*" "%OUT%\war\" >nul
copy /y "%OUT%\dto.jar"    "%OUT%\war\WEB-INF\lib\" >nul
copy /y "%OUT%\engine.jar" "%OUT%\war\WEB-INF\lib\" >nul
copy /y "%LIB%\gson-2.11.0.jar"           "%OUT%\war\WEB-INF\lib\" >nul
copy /y "%LIB%\jakarta.xml.bind-api.jar"  "%OUT%\war\WEB-INF\lib\" >nul
copy /y "%LIB%\jaxb-core.jar"             "%OUT%\war\WEB-INF\lib\" >nul
copy /y "%LIB%\jaxb-impl.jar"             "%OUT%\war\WEB-INF\lib\" >nul
copy /y "%LIB%\jakarta.activation-api.jar" "%OUT%\war\WEB-INF\lib\" >nul
copy /y "%LIB%\angus-activation.jar"      "%OUT%\war\WEB-INF\lib\" >nul
"%JAR%" --create --file "%DIST%\guessmarket.war" -C "%OUT%\war" .
if errorlevel 1 (echo [ERROR] WAR packaging failed & exit /b 1)

rem ------------------------------------------------------------- client ----
echo(
echo === Compiling JavaFX client ===
call :sources "%HERE%client\src" "%OUT%\client_sources.txt"
javac -d "%OUT%\client" -cp "%OUT%\dto.jar;%LIB%\*" --module-path "%SDK%" --add-modules javafx.controls "@%OUT%\client_sources.txt"
if errorlevel 1 (echo [ERROR] client compilation failed & exit /b 1)
if not exist "%OUT%\client\client\app\" mkdir "%OUT%\client\client\app"
xcopy /y /q "%HERE%client\src\client\app\*.css" "%OUT%\client\client\app\" >nul

echo === Packaging client folder ===
"%JAR%" --create --file "%DIST%\client\client.jar" --manifest "%HERE%client\MANIFEST.MF" -C "%OUT%\client" .
if errorlevel 1 (echo [ERROR] client.jar packaging failed & exit /b 1)
copy /y "%OUT%\dto.jar" "%DIST%\client\dto.jar" >nul
copy /y "%LIB%\gson-2.11.0.jar"              "%DIST%\client\lib\" >nul
copy /y "%LIB%\okhttp-4.9.1.jar"             "%DIST%\client\lib\" >nul
copy /y "%LIB%\okio-2.8.0.jar"               "%DIST%\client\lib\" >nul
copy /y "%LIB%\kotlin-stdlib-1.4.10.jar"     "%DIST%\client\lib\" >nul
copy /y "%LIB%\kotlin-stdlib-common-1.4.10.jar" "%DIST%\client\lib\" >nul
copy /y "%LIB%\annotations-13.0.jar"         "%DIST%\client\lib\" >nul
copy /y "%HERE%client\run.bat" "%DIST%\client\run.bat" >nul
xcopy /e /i /y /q "%HERE%javafx-sdk-25" "%DIST%\client\javafx-sdk-25" >nul

echo(
echo === Build complete ===
echo   dist\guessmarket.war          (copy into tomcat\webapps)
echo   dist\client\run.bat           (starts the JavaFX client)
endlocal
exit /b 0

rem ---- helper: write a quoted javac argfile with forward-slash paths ----
:sources
if exist "%~2" del "%~2"
for /r "%~1" %%f in (*.java) do (
    set "p=%%f"
    echo "!p:\=/!">>"%~2"
)
exit /b 0
