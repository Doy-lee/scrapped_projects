@echo off
REM Build process for text editor spell checker prototype

IF NOT EXIST ..\build mkdir ..\build
pushd ..\build

set SDL2Folder=F:\workspace\libraries\SDL2-2.0.4
set SDL2ImageFolder=F:\workspace\libraries\SDL2_image-2.0.0

REM Zi - debug information
REM MDd - debug mode
set compileFlags=/MDd /Zi /W3

REM Include the header files to use function prototypes
set includeFlags=/I %SDL2Folder%\include /I %SDL2ImageFolder%\include

REM Link SDL libraries to use functions in code
set externalLibs=%SDL2Folder%\lib\x64\SDL2.lib %SDL2Folder%\lib\x64\SDL2main.lib %SDL2ImageFolder%\lib\x64\SDL2_image.lib
REM Set entry point to main, don't use MSVC's C runtime library as we'll use SDLs.
set linkerFlags=/DEBUG /ENTRY:mainCRTStartup /SUBSYSTEM:CONSOLE /NODEFAULTLIB:msvcrt.lib
set additionalLibraryPath=/LIBPATH:%SDL2Folder%\lib\x64 /LIBPATH:%SDL2ImageFolder%\lib\x64

cl %compileFlags% /Fotxt.o /c ..\src\txt.c %includeFlags%
cl %compileFlags% /Fotxt_math.o /c ..\src\txt_math.c /nologo
link %linkerFlags% /nologo /out:text_editor.exe txt.o txt_math.o %externalLibs%

popd
