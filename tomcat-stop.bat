@echo off
setlocal enabledelayedexpansion
rem  Guess Market - EX3 : stops the local development Tomcat started by tomcat-start.bat
set "HERE=%~dp0"
if not defined CATALINA_HOME set "CATALINA_HOME=%HERE%tomcat"
if not defined JAVA_HOME (
    for /f "tokens=1,* delims==" %%a in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr /C:"java.home"') do set "JAVA_HOME=%%b"
    if defined JAVA_HOME set "JAVA_HOME=!JAVA_HOME:~1!"
)
call "%CATALINA_HOME%\bin\shutdown.bat"
endlocal
