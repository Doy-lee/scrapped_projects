#include <stdio.h>
#include <Windows.h>
#include <strsafe.h>
#include <Shlwapi.h>

#include "dsync.h"

#define DSYNC_DEBUG TRUE

inline i32 trimAroundStr(char *src, i32 srcLen, const char charsToTrim[],
                         const i32 charsToTrimSize) {

	// TODO: Implement early exit? Check if first/last char has any of the chars
	// we want to trim, if not, then early exit?
	assert(srcLen > 0 && charsToTrimSize > 0);

	// srcLen-1 as arrays start from 0, if we want to look at last char
	i32 index = srcLen-1;
	b32 matched = FALSE;
	i32 newLen = 0;

	// Starting from EOL if any chars match for trimming, remove and update
	// string length
	for (i32 i = index; i > 0; i--) {
		for (i32 j = 0; j < charsToTrimSize; j++) {
			if (src[i] == charsToTrim[j]) {
				src[i] = 0;
				matched = TRUE;
				break;
			}
		}
		newLen = i + 1;
		if (!matched) break;
		else matched = FALSE;
	}

	matched = FALSE;
	// Count the number of leading characters to remove
	i32 numLeading = 0;
	for (i32 i = 0; i < newLen; i++) {
		for (i32 j = 0; j < charsToTrimSize; j++) {
			if (src[i] == charsToTrim[j]) {
				numLeading++;
				matched = TRUE;
				break;
			}
		}
		if (!matched) break;
		else matched = FALSE;
	}

	if (numLeading > 0) {
		// Shift all chars back how many trash leading elements there are
		for (i32 i = 0; i < newLen; i++) {
			src[i] = src[i + numLeading];
		}
		newLen -= numLeading;
	}
	return newLen;
}

CFGToken *parseCFGFile(char *cfgBuffer, i32 cfgSize, i32 *numTokens) {
	const char *optionStrings[NUM_TYPES] = { 0 };
	optionStrings[(enum CFGTypes)COMPRESSION] = "7Z_COMPRESSION";
	optionStrings[(enum CFGTypes)BACKUP_LOC] = "BACKUP_LOCATION";

	i32 initialNumOfTokens = 10;
	i32 optionIndex = 0;
	CFGToken *options = (CFGToken *)
	                    calloc(initialNumOfTokens, sizeof(CFGToken));

	char *cfgLine = NULL;
	b32 ignoreLine = FALSE;
	for (i32 i = 0; i < (i32)cfgSize; i++) {
		if (ignoreLine) {
			// Check for CRLF which resets ignoreLine flag
			if (cfgBuffer[i] == '\n') {
				if (cfgBuffer[i-1] == '\r') {
					// CRLF found
					ignoreLine = FALSE;
				}
			}
		} else {
			// Check if # found, if so, start ignoring line
			if (cfgBuffer[i] == '#') {
				ignoreLine = TRUE;
			} else if (cfgBuffer[i] == '[') {
				// Extract config token
				char tokenString[MAX_TOKEN_LEN] = { 0 };
				i32 strIndex = 0;
				b32 syntaxValidToken = FALSE;

				// Copy token string to memory
				while (cfgBuffer[++i] != ']' && i < (i32)cfgSize) {
					tokenString[strIndex++] = cfgBuffer[i];
				}
				tokenString[strIndex] = '\0';

				if (cfgBuffer[++i] == '=' &&
				    cfgBuffer[i-1] == ']' &&
				    i < (i32)cfgSize) {
					syntaxValidToken = TRUE;
				}

				char tokenValue[MAX_SWITCH_LENGTH] = { 0 };
				i32 valIndex = 0;
				// Extract config value
				if (syntaxValidToken) {
					while (cfgBuffer[++i] != '\n' && i < (i32)cfgSize) {
						tokenValue[valIndex++] = cfgBuffer[i];
					}

					// NOTE: CRLF is 2 bytes. We check for the LF part
					// first. Then check that the previosu character is CR.
					// This means we copy over the CR whilst extracting the
					// token value. This method prevents array out of bounds
					// checking if we check for CR first then LF.
					if (cfgBuffer[i] == '\n') {
						tokenValue[--valIndex] = '\0';
					}

					// Store token into memory
					// TODO: We force CFGTypes to start from 1, is having an
					// invalid option a good idea?
					for(enum CFGTypes i = 1; i < (enum CFGTypes)NUM_TYPES;
					    i++) {
						if (strcmp(tokenString, optionStrings[i]) == 0) {

							options[optionIndex].option = i;

							// NOTE: valIndex as a side effect tracks the length
							// of the token
							i32 tokenLen = valIndex;
							const char charsToTrim[] = {'"', ' '};
							tokenLen = trimAroundStr(tokenValue, tokenLen,
							                         charsToTrim, 2);

							options[optionIndex].valueLen = tokenLen;
							options[optionIndex].value =
							                 (char *) calloc(tokenLen, sizeof(char));
							memcpy_s(options[optionIndex].value, tokenLen,
							         tokenValue, tokenLen);

							optionIndex++;
							// TODO: Realloc memory for more tokens
							assert(optionIndex <= initialNumOfTokens);
							break;
						}
					}


				}
			}
		}

	}

	// NOTE: optionIndex as a side effect tracks the number of parsed options
	*numTokens = optionIndex;
	return options;
}

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

	//// DEBUG TEST ////
	const char trim[] = {'"', ' '};
	char brokenString_1[] = "       F:\\test        ";
	char brokenString_2[] = " C:\\   \"   ";

	trimAroundStr(brokenString_1, 22, trim, 2);
	assert(strcmp(brokenString_1, "F:\\test") == 0);

	trimAroundStr(brokenString_2, 11, trim, 2);
	assert(strcmp(brokenString_2, "C:\\") == 0);
#else
	const char *backupLocA = "F:\\workspace\\GoogleDrive\\dsync\\";
#endif
	const char *backupLocB = "F:\\workspace\\Dropbox\\Apps\\dsync\\";

	ProgramState state = { 0 };

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

	if (GetLastError() == ERROR_ALREADY_EXISTS) {
		// Parse existing cfg file
		DWORD cfgSize = GetFileSize(cfgFile, NULL);
		DWORD bytesRead = 0;
		// TODO: Temporary assert if file size is absurdly big (most likely user
		// error)
		assert(cfgSize <= Megabytes(1));

		char *cfgBuffer = { 0 };
		cfgBuffer = (char *) calloc(cfgSize, sizeof(char));
		ReadFile(cfgFile, cfgBuffer, cfgSize, &bytesRead, NULL);
		assert(cfgSize == bytesRead);

		CFGToken *cfgOptions = { 0 };
		i32 numOptions = 0;
		cfgOptions = parseCFGFile(cfgBuffer, cfgSize, &numOptions);

		//TODO: Store backup locations
		state.backupLocations = malloc(sizeof(char*) * numOptions);
		
		for (i32 i = 0; i < numOptions; i++) {
			if (cfgOptions->option = (enum CFGTypes)BACKUP_LOC) {
			}
		}

		free(cfgOptions);
		free(cfgBuffer);
	} else {
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
				WriteFile(cfgFile, defaultCfg[i], (i32)numBytesToWrite,
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
