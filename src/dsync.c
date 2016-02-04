#include <stdio.h>
#include <Windows.h>
#include <strsafe.h>
#include <Shlwapi.h>

#define TIME_START_DATE 1900
#define TRUE 1
#define FALSE 0

int main(int argc, const char *argv[]) {
	const char *backupLocA = "F:\\workspace\\GoogleDrive\\dsync\\";
	const char *backupLocB = "F:\\workspace\\Dropbox\\Apps\\dsync\\";

	char dirToSync[MAX_PATH] = { 0 };
	if (argc == 1) {
		GetCurrentDirectory(MAX_PATH, dirToSync);
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
	for (int i = MAX_PATH - 1; programDir[i] != '\\'; i--)
		programDir[i] = 0;

	// Generate the absolute path for the output zip
	const char *outputDir = backupLocA;
	const char *archiveName = "Doyle";
	char *absOutputFormat = "%s%s_%s.7z";
	char absOutput[MAX_PATH] = { 0 };
	StringCchPrintf(absOutput, MAX_PATH, absOutputFormat, outputDir,
	                archiveName, timestamp);

	// Generate the command line string
	const char *zipExeName = "7za.exe";
	char *switches = "a -mx9";
	char *inputDir = "F:\\Doyle\\";
	char cmd[MAX_PATH] = { 0 };
	char *cmdFormat = "%s%s %s %s %s";
	StringCchPrintf(cmd, MAX_PATH, cmdFormat, programDir, zipExeName, switches,
	                absOutput, inputDir);

	// Execute the 7zip command
	STARTUPINFO startInfo = { 0 };
	PROCESS_INFORMATION procInfo = { 0 };
	if (CreateProcess(NULL, cmd, NULL, NULL, FALSE,
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
		if (!(CreateHardLink(altAbsOutput, absOutput, NULL))) {
			// TODO: Hardlink failed
		}
	} else {
		// TODO: CreateProcess failed
	}
	return TRUE;
}
