#include <stdio.h>
#include <Windows.h>
#include <strsafe.h>

#include <Shlwapi.h>  // PathFileExistsW
#include <Pathcch.h>  // PathCchRemoveFileSpec
#include <Shellapi.h> // Shell_NotifyIconW

#include "dsync.h"

#define DQN_IMPLEMENTATION
#include "dqn.h"

#define DSYNC_DEBUG true

/**
	TODO: Verbose mode option in cfg file
	TODO: Logging to file
	TODO: Compression values from cfg file
	TODO: Unit tests
*/

inline i32 str_trim_around(char *src, i32 srcLen, const char *charsToTrim,
                           const i32 charsToTrimSize)
{

	// TODO: Implement early exit? Check if first/last char has any of the chars
	// we want to trim, if not, then early exit?
	DQN_ASSERT(srcLen > 0 && charsToTrimSize > 0);

	// srcLen-1 as arrays start from 0, if we want to look at last char
	i32 index = srcLen-1;
	bool matched = false;
	i32 newLen = 0;

	// Starting from EOL if any chars match for trimming, remove and update
	// string length
	for (i32 i = index; i > 0; i--)
	{
		for (i32 j = 0; j < charsToTrimSize; j++)
		{
			if (src[i] == charsToTrim[j])
			{
				src[i]  = 0;
				matched = true;
				break;
			}
		}
		newLen = i + 1;
		if (!matched)
			break;
		else
			matched = false;
	}

	matched = false;
	// Count the number of leading characters to remove
	i32 numLeading = 0;
	for (i32 i = 0; i < newLen; i++)
	{
		for (i32 j = 0; j < charsToTrimSize; j++)
		{
			if (src[i] == charsToTrim[j])
			{
				numLeading++;
				matched = true;
				break;
			}
		}
		if (!matched)
			break;
		else
			matched = false;
	}

	if (numLeading > 0)
	{
		// Shift all chars back how many trash leading elements there are
		for (i32 i = 0; i < newLen; i++)
		{
			src[i] = src[i + numLeading];
		}
		newLen -= numLeading;
	}
	return newLen;
}

void dsync_unit_test()
{
	//// DEBUG TEST ////
	const char trim[] = {'"', ' '};
	char brokenString_1[] = "       F:\\test        ";
	char brokenString_2[] = " C:\\   \"   ";

	str_trim_around(brokenString_1, 22, trim, 2);
	DQN_ASSERT(dqn_strcmp(brokenString_1, "F:\\test") == 0);

	str_trim_around(brokenString_2, 11, trim, 2);
	DQN_ASSERT(dqn_strcmp(brokenString_2, "C:\\") == 0);
}

#define WIN32_TASKBAR_ICON_UID 0x282ACD13;
FILE_SCOPE LRESULT CALLBACK win32_main_callback(HWND window, UINT msg,
                                                WPARAM wParam, LPARAM lParam)
{
	LRESULT result = 0;
	switch (msg)
	{
		case WM_CREATE:
		{

			// Create Taskbar Icon
			NOTIFYICONDATAW notifyIconData  = {};
			notifyIconData.cbSize           = sizeof(notifyIconData);
			notifyIconData.hWnd             = window;
			notifyIconData.uID              = WIN32_TASKBAR_ICON_UID;
			notifyIconData.uFlags           = NIF_TIP;
			notifyIconData.hIcon            = LoadIcon(NULL, IDI_APPLICATION);
			swprintf_s(notifyIconData.szTip,
			           DQN_ARRAY_COUNT(notifyIconData.szTip), L"Dsync");
			DQN_ASSERT(Shell_NotifyIconW(NIM_ADD, &notifyIconData));
		}
		break;

		default:
		{
			result = DefWindowProcW(window, msg, wParam, lParam);
		}
		break;
	}

	return result;
}

