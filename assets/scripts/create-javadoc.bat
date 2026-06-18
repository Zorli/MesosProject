@echo off
setlocal

REM Windows counterpart of create-javadoc.sh: regenerates the HTML Javadocs.
REM Resolve the project root (two levels up from this script) and move there.
cd /d "%~dp0..\.." || exit /b 1

if exist "deliverables\javadoc" rmdir /s /q "deliverables\javadoc"
call mvnw.cmd -DskipTests javadoc:javadoc || goto :error

echo Javadocs generated in deliverables\javadoc\apidocs
exit /b 0

:error
set "rc=%errorlevel%"
echo.
echo create-javadoc failed with exit code %rc%.
exit /b %rc%
