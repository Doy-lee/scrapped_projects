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
	HMENU popUpMenu;
} DsyncState;

FILE_SCOPE DsyncState globalState;
FILE_SCOPE const char *const GLOBAL_INI_SECTION_WATCH_LOCATIONS     = "WatchLocations";
FILE_SCOPE const char *const GLOBAL_INI_SECTION_BACKUP_TO_LOCATIONS = "BackupToLocations";
FILE_SCOPE const char *const GLOBAL_INI_PROPERTY_LOCATION           = "location";

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

FILE_SCOPE void dsync_unit_test()
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

enum Win32Menu
{
	win32menu_exit
};

#define WIN32_TASKBAR_ICON_UID 0x282ACD13
#define WIN32_TASKBAR_ICON_MSG 0x83AD
FILE_SCOPE LRESULT CALLBACK win32_main_callback(HWND window, UINT msg,
                                                WPARAM wParam, LPARAM lParam)
{
	LRESULT result = 0;
	switch (msg)
	{
		case WM_CREATE:
		{
			globalState.popUpMenu = CreatePopupMenu();
			AppendMenu(globalState.popUpMenu, MF_STRING, win32menu_exit,
			           "Exit");

			// Create Taskbar Icon
			NOTIFYICONDATAW notifyIconData = {};
			notifyIconData.cbSize           = sizeof(notifyIconData);
			notifyIconData.hWnd             = window;
			notifyIconData.uCallbackMessage = WIN32_TASKBAR_ICON_MSG;
			notifyIconData.uID              = WIN32_TASKBAR_ICON_UID;
			notifyIconData.uFlags           = NIF_TIP | NIF_MESSAGE;
			notifyIconData.hIcon            = LoadIcon(NULL, IDI_APPLICATION);
			swprintf_s(notifyIconData.szTip,
			           DQN_ARRAY_COUNT(notifyIconData.szTip), L"Dsync");
			DQN_ASSERT(Shell_NotifyIconW(NIM_ADD, &notifyIconData));
		}
		break;

		case WIN32_TASKBAR_ICON_MSG:
		{
			if (lParam == WM_RBUTTONDOWN)
			{
				POINT p;
				GetCursorPos(&p);

				// A little Windows quirk.  You need to do this so the menu
				// disappears if the user clicks off it
				SetForegroundWindow(window);
				u32 clickedCmd = TrackPopupMenu(
				    globalState.popUpMenu, (TPM_RETURNCMD | TPM_LEFTALIGN |
				                            TPM_BOTTOMALIGN | TPM_RIGHTBUTTON),
				    p.x, p.y, 0, window, 0);

				if (clickedCmd == win32menu_exit)
					SendMessage(window, WM_CLOSE, 0, 0);
			}
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

FILE_SCOPE wchar_t **
dsync_config_extract_ini_section(DqnIni *ini, DqnPushBuffer *pushBuffer,
                                 const char *const sectionName,
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
			bool loadedPathIsValid = true;
			if (PathFileExistsW(wideValue))
			{
				for (u32 i = 0; i < propertyIndex; i++)
				{
					// NOTE: If there are duplicate strings, loaded from before,
					// just reject them
					if (dqn_wstrcmp(wideValue, result[i]) == 0)
					{
						loadedPathIsValid = false;
						break;
					}
				}

				if (loadedPathIsValid)
				{
					// NOTE: +1 since strlen doesn't include null terminator
					u32 valueLen       = dqn_strlen(value) + 1;
					result[valueIndex] = (wchar_t *)dqn_push_buffer_allocate(
					    pushBuffer, valueLen * sizeof(wchar_t));

					swprintf_s(result[valueIndex++], valueLen, L"%s",
					           wideValue);
				}
			}
			else
			{
				loadedPathIsValid = false;
			}

			if (!loadedPathIsValid)
			{
				(*numProperties)--;
			}
		}
	}

	return result;
}

// Returns the len of the actual path regardless of whether the input buffer is
// large enough or not.
FILE_SCOPE u32 dsync_config_get_path(wchar_t *outBuf, u32 outBufSize)
{
	u32 len = 0;
	wchar_t programPath[1024] = {};
	u32 programPathLen =
	    win32_get_module_directory(programPath, DQN_ARRAY_COUNT(programPath));
	len += programPathLen;

	const wchar_t *DSYNC_INI_FILE = L"dsync.ini";
	if (programPath[programPathLen - 1] == '\\')
	{
		len += swprintf_s(outBuf, outBufSize, L"%s%s", programPath,
		                  DSYNC_INI_FILE);
	}
	else
	{
		len += swprintf_s(outBuf, outBufSize, L"%s\\%s", programPath,
		                  DSYNC_INI_FILE);
	}

	if (len == outBufSize)
	{
		DQN_WIN32_ERROR_BOX(
		    "dqn_sprintf() buffer maxed: Len of copied text is len "
		    "of supplied buffer. Dsync ini path is not well defined.",
		    NULL);
		DQN_ASSERT(DQN_INVALID_CODE_PATH);
	}

	return len;
}

// Returns the ini structure and the fileHandle. Pass in an empty fileHandle for
// it to fill out. It creates an ini file if it does not exist yet
FILE_SCOPE DqnIni *dsync_config_load_to_ini(DqnPushBuffer *buffer,
                                            bool needWritePermission,
                                            DqnFile *fileHandle)
{
	if (!fileHandle) return NULL;

	wchar_t configPath[1024] = {};
	dsync_config_get_path(configPath, DQN_ARRAY_COUNT(configPath));

	u32 permissions = 0;
	if (needWritePermission)
	{
		permissions =
		    (dqnfilepermissionflag_read | dqnfilepermissionflag_write);
	}
	else
	{
		permissions = dqnfilepermissionflag_read;
	}

	if (!dqn_file_openw(configPath, fileHandle,
	                    permissions, dqnfileaction_open_only))
	{
		// NOTE: File does not exist, create it
		if (!dqn_file_openw(configPath, fileHandle, permissions,
		                    dqnfileaction_create_if_not_exist))
		{
			DQN_WIN32_ERROR_BOX(
			    "dqn_file_openw() failed: Could not create config "
			    "file", NULL);
			return NULL;
		}
	}

	DqnTempBuffer tempBuffer = dqn_push_buffer_begin_temp_region(buffer);
	u8 *configBuffer =
	    (u8 *)dqn_push_buffer_allocate(tempBuffer.buffer, fileHandle->size);
	size_t bytesRead =
	    dqn_file_read(*fileHandle, configBuffer, fileHandle->size);
	DQN_ASSERT(bytesRead == fileHandle->size);
	DqnIni *ini = dqn_ini_load((char *)configBuffer, NULL);
	dqn_push_buffer_end_temp_region(tempBuffer);

	return ini;
}

FILE_SCOPE void dsync_config_load_to_ini_close(DqnIni *ini, DqnFile *fileHandle)
{
	if (ini) dqn_ini_destroy(ini);
	if (fileHandle) dqn_file_close(fileHandle);
}


FILE_SCOPE DsyncLocations dsync_config_load(DqnPushBuffer *pushBuffer)
{
	DsyncLocations locations = {};

	// Load CFG file
	DqnFile configFile = {};
	DqnIni *ini = dsync_config_load_to_ini(pushBuffer, false, &configFile);

	// Fill out locations data
	{
		locations.backup = dsync_config_extract_ini_section(
		    ini, pushBuffer, GLOBAL_INI_SECTION_BACKUP_TO_LOCATIONS,
		    &locations.numBackup);
		wchar_t **watchLocations = dsync_config_extract_ini_section(
		    ini, pushBuffer, GLOBAL_INI_SECTION_WATCH_LOCATIONS,
		    &locations.numWatch);
		locations.watch = (WatchPath *)dqn_push_buffer_allocate(
		    pushBuffer, locations.numWatch * sizeof(WatchPath));
		for (u32 i = 0; i < locations.numWatch; i++)
		{
			locations.watch[i].path                   = watchLocations[i];

			// TODO(doyle): Not being cleared to zero in the allocate.
			locations.watch[i].timeLastDetectedChange = 0;
			locations.watch[i].numChanges             = 0;
		}
	}
	dsync_config_load_to_ini_close(ini, &configFile);

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

	if (!PathFileExistsW(fullPath))
	{
		DQN_WIN32_ERROR_BOX(
		    "PathFileExistsW() failed: Path does not point to valid file",
		    NULL);
		return;
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
	// Get the first output directory, the first zip is stored there. Later we
	// will copy that file to the other locations
	wchar_t firstOutputFile[DQN_ARRAY_COUNT(fullPath)] = {};
	u32 firstOutputFileLen = dsync_make_archive_output_path(
	    backupLocations[0], archiveName, firstOutputFile,
	    DQN_ARRAY_COUNT(firstOutputFile));
	DQN_ASSERT(firstOutputFileLen != 0);

	// Generate the command line string for 7z
	const wchar_t *const ZIP_EXE_NAME = L"7za";
	const wchar_t *const CMD_SWITCHES = L"a -mx9";
	wchar_t cmd[2048]                 = {};

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

	////////////////////////////////////////////////////////////////////////////
	// Execute ZIP command, then copy files to remainder backup locations
	////////////////////////////////////////////////////////////////////////////
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

// NOTE: If len is -1, then strlen will be used to determine
FILE_SCOPE bool win32_console_writew(wchar_t *string, DWORD len,
                                     DWORD *numWritten)
{
	if (len == -1) len = dqn_wstrlen(string);

	LOCAL_PERSIST HANDLE handle = NULL;
	if (!handle)
	{
		handle = GetStdHandle(STD_OUTPUT_HANDLE);
		if (handle == INVALID_HANDLE_VALUE)
		{
			dqn_win32_display_last_error("GetStdHandle() failed");
			return false;
		}
	}

	if (WriteConsoleW(handle, string, len, numWritten, NULL) == 0)
	{
		dqn_win32_display_last_error("WriteConsoleW() failed");
		return false;
	}

	return true;
}

FILE_SCOPE void dsync_console_write_default_help()
{
	win32_console_writew(
	    L"Dsync by Doyle at github.com/doy-lee/dsync\n\n"
	    L"Usage: dsync {watch|backupto} <directory>\n\n"
	    L"BEWARE: Setting a watch directory as a backup directory will cause an infinite backing up routine\n\n"

	    L"Commands:\n"
	    L"backupto                <directory> - Add directory to, backup the watch directories to.\n"
	    L"help                                - Show this help dialog\n"
	    L"list                                - List the sync configuration\n"
	    L"remove {watch|backupto} <index>     - Remove the entry index from sync configuration.\n"
	    L"                                      Use 'dsync list' to view the indexes\n"
	    L"watch                   <directory> - Add directory to, the watch list for backing up.\n\n",
	    -1, NULL);
}

FILE_SCOPE void
dsync_config_print_out_section_internal(const DqnIni *const ini,
                                        const char *const sectionName,
                                        const wchar_t *const linePrefix)
{
	if (!ini || !sectionName || !linePrefix) return;

	i32 sectionIndex =
	    dqn_ini_find_section(ini, sectionName, 0);
	if (sectionIndex != DQN_INI_NOT_FOUND)
	{
		i32 numProperties = dqn_ini_property_count(ini, sectionIndex);
		for (i32 propertyIndex = 0; propertyIndex < numProperties;
		     propertyIndex++)
		{
			const char *val =
			    dqn_ini_property_value(ini, sectionIndex, propertyIndex);
			wchar_t valW[1024] = {};
			dqn_win32_utf8_to_wchar(val, valW, DQN_ARRAY_COUNT(valW));

			wchar_t output[1024] = {};
			i32 len =
			    swprintf_s(output, DQN_ARRAY_COUNT(output), L"%s[%03d]: %s\n",
			               linePrefix, propertyIndex, valW);

			win32_console_writew(output, len, NULL);
		}
	}
}

FILE_SCOPE void dsync_config_write_to_disk(const DqnIni *const ini,
                                           DqnPushBuffer *const buffer)
{
	DqnTempBuffer tempBuffer = dqn_push_buffer_begin_temp_region(buffer);
	i32 requiredSize         = dqn_ini_save(ini, NULL, 0);
	u8 *dataToWriteOut = (u8 *)dqn_push_buffer_allocate(buffer, requiredSize);
	dqn_ini_save(ini, (char *)dataToWriteOut, requiredSize);

	DqnFile configFile       = {};
	wchar_t configPath[1024] = {};
	dsync_config_get_path(configPath, DQN_ARRAY_COUNT(configPath));
	if (dqn_file_openw(configPath, &configFile, (dqnfilepermissionflag_read |
	                                             dqnfilepermissionflag_write),
	                   dqnfileaction_clear_if_exist))
	{
		dqn_file_write(&configFile, dataToWriteOut, requiredSize, 0);
		dqn_file_close(&configFile);
	}
	else
	{
		dqn_win32_display_last_error("dqn_file_openw() failed");
		win32_console_writew(
		    L"dqn_file_open() failed: Unable to write config file "
		    L"to disk",
		    -1, NULL);
	}
	dqn_push_buffer_end_temp_region(tempBuffer);
}

FILE_SCOPE void dsync_console_handle_args(LPWSTR lpCmdLine)
{
// NOTE: This is for debugging in Visual Studios. Since there is no console
// when debugging. But in release, when using the executable on the command
// line it does have one so we don't need to allocate etc.
#if 0
	AllocConsole();
#else
	if (AttachConsole(ATTACH_PARENT_PROCESS) == 0)
	{
		dqn_win32_display_last_error("AttachConsole() failed");
		return;
	}
#endif

	HANDLE stdoutHandle = GetStdHandle(STD_OUTPUT_HANDLE);
	i32 argc;
	wchar_t **argv = CommandLineToArgvW(lpCmdLine, &argc);
	if (argc < 4)
	{
		// Convert to lowercase
		for (i32 i = 0; i < argc; i++)
		{
			i32 len = dqn_wstrlen(argv[i]);
			for (i32 j     = 0; j < len; j++)
				argv[i][j] = dqn_wchar_to_lower(argv[i][j]);
		}
	}
	else
	{
		dsync_console_write_default_help();
		return;
	}

	enum UpdateMode
	{
		updatemode_invalid,
		updatemode_watch,
		updatemode_backup,
	};

	if (argc == 1)
	{
		if (dqn_wstrcmp(argv[0], L"list") == 0)
		{
			DqnPushBuffer buffer = {};
			dqn_push_buffer_init(&buffer, DQN_KILOBYTE(512), 4);

			DqnFile configFile = {};
			DqnIni *ini = dsync_config_load_to_ini(&buffer, false, &configFile);
			dqn_file_close(&configFile);

			dsync_config_print_out_section_internal(
			    ini, GLOBAL_INI_SECTION_BACKUP_TO_LOCATIONS, L"Backup Locations");
			dsync_config_print_out_section_internal(
			    ini, GLOBAL_INI_SECTION_WATCH_LOCATIONS, L" Watch Locations");
		}
		else
		{
			dsync_console_write_default_help();
		}
	}
	else if (argc == 2)
	{
		if (PathFileExistsW(argv[1]))
		{
			if (!PathIsDirectoryW(argv[1]))
			{
				win32_console_writew(
				    L"Backup/Watch argument was not a "
				    L"directory\n Sorry we don't support "
				    L"individual watch on files at the moment.",
				    -1, NULL);
				return;
			}

			wchar_t destPathW[1024] = {};
			GetFullPathNameW(argv[1], DQN_ARRAY_COUNT(destPathW), destPathW,
			                 NULL);

			enum UpdateMode mode = updatemode_invalid;
			if (dqn_wstrcmp(argv[0], L"watch") == 0)
			{
				mode = updatemode_watch;
			}
			else if (dqn_wstrcmp(argv[0], L"backupto") == 0)
			{
				mode = updatemode_backup;
			}
			else if (dqn_wstrcmp(argv[0], L"help") == 0)
			{
				dsync_console_write_default_help();
				return;
			}
			else
			{
				dsync_console_write_default_help();
				return;
			}

			if (mode == updatemode_watch || updatemode_backup)
			{
				DqnPushBuffer buffer = {};
				dqn_push_buffer_init(&buffer, DQN_KILOBYTE(512), 4);
				DqnFile configFile = {};
				DqnIni *ini =
				    dsync_config_load_to_ini(&buffer, true, &configFile);
				dqn_file_close(&configFile);
				DQN_ASSERT(ini);

				const char *searchSectionString = NULL;
				if (mode == updatemode_watch)
				{
					searchSectionString = GLOBAL_INI_SECTION_WATCH_LOCATIONS;
				}
				else if (mode == updatemode_backup)
				{
					searchSectionString =
					    GLOBAL_INI_SECTION_BACKUP_TO_LOCATIONS;
				}

				i32 sectionIndex =
				    dqn_ini_find_section(ini, searchSectionString, 0);
				if (sectionIndex == DQN_INI_NOT_FOUND)
				{
					sectionIndex =
					    dqn_ini_section_add(ini, searchSectionString, 0);
				}
				DQN_ASSERT(sectionIndex != DQN_INI_NOT_FOUND);

				char destPath[DQN_ARRAY_COUNT(destPathW)] = {};
				DQN_ASSERT(dqn_win32_wchar_to_utf8(destPathW, destPath,
				                                   DQN_ARRAY_COUNT(destPath)));

				dqn_ini_property_add(ini, sectionIndex,
				                     GLOBAL_INI_PROPERTY_LOCATION, 0, destPath,
				                     0);

				dsync_config_write_to_disk(ini, &buffer);
				dsync_config_load_to_ini_close(ini, &configFile);
			}
		}
		else
		{
			win32_console_writew(
			    L"Path to watch/backup was determined invalid by Windows\n", -1,
			    NULL);
		}
	}
	else if (argc == 3)
	{
		bool argsInvalid = false;
		if (dqn_wstrcmp(argv[0], L"remove") == 0)
		{
			enum UpdateMode mode = updatemode_invalid;
			if (dqn_wstrcmp(argv[1], L"backupto") == 0)
			{
				mode = updatemode_backup;
			}
			else if (dqn_wstrcmp(argv[1], L"watch") == 0)
			{
				mode = updatemode_watch;
			}
			else
			{
				dsync_console_write_default_help();
				return;
			}

			DqnPushBuffer buffer = {};
			dqn_push_buffer_init(&buffer, DQN_KILOBYTE(512), 4);

			DqnFile configFile = {};
			DqnIni *ini = dsync_config_load_to_ini(&buffer, true, &configFile);
			dqn_file_close(&configFile);
			DQN_ASSERT(ini);

			i32 sectionIndex = 0;
			if (mode == updatemode_backup)
			{
				sectionIndex = dqn_ini_find_section(
				    ini, GLOBAL_INI_SECTION_BACKUP_TO_LOCATIONS, 0);
			}
			else if (mode == updatemode_watch)
			{
				sectionIndex = dqn_ini_find_section(
				    ini, GLOBAL_INI_SECTION_WATCH_LOCATIONS, 0);
			}
			else
			{
				DQN_ASSERT(DQN_INVALID_CODE_PATH);
			}

			if (sectionIndex == DQN_INI_NOT_FOUND)
			{
				wchar_t output[1024] = {};
				i32 len = swprintf_s(output, DQN_ARRAY_COUNT(output),
				           L"No sections found in ini file for: %s", argv[1]);
				win32_console_writew(output, len, NULL);
				return;
			}

			i32 indexToRemove = dqn_wstr_to_i32(argv[2], dqn_wstrlen(argv[2]));
			i32 indexCount    = dqn_ini_property_count(ini, sectionIndex);
			if (indexToRemove < 0 || indexToRemove > indexCount)
			{
				wchar_t output[1024] = {};
				i32 len = swprintf_s(output, DQN_ARRAY_COUNT(output),
				           L"Supplied index is invalid: %s", argv[1]);
				win32_console_writew(output, len, NULL);
				return;
			}
			dqn_ini_property_remove(ini, sectionIndex, indexToRemove);

			dsync_config_write_to_disk(ini, &buffer);
			dsync_config_load_to_ini_close(ini, &configFile);
		}
		else
		{
			dsync_console_write_default_help();
			return;
		}
	}
	else
	{
		dsync_console_write_default_help();
	}
}

// BIG TODO(doyle): This program only works if, after it detects a change that
// the user continues working and once it hits the threshold, then if it detects
// another change will it backup.

// Reason being is I had trouble getting multithreading to work so I'm taking
// the easy road for now.
int WINAPI wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance,
                    LPWSTR lpCmdLine, int nShowCmd)
{
	dsync_unit_test();

	////////////////////////////////////////////////////////////////////////////
	// Check Command Line
	////////////////////////////////////////////////////////////////////////////
	// NOTE: In win32 if there are no command line args, argc returns 1. If
	// there is exactly 1 argument, argc also returns 1. Win32 treats the empty
	// command line, the first argument the executable. But if there's an
	// argument then the executable no longer counts in argc.
	// So check the cmd line to see if there are any args in the fist place.
	if (dqn_wstrlen(lpCmdLine) != 0)
	{
		// If arguments supplied, treat the invocation of the program as a
		// command line program
		dsync_console_handle_args(lpCmdLine);
		return 0;
	}

	////////////////////////////////////////////////////////////////////////////
	// Initialise Win32 App
	////////////////////////////////////////////////////////////////////////////
	HWND mainWindow = NULL;
	{
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

		mainWindow =
		    CreateWindowExW(0, wc.lpszClassName, NULL, 0, CW_USEDEFAULT,
		                    CW_USEDEFAULT, 0, 0, NULL, NULL, hInstance, NULL);

		if (!mainWindow)
		{
			DQN_WIN32_ERROR_BOX("CreateWindowExW() failed.", NULL);
			return -1;
		}
	}
	DQN_ASSERT(mainWindow);

	////////////////////////////////////////////////////////////////////////////
	// Read User Data
	////////////////////////////////////////////////////////////////////////////
	DqnPushBuffer pushBuffer = {};
	dqn_push_buffer_init(&pushBuffer, DQN_KILOBYTE(512), 4);
	globalState.locations  = dsync_config_load(&pushBuffer);

	if (globalState.locations.numBackup <= 0 ||
	    globalState.locations.numWatch <= 0)
	{
		DQN_WIN32_ERROR_BOX(
		    "dsync_config_load() returned empty: There are no backup locations "
		    "and/or watch locations.",
		    NULL);
		return 0;
	}
	else if (!globalState.locations.watch || !globalState.locations.backup)
	{
		DQN_WIN32_ERROR_BOX(
		    "dsync_config_load() returned empty: There are no strings defined "
		    "in the backup and/or watch locations",
		    NULL);
		return 0;
	}

	////////////////////////////////////////////////////////////////////////////
	// Setup win32 handles for monitoring changes
	////////////////////////////////////////////////////////////////////////////
	HANDLE *fileFindChangeArray = (HANDLE *)dqn_push_buffer_allocate(
	    &pushBuffer, globalState.locations.numWatch);
	for (u32 i = 0; i < globalState.locations.numWatch; i++)
	{
		const u32 FLAGS = FILE_NOTIFY_CHANGE_DIR_NAME |
		                  FILE_NOTIFY_CHANGE_LAST_WRITE |
		                  FILE_NOTIFY_CHANGE_FILE_NAME;
		const bool WATCH_ALL_SUBDIRECTORIES = true;

		fileFindChangeArray[i] = FindFirstChangeNotificationW(
		    globalState.locations.watch[i].path, WATCH_ALL_SUBDIRECTORIES, FLAGS);

		if (fileFindChangeArray[i] == INVALID_HANDLE_VALUE)
		{
			DQN_WIN32_ERROR_BOX("FindFirstChangeNotification() failed.", NULL);
			return -1;
		}
	}

	while (true)
	{
		MSG msg;
		while (PeekMessage(&msg, mainWindow, 0, 0, PM_REMOVE))
		{
			TranslateMessage(&msg);
			DispatchMessageW(&msg);
		}

		////////////////////////////////////////////////////////////////////////
		// Begin blocking call to watch files
		////////////////////////////////////////////////////////////////////////
		const i32 NUM_HANDLES = globalState.locations.numWatch;
		DWORD waitSignalled   = WaitForMultipleObjects(
		    NUM_HANDLES, fileFindChangeArray, false, INFINITE);

		////////////////////////////////////////////////////////////////////////
		// File Changes Detected
		////////////////////////////////////////////////////////////////////////
		DQN_ASSERT(waitSignalled != WAIT_TIMEOUT);
		if (waitSignalled == WAIT_FAILED)
		{
			dqn_win32_display_last_error("WaitForMultipleObjects() failed");
			return 0;
		}

		i32 signalIndex = waitSignalled - WAIT_OBJECT_0;
		DQN_ASSERT(signalIndex >= 0 && signalIndex < NUM_HANDLES);
		if (FindNextChangeNotification(fileFindChangeArray[signalIndex]) == 0)
		{
			dqn_win32_display_last_error("FindNextChangeNotification() failed");
			return 0;
		}

		////////////////////////////////////////////////////////////////////////
		// Handle file change logic
		////////////////////////////////////////////////////////////////////////
		WatchPath *watch = &globalState.locations.watch[signalIndex];
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
			dsync_backup(watch->path, globalState.locations.backup,
			             globalState.locations.numBackup);

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