// Return the copied len
FILE_SCOPE u32 win32_get_module_directory(wchar_t *directory, u32 len)
{
	u32 copiedLen = GetModuleFileNameW(NULL, directory, len);
	if (copiedLen == len)
	{
		DQN_WIN32_ERROR_BOX(
		    "GetModuleFileNameW() buffer maxed: Len of copied text is len "
		    "of supplied buffer.",
		    NULL);
		DQN_ASSERT(DQN_INVALID_CODE_PATH);
	}

	// NOTE: Should always work if GetModuleFileNameW works and we're running an
	// executable.
	if (PathCchRemoveFileSpec(directory, copiedLen) == S_FALSE)
	{
		dqn_win32_display_last_error("PathCchRemoveFileSpec() failed");
		DQN_ASSERT(DQN_INVALID_CODE_PATH);
	}

	return copiedLen;
}

typedef struct DsyncLocations
{
	wchar_t **backup;
	u32 numBackup;

	wchar_t **watch;
	u32 numWatch;
} DsyncLocations;

typedef struct DsyncState {
	DsyncLocations locations;

	f32 lastNotificationTimestamp;
} DsyncState;

FILE_SCOPE wchar_t **config_extract_ini_section(DqnIni *ini,
                                                DqnPushBuffer *pushBuffer,
                                                char *sectionName,
                                                u32 *numProperties)
{
	wchar_t **result = NULL;
	if (!ini || !pushBuffer || !numProperties) return result;

	i32 sectionIndex = dqn_ini_find_section(ini, sectionName, 0);
	if (sectionIndex != DQN_INI_NOT_FOUND)
	{
		*numProperties = dqn_ini_property_count(ini, sectionIndex);
		result         = (wchar_t **)dqn_push_buffer_allocate(
		    pushBuffer, (*numProperties) * sizeof(wchar_t *));

		i32 valueIndex = 0;
		for (u32 propertyIndex = 0; propertyIndex < *numProperties;
		     propertyIndex++)
		{
			const char *value =
			    dqn_ini_property_value(ini, sectionIndex, propertyIndex);
			wchar_t wideValue[1024] = {};
			dqn_win32_utf8_to_wchar(value, wideValue,
			                        DQN_ARRAY_COUNT(wideValue));

			// NOTE: We check unicode ver. if it exists since Windows does
			// not use UTF-8. We also waste space in our locations pointers
			// to string, if the location isn't valid, because we
			// pre-allocate the number of strings based on the count in the
			// ini file. But thats fine since it's just the size of
			// a pointer (negligible).
			if (PathFileExistsW(wideValue))
			{
				// NOTE: +1 since strlen doesn't include null terminator
				u32 valueLen = dqn_strlen(value) + 1;
				result[valueIndex] = (wchar_t *)dqn_push_buffer_allocate(
				    pushBuffer, valueLen * sizeof(wchar_t));

				swprintf_s(result[valueIndex++], valueLen, L"%s", wideValue);
			}
			else
			{
				// NOTE: Not a valid property, in our case, not a valid file
				// path
				(*numProperties)--;
			}
		}
	}

	return result;
}

