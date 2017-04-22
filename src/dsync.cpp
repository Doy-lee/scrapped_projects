#include "Dsync.h"

#include "DsyncConfig.h"
#include "DsyncConsole.h"
#include "Win32Dsync.h"

#include <Pathcch.h> // PathCchRemoveFileSpec
#include <Shlwapi.h> // PathFileExistsW
#include <stdio.h>

#define DQN_IMPLEMENTATION
#include "dqn.h"

#define DSYNC_DEBUG true

FILE_SCOPE u32 dsync_make_archive_output_path(wchar_t *const outputDirectory,
                                              wchar_t *const archiveName,
                                              wchar_t *const result,
                                              const u32 resultLen)
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

void dsync_backup(wchar_t *const path, wchar_t **const backupLocations,
                  const u32 numBackupLocations)
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
	if (CreateProcessW(NULL, cmd, NULL, NULL, false, CREATE_NO_WINDOW, NULL,
	                   NULL, &startInfo, &procInfo))
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
