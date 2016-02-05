#include <stdio.h>
#include <stdint.h>
#include <assert.h>
#include <Windows.h>
#include <strsafe.h>
#include <Shlwapi.h>

#define TIME_START_DATE 1900
#define TRUE 1
#define FALSE 0

#define DSYNC_DEBUG TRUE

#define Kilobytes(Value) ((Value)*1024LL)
#define Megabytes(Value) (Kilobytes(Value)*1024LL)
#define Gigabytes(Value) (Megabytes(Value)*1024LL)
#define Terabytes(Value) (Gigabytes(Value)*1024LL)

typedef int32_t i32;
typedef i32 b32;

char *getDirectoryName(char *absPath) {
	size_t absPathSize = 0;
	if (SUCCEEDED(StringCchLength(absPath, MAX_PATH, &absPathSize))) {
		for (i32 i = (i32)absPathSize; i > 0; i--) {
			if (absPath[i] == '\\') {
				assert((i + 1) < (i32)absPathSize);
				return &absPath[i+1];
				break;
			}
		}
	}

	return NULL;
}

i32 main(i32 argc, const char *argv[]) {

#if DSYNC_DEBUG
	const char *backupLocA = "F:\\";
#else
	const char *backupLocA = "F:\\workspace\\GoogleDrive\\dsync\\";
#endif
	const char *backupLocB = "F:\\workspace\\Dropbox\\Apps\\dsync\\";

	// Load CFG file
	char programDir[MAX_PATH] = { 0 };
	GetModuleFileName(NULL, programDir, MAX_PATH);
	// NOTE: Remove the exe from the path to just get the folder path
	for (i32 i = MAX_PATH - 1; programDir[i] != '\\'; i--)
		programDir[i] = 0;
	
	const char *cfgName = "dsync.cfg";
	char cfgPath[MAX_PATH] = { 0 };
	char *cfgPathFormat = "%s%s";
	StringCchPrintf(cfgPath, MAX_PATH, cfgPathFormat, programDir, cfgName);

	// Attempt to get a handle for the config file
	HANDLE cfgFile = { 0 };
	cfgFile = CreateFile(cfgPath,
	                     GENERIC_READ | GENERIC_WRITE,
	                     FILE_SHARE_READ,
	                     NULL,
	                     OPEN_ALWAYS,
	                     FILE_ATTRIBUTE_NORMAL,
	                     NULL);

	if(GetLastError() == ERROR_ALREADY_EXISTS) {
		// Parse existing cfg file
		DWORD cfgSize = GetFileSize(cfgFile, NULL);
		DWORD bytesRead = 0;
		// TODO: Temporary assert if file size is absurdly big (most likely user
		// error)
		assert(cfgSize <= Megabytes(1));

		char *cfgBuffer = { 0 };
		cfgBuffer = (char *) malloc(sizeof(char) * cfgSize);
		ReadFile(cfgFile, cfgBuffer, cfgSize, &bytesRead, NULL);
		assert(cfgSize == bytesRead);

		// TODO: File is in memory, start parsing

		free(cfgBuffer);
	} else {
		// Create one
		i32 cfgSize = 8; // TODO: Determine from array?
		const char *defaultCfg[] = {"# DSYNC Config File",
		                            "# Lines prefixed with # are ignored",
		                            "# ",
		                            "# SAMPLE CONFIG",
		                            "# [BACKUP_LOCATION_000]=C:\\",
		                            "# [7Z_COMPRESSION]=mx9",
		                            "",
		                            "[7Z_COMPRESSION]=mx9"};

		const char crlf[2] = {'\r', '\n'};

		// Write default cfg template to file
		for (i32 i = 0; i < cfgSize; i++) {
			size_t numBytesToWrite = 0;
			DWORD bytesWritten = 0;
			// Determine number of bytes to write by checking string length
			if (SUCCEEDED(StringCchLength(defaultCfg[i], STRSAFE_MAX_CCH,
			                              &numBytesToWrite))) {
				// TODO: What to do in fail case?
				WriteFile(cfgFile, defaultCfg[i], numBytesToWrite,
				          &bytesWritten, NULL);

				assert(numBytesToWrite == bytesWritten);

				// If not the last line in the file, write CRLF bytes
				if ((i + 1) < cfgSize)
					for (i32 j = 0; j < 2; j++)
						WriteFile(cfgFile, &crlf[j], 1, NULL, NULL);
			}
		}
	}

	CloseHandle(cfgFile);

	// Assume all args after module name are files to backup
	char **filesToSync = { 0 };
	i32 numFilesToBackup = 1;
	char currDir[MAX_PATH] = { 0 };
	GetCurrentDirectory(MAX_PATH, currDir);

	// However if no args supplied after module then backup current directory
	if (argc == 1) {
		// TODO: We don't free this memory, but do we need to? The program is so
		// short that we can arguably leave GC to the OS
		filesToSync = (char **)malloc(sizeof(char*));
		filesToSync[0] = (char *)malloc(sizeof(char) * MAX_PATH);
		*filesToSync = currDir;
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

	// Generate the archive name based on the files given
	char *archiveName = NULL;
	if (numFilesToBackup == 1) {
		// Set archive name to the file, note that file can be a absolute path
		// or relative or just the file name. Extract name if it is a path
		char *filename = *filesToSync;
		size_t filenameSize = 0;
		b32 fileIsPath = FALSE;
		if (SUCCEEDED(StringCchLength(filename, MAX_PATH, &filenameSize))) {
			for (i32 i = 0; i < (i32)filenameSize; i++) {
				if (filename[i] == '\\') {
					fileIsPath = TRUE;
					break;
				}
			}
		} else {
			return FALSE;
		}

		if (fileIsPath) {
			archiveName = getDirectoryName(currDir);
		} else {
			archiveName = filename;
		}
	} else {
		// If there is more than 1 file, then we want to use the current
		// directory name that the file is in
		archiveName = getDirectoryName(currDir);
	}

	// Generate the absolute path for the output zip
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
	// Max switch is 32768 - MAX_PATH as derived from WINAPI
	char cmdArgs[MAX_SWITCH_LENGTH] = { 0 };
	char *cmdArgsFormat = "%s %s %s";
	StringCchPrintf(cmdArgs, MAX_SWITCH_LENGTH, cmdArgsFormat, "7za", switches,
	                absOutput);

	// Append input files to command line
	char *inputFileFormat = "%s \"%s\"";
	for (i32 i = 0; i < numFilesToBackup; i++) {
		StringCchPrintf(cmdArgs, MAX_PATH, inputFileFormat, cmdArgs,
		                filesToSync[i]);
	}

	// Execute the 7zip command
	printf("%s\n", cmdArgs);
	
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
