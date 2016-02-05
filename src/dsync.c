#include <stdio.h>
#include <stdint.h>
#include <Windows.h>
#include <strsafe.h>
#include <Shlwapi.h>

#define TIME_START_DATE 1900
#define TRUE 1
#define FALSE 0

#define DSYNC_DEBUG TRUE

typedef int32_t i32;

i32 main(i32 argc, const char *argv[]) {

#if DSYNC_DEBUG
	const char *backupLocA = "F:\\";
#else
	const char *backupLocA = "F:\\workspace\\GoogleDrive\\dsync\\";
#endif
	const char *backupLocB = "F:\\workspace\\Dropbox\\Apps\\dsync\\";

	// Assume all args after module name are files to backup
	char **filesToSync = { 0 };
	i32 numFilesToBackup = 1;

	// However if no args supplied after module then backup current directory
	if (argc == 1) {
		filesToSync = (char **)malloc(sizeof(char*));
		filesToSync[0] = (char *)malloc(sizeof(char) * MAX_PATH);
		GetCurrentDirectory(MAX_PATH, *filesToSync);
	} else {
		filesToSync = &argv[1];
		numFilesToBackup = argc - 1;
	}

	// Generate timestamp string
	SYSTEMTIME sysTime = { 0 };
	GetLocalTime(&sysTime);
	char timestamp[MAX_PATH] = { 0 };
	char *timestampFormat = "%d-%02d-%02d_%02d%02d%02d";
	StringCchPrintf(timestamp, MAX_PATH, timestampFormat, sysTime.wYear,
	                sysTime.wMonth, sysTime.wDay, sysTime.wHour,
	                sysTime.wMinute, sysTime.wSecond);


	char programDir[MAX_PATH] = { 0 };
	GetModuleFileName(NULL, programDir, MAX_PATH);
	// NOTE: Remove the exe from the path to just get the folder path
	for (i32 i = MAX_PATH - 1; programDir[i] != '\\'; i--)
		programDir[i] = 0;

	// Generate the absolute path for the output zip
	const char *archiveName = "Doyle";
	char *absOutputFormat = "%s%s_%s.7z";
	char absOutput[MAX_PATH] = { 0 };
	StringCchPrintf(absOutput, MAX_PATH, absOutputFormat, backupLocA,
	                archiveName, timestamp);

	// Generate the command line string
	const char *zipExeName = "7za.exe";
	char *switches = "a -mx9";
	char cmd[MAX_PATH] = { 0 };
	char *cmdFormat = "%s%s ";
	StringCchPrintf(cmd, MAX_PATH, cmdFormat, programDir, zipExeName);

#define MAX_SWITCH_LENGTH 32508
	// Max switch is 32768 - MAX_PATH as defined in WINAPI
	char cmdArgs[MAX_SWITCH_LENGTH] = { 0 };
	char *cmdArgsFormat = "%s %s %s ";
	StringCchPrintf(cmdArgs, MAX_SWITCH_LENGTH, cmdArgsFormat, "7za", switches, absOutput);
	                

	// Append input files to command line
	for (i32 i = 0; i < numFilesToBackup; i++) {
		if (SUCCEEDED(StringCchCat(cmdArgs, MAX_PATH, filesToSync[i]))) {
			// If appended last file to string, don't add whitespace (trailing)
			if (i + 1 < numFilesToBackup) {
				if (SUCCEEDED(StringCchCat(cmdArgs, MAX_PATH, " "))) {
				} else {
					return FALSE;
				}
			}
		} else {
			return FALSE;
		}
	}

	// Execute the 7zip command
	
	STARTUPINFO startInfo = { 0 };
	PROCESS_INFORMATION procInfo = { 0 };
	if (CreateProcess(cmd, cmdArgs, NULL, NULL, FALSE,
				0, NULL, NULL, &startInfo, &procInfo)) {
		WaitForSingleObject(procInfo.hProcess, INFINITE);
		CloseHandle(procInfo.hProcess);
		CloseHandle(procInfo.hThread);

		// After completing backup, hard-link to secondary path
		// TODO: Hard link doesn't work if alternate backup is on different
		// drive
		char altAbsOutput[MAX_PATH] = { 0 };
		StringCchPrintf(altAbsOutput, MAX_PATH, absOutputFormat, backupLocB,
		                archiveName, timestamp);
#if DSYNC_DEBUG
#else
		if (!(CreateHardLink(altAbsOutput, absOutput, NULL))) {
			// TODO: Hardlink failed
		}
#endif
	} else {
		// TODO: CreateProcess failed
	}

	return TRUE;
}