FILE_SCOPE DsyncLocations dsync_config_load(DqnPushBuffer *pushBuffer)
{
	DsyncLocations locations = {};

	// Load CFG file
	wchar_t programPath[512] = {};
	u32 programPathLen = win32_get_module_directory(programPath, DQN_ARRAY_COUNT(programPath));

	const wchar_t *DSYNC_INI_FILE = L"dsync.ini";
	wchar_t configPath[512]    = {};
	u32 configPathLen          = 0;
	if (programPath[programPathLen - 1] == '\\')
	{
		configPathLen = swprintf_s(configPath, DQN_ARRAY_COUNT(configPath),
		                           L"%s%s", programPath, DSYNC_INI_FILE);
	}
	else
	{
		configPathLen = swprintf_s(configPath, DQN_ARRAY_COUNT(configPath),
		                           L"%s\\%s", programPath, DSYNC_INI_FILE);
	}

	if (configPathLen == DQN_ARRAY_COUNT(configPath))
	{
		DQN_WIN32_ERROR_BOX(
		    "dqn_sprintf() buffer maxed: Len of copied text is len "
		    "of supplied buffer. Dsync ini path is not well defined.",
		    NULL);
		DQN_ASSERT(DQN_INVALID_CODE_PATH);
	}

	DqnPushBuffer transientBuffer = {};
	dqn_push_buffer_init(&transientBuffer, DQN_KILOBYTE(512), 4);

	DqnFile configFile = {};
	if (!dqn_file_open_wide(configPath, &configFile, dqnfilepermissionflag_read,
	                        dqnfileaction_open_only))
	{
		// NOTE: File does not exist
	}
	else
	{
		u8 *configBuffer =
		    (u8 *)dqn_push_buffer_allocate(&transientBuffer, configFile.size);
		size_t bytesRead =
		    dqn_file_read(configFile, configBuffer, configFile.size);
		DQN_ASSERT(bytesRead == configFile.size);
		dqn_file_close(&configFile);

		DqnIni *ini = dqn_ini_load((char *)configBuffer, NULL);

		locations.backup = config_extract_ini_section(
		    ini, pushBuffer, "BackupToLocations", &locations.numBackup);
		locations.watch = config_extract_ini_section(
		    ini, pushBuffer, "WatchLocations", &locations.numWatch);
		dqn_ini_destroy(ini);
	}
	dqn_push_buffer_free(&transientBuffer);

	return locations;
};

#define WIN32_UNIQUE_TIMESTAMP_MAX_LEN 18
u32 win32_create_unique_timestamp(char *buf, i32 bufSize)
{
	SYSTEMTIME sysTime = {};
	GetLocalTime(&sysTime);

	u32 len = dqn_sprintf(buf, "%04d-%02d-%02d_%02d%02d%02d", sysTime.wYear,
	                      sysTime.wMonth, sysTime.wDay, sysTime.wHour,
	                      sysTime.wMinute, sysTime.wSecond);

	if (bufSize == len)
	{
		DQN_WIN32_ERROR_BOX(
		    "dqn_sprintf() buffer maxed: Len of copied text is len "
		    "of supplied buffer.",
		    NULL);
		DQN_ASSERT(DQN_INVALID_CODE_PATH);
	}

	return len;
}

