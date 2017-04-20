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

typedef struct WatchPath
{
	wchar_t *path;
	f32 timeLastDetectedChange;
	u32 numChanges;
} WatchPath;

typedef struct DsyncLocations
{
	wchar_t **backup;
	u32 numBackup;

	WatchPath *watch;
	u32 numWatch;
} DsyncLocations;

typedef struct DsyncState {
	DsyncLocations locations;
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

		wchar_t **watchLocations = config_extract_ini_section(
		    ini, pushBuffer, "WatchLocations", &locations.numWatch);

		locations.watch = (WatchPath *)dqn_push_buffer_allocate(
		    pushBuffer, locations.numWatch * sizeof(WatchPath));
		for (u32 i = 0; i < locations.numWatch; i++)
		{
			locations.watch[i].path = watchLocations[i];
		}

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

FILE_SCOPE u32 win32_make_path_to_directory(const wchar_t *const in,
                                            wchar_t *out, u32 outLen)
{
	if (!in || !out) return 0;

	u32 copiedLen = 0;
	i32 inLen     = dqn_wstrlen(in);
	if (in[inLen - 1] != '\\')
		copiedLen = swprintf_s(out, outLen, L"%s\\", in);
	else
		copiedLen = swprintf_s(out, outLen, L"%s", in);

	return copiedLen;
}

FILE_SCOPE u32 dsync_make_archive_output_path(wchar_t *outputDirectory,
                                              wchar_t *archiveName,
                                              wchar_t *result, u32 resultLen)
{
	if (!outputDirectory || !archiveName || !result || !resultLen) return 0;
	if (resultLen == 0) return 0;

	// Make sure path has ending '\' to make it a directory
	u32 usedLen =
	    win32_make_path_to_directory(outputDirectory, result, resultLen);

	// Append the archive name to the target directory to form the full path
	usedLen +=
	    swprintf_s(&result[usedLen], (resultLen - usedLen), L"%s", archiveName);

	// If len is equal to the size of the given buffer then we're out of space
	if (usedLen >= resultLen)
	{
		DQN_WIN32_ERROR_BOX(
		    "swprintf_s() buffer maxed: Len of copied text is len "
		    "of supplied buffer.",
		    NULL);
		DQN_ASSERT(DQN_INVALID_CODE_PATH);
	}

	return usedLen;
}

void dsync_backup(wchar_t *path, wchar_t **const backupLocations,
                  u32 numBackupLocations)
{
	if (numBackupLocations == 0 || !backupLocations || !path) return;
	for (u32 i = 0; i < numBackupLocations; i++)
	{
		if (!backupLocations[i]) return;
	}

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
	u32 fullPathLen =
	    GetFullPathNameW(path, DQN_ARRAY_COUNT(fullPath), fullPath, NULL);
	if (fullPathLen == 0)
	{
		dqn_win32_display_last_error("GetFullPathNameW() failed");
		return;
	}

	// Remove backslash from end of string if it's a directory
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
	wchar_t *backupName = NULL;
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

	wchar_t archiveName[1024] = {};
	{
		// Generate timestamp string
		char timestamp[WIN32_UNIQUE_TIMESTAMP_MAX_LEN] = {};
		win32_create_unique_timestamp(timestamp, DQN_ARRAY_COUNT(timestamp));
		wchar_t wideTimestamp[DQN_ARRAY_COUNT(timestamp)] = {};
		dqn_win32_utf8_to_wchar(timestamp, wideTimestamp,
		                        DQN_ARRAY_COUNT(wideTimestamp));

		swprintf_s(archiveName, DQN_ARRAY_COUNT(archiveName), L"%s_%s.7z",
		           backupName, wideTimestamp);
	}

	////////////////////////////////////////////////////////////////////////////
	// Enum files to Archive and add to command line
	////////////////////////////////////////////////////////////////////////////
	// Get the first output directory, the first zip is stored there. Then
	// copy that file to the other locations
	wchar_t firstOutputFile[DQN_ARRAY_COUNT(fullPath)] = {};
	u32 firstOutputFileLen = dsync_make_archive_output_path(
	    backupLocations[0], archiveName, firstOutputFile,
	    DQN_ARRAY_COUNT(firstOutputFile));

	DQN_ASSERT(firstOutputFileLen != 0);

	// Generate the command line string for 7z
	const wchar_t *const ZIP_EXE_NAME = L"7za";
	const wchar_t *const CMD_SWITCHES = L"a -mx9";
	wchar_t cmd[2048]                 = {0};

	wchar_t *cmdPtr = cmd;
	cmdPtr += swprintf_s(cmdPtr, DQN_ARRAY_COUNT(cmd), L"%s %s %s",
	                     ZIP_EXE_NAME, CMD_SWITCHES, firstOutputFile);

	if (backingUpDirectory)
	{
		////////////////////////////////////////////////////////////////////////
		// Convert path into a search string for enumerating files
		////////////////////////////////////////////////////////////////////////
		const i32 MAX_UTF8_SIZE          = DQN_ARRAY_COUNT(fullPath) * 4;
		char fullPathUtf8[MAX_UTF8_SIZE] = {};
		if (!dqn_win32_wchar_to_utf8(fullPath, fullPathUtf8,
		                             DQN_ARRAY_COUNT(fullPathUtf8)))
		{
			DQN_ASSERT(DQN_INVALID_CODE_PATH);
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
			wchar_t inputFile[DQN_ARRAY_COUNT(fullPath)] = {};
			if (!dqn_win32_utf8_to_wchar(fileList[i], inputFile,
			                            DQN_ARRAY_COUNT(inputFile)))
			{
				DQN_ASSERT(DQN_INVALID_CODE_PATH);
			}

			// NOTE: Don't backup short-hand notated files for cwd and prev-cwd
			if (dqn_wstrcmp(inputFile, L"..") == 0 ||
			    dqn_wstrcmp(inputFile, L".") == 0)
			{
				continue;
			}

			wchar_t *extension = PathFindExtensionW(inputFile) + 1;
			if (extension)
			{
				// Skip vim swap files
				if (dqn_wstrcmp(extension, L"swp") == 0)
				{
					continue;
				}
			}

			i32 bufLen = DQN_ARRAY_COUNT(cmd) - (cmdPtr - cmd);
			cmdPtr += swprintf_s(cmdPtr, bufLen, L" \"%s\\%s\"", fullPath, inputFile);
			DQN_ASSERT((cmdPtr - cmd) < DQN_ARRAY_COUNT(cmd));
		}
		dqn_dir_read_free(fileList, numFiles);
	}
	else
	{
		// Just need to backup single file
		cmdPtr += swprintf_s(cmdPtr, DQN_ARRAY_COUNT(cmd), L" \"%s\"", fullPath);
		DQN_ASSERT((cmdPtr - cmd) < DQN_ARRAY_COUNT(cmd));
	}

	STARTUPINFOW startInfo        = {0};
	PROCESS_INFORMATION procInfo = {0};
	if (CreateProcessW(NULL, cmd, NULL, NULL, false, 0, NULL, NULL,
	                  &startInfo, &procInfo))
	{
		DWORD exitCode = 0;
		WaitForSingleObject(procInfo.hProcess, INFINITE);
		GetExitCodeProcess(procInfo.hProcess, &exitCode);

		CloseHandle(procInfo.hProcess);
		CloseHandle(procInfo.hThread);

		if (exitCode == 0)
		{
			// No error
		}
		else if (exitCode == 1)
		{
			DQN_WIN32_ERROR_BOX("Zip failed: Warning (non-fatal errors).",
			                    NULL);
		}
		else
		{
			if (exitCode == 2)
			{
				DQN_WIN32_ERROR_BOX("Zip failed: Fatal error.", NULL);
			}
			else if (exitCode == 7)
			{
				DQN_WIN32_ERROR_BOX("Zip failed: Command line error.", NULL);
			}
			else if (exitCode == 8)
			{
				DQN_WIN32_ERROR_BOX(
				    "Zip failed: Not enough memory for operation.", NULL);
			}
			else if (exitCode == 255)
			{
				DQN_WIN32_ERROR_BOX(
				    "Zip failed: User stopped the process.", NULL);
			}

			OutputDebugStringW(cmd);
			return;
		}

		for (u32 i = 1; i < numBackupLocations; i++)
		{
			wchar_t outputFile[DQN_ARRAY_COUNT(fullPath)] = {};
			u32 outputFileLen = dsync_make_archive_output_path(
			    backupLocations[i], archiveName, outputFile,
			    DQN_ARRAY_COUNT(outputFile));
			DQN_ASSERT(outputFileLen != 0);

			if (CopyFileW(firstOutputFile, outputFile, true))
			{
				// Success!
			}
			else
			{
				DQN_WIN32_ERROR_BOX("CopyFile() failed", NULL);
			}

            // TODO(doyle): If i ever want hardlinks back ..
#if 0
			if (CreateHardLink(altAbsOutput, absOutput, NULL))
			{
				printf("- Hard-linked file to: %s\n", altAbsOutput);
			}
			else
			{
				printf(
					"- Hard-link failed, maybe destination is on a "
					"different drive, try copying ..\n");
				if (CopyFile(absOutput, altAbsOutput, true))
				{
					printf("- Copied file to: %s\n", altAbsOutput);
				}
				else
				{
					// TODO: Format error message using getLastError(),
					// formatMessage()
					printf("- Failed file copy to: %s\n", altAbsOutput);
				}
			}
#endif

		}
	}
	else
	{
		DQN_WIN32_ERROR_BOX("CreateProcess() failed: Unable to invoke 7za.exe",
		                    NULL);
	}
}

// BIG TODO(doyle): This program only works if, after it detects a change that
// the user continues working and once it hits the threshold, then if it detects
// another change will it backup.

// Reason being is I had trouble getting multithreading to work so I'm taking
// the easy road for now.
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
		const u32 FLAGS = FILE_NOTIFY_CHANGE_DIR_NAME |
		                  FILE_NOTIFY_CHANGE_LAST_WRITE |
		                  FILE_NOTIFY_CHANGE_FILE_NAME;
		const bool WATCH_ALL_SUBDIRECTORIES = true;

		fileFindChangeArray[i] = FindFirstChangeNotificationW(
		    state.locations.watch[i].path, WATCH_ALL_SUBDIRECTORIES, FLAGS);

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

		WatchPath *watch = &state.locations.watch[signalIndex];
		watch->numChanges++;

		// NOTE: If first time detected change, don't backup until we change it
		// again after the minimum time between backup
		if (watch->timeLastDetectedChange == 0)
			watch->timeLastDetectedChange = (f32)dqn_time_now_in_s();

		const f32 MIN_TIME_BETWEEN_BACKUP_IN_S = (60.0f * 2.0f);
		f32 secondsElapsed =
		    (f32)dqn_time_now_in_s() - watch->timeLastDetectedChange;
		if (secondsElapsed >= MIN_TIME_BETWEEN_BACKUP_IN_S)
		{
			dsync_backup(watch->path, state.locations.backup,
			             state.locations.numBackup);

			NOTIFYICONDATAW notifyIconData = {};
			notifyIconData.cbSize          = sizeof(notifyIconData);
			notifyIconData.hWnd            = mainWindow;
			notifyIconData.uID             = WIN32_TASKBAR_ICON_UID;
			notifyIconData.uFlags          = NIF_INFO | NIF_REALTIME;
			notifyIconData.hIcon           = LoadIcon(NULL, IDI_APPLICATION);
			notifyIconData.dwState         = NIS_SHAREDICON;
			swprintf_s(notifyIconData.szInfo,
			           DQN_ARRAY_COUNT(notifyIconData.szInfo),
			           L"Backing up %d changes in \"%s\"", watch->numChanges,
			           watch->path);

			swprintf_s(notifyIconData.szInfoTitle,
			           DQN_ARRAY_COUNT(notifyIconData.szInfoTitle), L"Dsync");

			notifyIconData.dwInfoFlags =
			    NIIF_INFO | NIIF_NOSOUND | NIIF_RESPECT_QUIET_TIME;
			DQN_ASSERT(Shell_NotifyIconW(NIM_MODIFY, &notifyIconData));

			watch->numChanges             = 0;
			watch->timeLastDetectedChange = (f32)dqn_time_now_in_s();
		}

	}

	return 0;
}

