@echo off
rem ============================================================================
rem  Guess Market - web client (exercise 4)
rem  Starts the small Node.js host (static files + proxy to Tomcat) and opens
rem  the browser at http://localhost:3000
rem  Prerequisites: Node.js installed; Tomcat running with guessmarket.war
rem  deployed on http://localhost:8080/guessmarket (exactly as for exercise 3).
rem  No npm install is needed: the host uses only Node's built-in modules.
rem ============================================================================
cd /d "%~dp0"

where node >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Node.js was not found on the PATH. Please install Node.js and run again.
    pause
    exit /b 1
)

echo Starting the Guess Market web client host (a second window, keep it open)...
start "Guess Market web client host - http://localhost:3000" node server.js
timeout /t 2 /nobreak >nul
echo Opening http://localhost:3000 in the default browser...
start "" "http://localhost:3000"
echo(
echo If the browser did not open, go to:   http://localhost:3000
echo To stop the web client, close the "Guess Market web client host" window.
echo(
pause
