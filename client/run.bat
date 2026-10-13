@echo off
setlocal
rem ============================================================================
rem  Guess Market - EX3  :  JavaFX client launcher
rem  Starts the client against the server at http://localhost:8080/guessmarket
rem  (the guessmarket.war deployed in Tomcat). Works from any directory: every
rem  path is anchored to this script's own folder, and the JavaFX runtime is
rem  taken from the bundled javafx-sdk-25, so only a Java 25 runtime on PATH is
rem  needed. You may start several clients (one window per user).
rem ============================================================================

set "HERE=%~dp0"

if not exist "%HERE%client.jar" (
    echo [ERROR] client.jar not found next to this script.
    exit /b 1
)

java --module-path "%HERE%javafx-sdk-25\lib" ^
     --add-modules javafx.controls ^
     --enable-native-access=javafx.graphics ^
     -cp "%HERE%client.jar;%HERE%dto.jar;%HERE%lib\*" ^
     client.Main %*

endlocal
