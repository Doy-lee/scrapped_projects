@echo off
REM Manual build from command line using Visual Studio's CL

REM Build tags file
ctags -R

REM Check if build tool is on path
REM >nul, 2>nul will remove the output text from the where command
where cl.exe >nul 2>nul
if %errorlevel%==1 (
	echo MSVC CL not on path, please add it to path to build by command line.
	goto end
)

IF NOT EXIST ..\bin mkdir ..\bin
pushd ..\bin

REM MD use dynamic runtime library
REM MT use static runtime library, so build and link it into exe
REM Od disables optimisations
REM W3 warning level 3
REM WX treat warnings as errors
REM Zi enables debug data, Z7 combines the debug files into one.

set compileFlags=/MT /Z7 /W3 /Od /WX

REM Create a set entry point to main, run as console app
set linkLibraries=user32.lib kernel32.lib gdi32.lib  shlwapi.lib shell32.lib Pathcch.lib
set linkerFlags=/SUBSYSTEM:WINDOWS,5.1

cl %compileFlags% ..\src\dsync.cpp /link %linkLibraries% %linkerFlags% /nologo /out:"dsync.exe"

popd