void dsync_backup(wchar_t *path)
{
	////////////////////////////////////////////////////////////////////////////
	// Process supplied path
	////////////////////////////////////////////////////////////////////////////
	if (!PathFileExistsW(path))
	{
		DQN_WIN32_ERROR_BOX(
		    "PathFileExistsW() failed: Path does not point to valid file",
		    NULL);
		return;
	}

	// Generate the archive name based on the files given
	wchar_t fullPath[1024] = {};
	wchar_t *backupName    = NULL;
	u32 fullPathLen =
	    GetFullPathNameW(path, DQN_ARRAY_COUNT(fullPath), fullPath, NULL);
	if (fullPathLen == 0)
	{
		dqn_win32_display_last_error("GetFullPathNameW() failed");
		return;
	}

	// Remove backslash from end of string if exists
	// TODO: Not reliable cleaning of trailing backslashes
	bool backingUpDirectory = PathIsDirectoryW(fullPath);
	if (backingUpDirectory)
	{
		if (fullPath[fullPathLen - 1] == '\\')
		{
			fullPath[fullPathLen - 1] = 0;
			fullPathLen--;
		}
	}

	////////////////////////////////////////////////////////////////////////////
	// Generate Archive Name
	////////////////////////////////////////////////////////////////////////////
	// Generate archive prefix name
	if (backingUpDirectory)
	{
		// Get directory name for archive name by iterating backwards from end
		// of string to first occurence of '\'
		backupName = fullPath;
		for (i32 i = fullPathLen - 1; i >= 0; i--)
		{
			if (backupName[i] == '\\')
			{
				backupName = &backupName[i + 1];
				break;
			}
		}
	}
	else
	{
		backupName = PathFindFileNameW(fullPath);
		DQN_ASSERT(backupName != fullPath);
	}

	// Generate timestamp string
	char timestamp[WIN32_UNIQUE_TIMESTAMP_MAX_LEN] = {};
	win32_create_unique_timestamp(timestamp, DQN_ARRAY_COUNT(timestamp));
	wchar_t wideTimestamp[DQN_ARRAY_COUNT(timestamp)] = {};
	dqn_win32_utf8_to_wchar(timestamp, wideTimestamp,
	                        DQN_ARRAY_COUNT(wideTimestamp));


	wchar_t archiveName[1024] = {};
	swprintf_s(archiveName, DQN_ARRAY_COUNT(archiveName), L"%s_%s", backupName,
	           wideTimestamp);

	////////////////////////////////////////////////////////////////////////////
	// Zip Files to Archive
	////////////////////////////////////////////////////////////////////////////
	if (backingUpDirectory)
	{
		////////////////////////////////////////////////////////////////////////
		// Convert path into a search string for enumerating files
		////////////////////////////////////////////////////////////////////////
		const i32 MAX_UTF8_SIZE = DQN_ARRAY_COUNT(fullPath * 4);
		char fullPathUtf8[MAX_UTF8_SIZE] = {};
		if (!dqn_win32_wchar_to_utf8(fullPath, fullPathUtf8,
		                             DQN_ARRAY_COUNT(fullPathUtf8)))
		{
			DQN_ASSERT(DQN_INVALID_CODE_PATH)
		}

		char searchTerm[MAX_UTF8_SIZE] = {};
		if (dqn_sprintf(searchTerm, "%s\\*", fullPathUtf8) ==
		    DQN_ARRAY_COUNT(searchTerm))
		{
			DQN_WIN32_ERROR_BOX(
			    "dqn_sprintf() buffer maxed: Len of copied text is len "
			    "of supplied buffer.",
			    NULL);
			DQN_ASSERT(DQN_INVALID_CODE_PATH);
		}

		////////////////////////////////////////////////////////////////////////
		// Enumerate directory files
		////////////////////////////////////////////////////////////////////////
		u32 numFiles;
		char **fileList = dqn_dir_read(searchTerm, &numFiles);
		for (u32 i = 0; i < numFiles; i++)
		{
			wchar_t inputFile[MAX_UTF8_SIZE] = {};
			if (!dqn_win32_utf8_to_wchar(fileList[i], inputFile,
			                            DQN_ARRAY_COUNT(inputFile)))
			{
				DQN_ASSERT(DQN_INVALID_CODE_PATH)
			}

			// NOTE: Don't backup short-hand notated files for cwd and prev-cwd
			if (dqn_wstrcmp(inputFile, L"..") == 0 ||
			    dqn_wstrcmp(inputFile, L".") == 0)
			{
				continue;
			}

			wchar_t output[MAX_UTF8_SIZE] = {};
			i32 len = swprintf_s(output, DQN_ARRAY_COUNT(output), L"%s\\%s",
			                     fullPath, inputFile);

			if (len == DQN_ARRAY_COUNT(output))
			{
				DQN_WIN32_ERROR_BOX(
				    "dqn_sprintf() buffer maxed: Len of copied text is len "
				    "of supplied buffer.",
				    NULL);
				DQN_ASSERT(DQN_INVALID_CODE_PATH);
			}
		}
		dqn_dir_read_free(fileList, numFiles);
	}
	else
	{
		// Just need to backup single file
	}

#if 0
	char *archiveName = NULL;
	if (numFilesToBackup == 1) {
		// Set archive name to the file, note that file can be a absolute path
		// or relative or just the file name. Extract name if it is a path
		char *filename = *filesToBackup;
		size_t filenameSize = 0;
		bool fileIsPath = false;
		if (SUCCEEDED(StringCchLength(filename, MAX_PATH, &filenameSize))) {
			for (i32 i = 0; i < (i32)filenameSize; i++) {
				if (filename[i] == '\\') {
					fileIsPath = true;
					break;
				}
			}
		} else {
			// TODO: File name length failed
			return false;
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

	// Check if a backup location has been specified. If not then just store the
	// zip in current directory, else use the first backup path
	char *backupPath;
	if (state.numBackupPaths == 0) {
		backupPath = currDir;
	} else {
		backupPath = state.backupPaths[0];
	}

	StringCchPrintf(absOutput, MAX_PATH, absOutputFormat, backupPath,
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
		                filesToBackup[i]);
	}

	// Execute the 7zip command
	printf("%s\n", cmdArgs);
	
	STARTUPINFO startInfo = { 0 };
	PROCESS_INFORMATION procInfo = { 0 };
	if (CreateProcess(cmd, cmdArgs, NULL, NULL, false,
				0, NULL, NULL, &startInfo, &procInfo)) {
		WaitForSingleObject(procInfo.hProcess, INFINITE);
		CloseHandle(procInfo.hProcess);
		CloseHandle(procInfo.hThread);

		if (state.numBackupPaths > 1) {

			printf("\n\nDSYNC REDUNDANCY BACKUP\n");
			printf("Now copying backup to alternate locations ..\n");
			// TODO: Use hard-link where possible, otherwise copy file
			// NOTE: We use the first backup path in the initial backup
			// So start from the 2nd backup location and iterate
			for (i32 i = 1; i < state.numBackupPaths; i++) {
				char altAbsOutput[MAX_PATH] = { 0 };
				StringCchPrintf(altAbsOutput, MAX_PATH, absOutputFormat,
				                state.backupPaths[i], archiveName, timestamp);

				// TODO: Let user choose between hard-link and non-hardlink
				if (CreateHardLink(altAbsOutput, absOutput, NULL)) {
					printf("- Hard-linked file to: %s\n", altAbsOutput);
				} else {
					printf("- Hard-link failed, maybe destination is on a different drive, try copying ..\n");
					if (CopyFile(absOutput, altAbsOutput, true)) {
						printf("- Copied file to: %s\n", altAbsOutput);
					} else {
						// TODO: Format error message using getLastError(),
						// formatMessage()
						printf("- Failed file copy to: %s\n", altAbsOutput);
					}
				}
			}

		}
	} else {
		// TODO: CreateProcess failed
	}
#endif
}

int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance,
                   LPSTR lpCmdLine, int nShowCmd)
{
	dsync_unit_test();

	WNDCLASSEXW wc = {
	    sizeof(WNDCLASSEX),
	    0,
	    win32_main_callback,
	    0, // int cbClsExtra
	    0, // int cbWndExtra
	    hInstance,
	    LoadIcon(NULL, IDI_APPLICATION),
	    LoadCursor(NULL, IDC_ARROW),
	    NULL,
	    L"", // LPCTSTR lpszMenuName
	    L"DsyncWindowClass",
	    NULL, // HICON hIconSm
	};

	if (!RegisterClassExW(&wc))
	{
		DQN_WIN32_ERROR_BOX("RegisterClassExW() failed.", NULL);
		return -1;
	}

	HWND mainWindow =
	    CreateWindowExW(0, wc.lpszClassName, NULL, 0, CW_USEDEFAULT,
	                    CW_USEDEFAULT, 0, 0, NULL, NULL, hInstance, NULL);

	if (!mainWindow)
	{
		DQN_WIN32_ERROR_BOX("CreateWindowExW() failed.", NULL);
		return -1;
	}

	dsync_backup(L"F:\\fake");
	dsync_backup(L"C:\\git\\dsync\\src\\");

	DqnPushBuffer pushBuffer = {};
	dqn_push_buffer_init(&pushBuffer, DQN_KILOBYTE(512), 4);
	DsyncState state = {};
	state.locations  = dsync_config_load(&pushBuffer);

	if (state.locations.numBackup <= 0 || state.locations.numWatch <= 0)
	{
		DQN_WIN32_ERROR_BOX(
		    "dsync_config_load() returned empty: There are no backup locations "
		    "and/or watch locations.",
		    NULL);
		return 0;
	}
	else if (!state.locations.watch || !state.locations.backup)
	{
		DQN_WIN32_ERROR_BOX(
		    "dsync_config_load() returned empty: There are no strings defined "
		    "in the backup and/or watch locations",
		    NULL);
		return 0;
	}

	HANDLE *fileFindChangeArray = (HANDLE *)dqn_push_buffer_allocate(
	    &pushBuffer, state.locations.numWatch);
	for (u32 i = 0; i < state.locations.numWatch; i++)
	{
		const bool WATCH_ALL_SUBDIRECTORIES = true;
		const u32 FLAGS =
		    FILE_NOTIFY_CHANGE_DIR_NAME | FILE_NOTIFY_CHANGE_LAST_WRITE;
		fileFindChangeArray[i] = FindFirstChangeNotificationW(
		    state.locations.watch[i], WATCH_ALL_SUBDIRECTORIES, FLAGS);

		if (fileFindChangeArray[i] == INVALID_HANDLE_VALUE)
		{
			DQN_WIN32_ERROR_BOX("FindFirstChangeNotification() failed.", NULL);
			return -1;
		}
	}

	while (true)
	{
		MSG msg;
		while (PeekMessageW(&msg, mainWindow, 0, 0, PM_REMOVE))
		{
			TranslateMessage(&msg);
			DispatchMessageW(&msg);
		}

		const i32 NUM_HANDLES = state.locations.numWatch;
		DWORD result = WaitForMultipleObjects(
		    NUM_HANDLES, fileFindChangeArray, false, INFINITE);
		DQN_ASSERT(result != WAIT_TIMEOUT);
		if (result == WAIT_FAILED)
		{
			dqn_win32_display_last_error("WaitForMultipleObjects() failed");
			return 0;
		}

		i32 signalIndex = result - WAIT_OBJECT_0;
		DQN_ASSERT(signalIndex >= 0 && signalIndex < NUM_HANDLES);

		// Do work on indexed handle
		if (FindNextChangeNotification(fileFindChangeArray[signalIndex]) == 0)
		{
			dqn_win32_display_last_error("FindNextChangeNotification() failed");
			return 0;
		}

		f32 secondsElapsed =
		    (f32)dqn_time_now_in_s() - state.lastNotificationTimestamp;
		const f32 MIN_TIME_BETWEEN_NOTIFICATIONS_IN_S = 5.0f;
		if (secondsElapsed >= MIN_TIME_BETWEEN_NOTIFICATIONS_IN_S)
		{
			state.lastNotificationTimestamp = (f32)dqn_time_now_in_s();

			NOTIFYICONDATAW notifyIconData = {};
			notifyIconData.cbSize          = sizeof(notifyIconData);
			notifyIconData.hWnd            = mainWindow;
			notifyIconData.uID             = WIN32_TASKBAR_ICON_UID;
			notifyIconData.uFlags          = NIF_INFO | NIF_REALTIME;
			notifyIconData.hIcon           = LoadIcon(NULL, IDI_APPLICATION);
			notifyIconData.dwState         = NIS_SHAREDICON;
			swprintf_s(notifyIconData.szInfo,
			           DQN_ARRAY_COUNT(notifyIconData.szInfo),
			           L"Detected file change in \"%s\"",
			           state.locations.watch[signalIndex]);

			swprintf_s(notifyIconData.szInfoTitle,
			           DQN_ARRAY_COUNT(notifyIconData.szInfoTitle), L"Dsync");

			notifyIconData.dwInfoFlags =
			    NIIF_INFO | NIIF_NOSOUND | NIIF_RESPECT_QUIET_TIME;
			DQN_ASSERT(Shell_NotifyIconW(NIM_MODIFY, &notifyIconData));
		}
	}

	return 0;
}

