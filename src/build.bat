@echo off
REM Manual build from command line using Visual Studio's CL

IF NOT EXIST ..\debug mkdir ..\debug
pushd ..\debug

REM Zi 		- debug information
REM MDd 	- debug mode
REM W3 		- Warning Level 3
set compileFlags=/MDd /Zi /W3

REM Create a PDB debug file, set entry point to main, run as console app
set linkerFlags=/DEBUG /ENTRY:mainCRTStartup /SUBSYSTEM:CONSOLE

cl %compileFlags% /Fodsync.obj /c ..\src\dsync.c
link %linkerFlags% /nologo /out:dsync.exe dsync.obj

popd

