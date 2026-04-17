@REM Maven Wrapper script for Windows
@echo off
setlocal
if "%JAVA_HOME%"=="" (
  for /f "tokens=*" %%i in ('where java 2^>nul') do set JAVA_EXE=%%i
  for %%i in ("%JAVA_EXE%") do set JAVA_HOME=%%~dpi..
  set JAVA_HOME=%JAVA_HOME:~0,-2%
)
set MAVEN_PROJECTBASEDIR=%~dp0
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%
set WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
set WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain
"%JAVA_HOME%\bin\java.exe" -classpath %WRAPPER_JAR% "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" %WRAPPER_LAUNCHER% %*
if ERRORLEVEL 1 goto error
goto end
:error
exit /b 1
:end
endlocal
