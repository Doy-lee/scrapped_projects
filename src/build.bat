@echo off
IF NOT EXIST ..\build mkdir ..\build
pushd ..\build
cl -nologo -Zi -W3 ..\src\PlaylistSyncer.cpp /link Shlwapi.lib setargv.obj
popd
