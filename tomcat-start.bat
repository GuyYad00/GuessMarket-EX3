@echo off
setlocal enabledelayedexpansion
rem ============================================================================
rem  Guess Market - EX3  :  local Tomcat helper (development only)
rem  Deploys dist\guessmarket.war into a Tomcat and starts it in a new window.
rem  Uses the Tomcat in .\tomcat unless CATALINA_HOME is already set.
rem  The grader does not need this script: they copy the WAR into their own
rem  tomcat\webapps folder.
rem ============================================================================
set "HERE=%~dp0"
if not defined CATALINA_HOME set "CATALINA_HOME=%HERE%tomcat"
if not exist "%CATALINA_HOME%\bin\catalina.bat" (
    echo [ERROR] No Tomcat found at "%CATALINA_HOME%". Set CATALINA_HOME or unpack Tomcat into .\tomcat
    exit /b 1
)
if not exist "%HERE%dist\guessmarket.war" (
    echo [ERROR] dist\guessmarket.war not found. Run build.bat first.
    exit /b 1
)

if not defined JAVA_HOME (
    for /f "tokens=1,* delims==" %%a in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr /C:"java.home"') do set "JAVA_HOME=%%b"
    if defined JAVA_HOME set "JAVA_HOME=!JAVA_HOME:~1!"
)

echo Removing any previous deployment...
if exist "%CATALINA_HOME%\webapps\guessmarket" rmdir /s /q "%CATALINA_HOME%\webapps\guessmarket"
if exist "%CATALINA_HOME%\webapps\guessmarket.war" del /q "%CATALINA_HOME%\webapps\guessmarket.war"
copy /y "%HERE%dist\guessmarket.war" "%CATALINA_HOME%\webapps\" >nul
echo Deployed guessmarket.war. Starting Tomcat (JAVA_HOME=%JAVA_HOME%)...
call "%CATALINA_HOME%\bin\startup.bat"
echo Server URL: http://localhost:8080/guessmarket/
endlocal
