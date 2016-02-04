
:: Dsync is a simple backup script that archives and date stamps a directory (my git dir)

@echo off
set dirTo7zip="C:\Program Files\7-Zip\7z.exe"

:: Forward declare currFolderName to be the first argument passed to the script
:: "" if nothing is passed
set currFolderName=%~1

if "%~1"=="" (
	:: No argument passed to script, backup current directory
	:: Retrieve current directory name to use as name for backup
	for %%a in (.) do set currFolderName=%%~na
	set dirToArchive=%cd%
) else (
	for %%a in (%~1) do set currFolderName=%%~na
	set dirToArchive=%currFolderName%

	:: Dodgy check for valid directory because of forward declare issues
	dir %currFolderName%
	if errorlevel 1 GOTO earlyExit
)

for /f "tokens=2-8 delims=.:/ " %%a in ("%date% %time%") do set localDateTime=%%c-%%a-%%b_%%d%%e%%f

echo Setting backup variables
:: Set to archive mode (a) and maximum compression, ultra mode (mx9)
set switches=a -mx9
set archiveName=%currFolderName%_%localDateTime%

echo Setting target destination to store backup
set backupLocA=F:\workspace\GoogleDrive\dsync\%archiveName%.7z
set backupLocB=F:\workspace\Dropbox\Apps\dsync\%archiveName%.7z

if not exist %dirToArchive% (
	echo Could not access/find directory to archive:
	echo %dirToArchive%
) else (
	if not exist %dirTo7zip% (
		echo 7zip exe/directory could not be found, exiting:
		echo %dirTo7zip%
	) else (
		echo Starting backup ...
		%dirTo7zip% %switches% %archiveName% %dirToArchive%
		echo Backup complete
		echo Moving archive to Google Drive
		mv %archiveName%.7z %backupLocA%
		if errorlevel 1 GOTO earlyExit

		echo Hard-linking archive to Dropbox, double redundancy
		mklink /h %backupLocB% %backupLocA%
		if errorlevel 1 GOTO earlyExit

		echo Dsync complete, files created:
		echo %backupLocA%
		echo %backupLocB%
	)
	exit /b
)

:earlyExit
echo Detected error, exiting prematurely
