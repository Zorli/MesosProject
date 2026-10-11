@echo off
setlocal

REM Windows counterpart of create-jar.sh: builds the same deliverable JARs using mvnw.cmd.
REM Resolve the project root (two levels up from this script) and move there.
cd /d "%~dp0..\.." || exit /b 1

if not exist "deliverables\jar" mkdir "deliverables\jar"
del /q "deliverables\jar\*.jar" >nul 2>&1

REM -Djavafx.platform is passed explicitly so the correct natives are bundled even when
REM cross-building (e.g. producing the Windows/Linux jars on a Mac). A CLI user property
REM takes precedence over the host auto-detection profiles and is honoured by transitive
REM OpenJFX/controlsfx native resolution, unlike a property set only inside a profile.
call mvnw.cmd -DskipTests clean package -Pclient-jar,client-linux-x64   -Djavafx.platform=linux       || goto :error
call mvnw.cmd -DskipTests clean package -Pclient-jar,client-mac-x64     -Djavafx.platform=mac         || goto :error
call mvnw.cmd -DskipTests clean package -Pclient-jar,client-mac-aarch64 -Djavafx.platform=mac-aarch64 || goto :error
call mvnw.cmd -DskipTests clean package -Pclient-jar,client-windows-x64 -Djavafx.platform=win         || goto :error
call mvnw.cmd -DskipTests clean package -Pserver-jar,server-release                                   || goto :error

dir "deliverables\jar"
exit /b 0

:error
set "rc=%errorlevel%"
echo.
echo create-jar failed with exit code %rc%.
exit /b %rc%
