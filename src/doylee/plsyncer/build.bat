@echo off
IF NOT EXIST ..\..\..\build\doylee\plsyncer mkdir ..\..\..\build\doylee\plsyncer
javac *.java
move *.class ..\..\..\build\doylee\plsyncer\
