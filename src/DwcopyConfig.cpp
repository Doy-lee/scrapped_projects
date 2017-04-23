#include "DwcopyConfig.h"

#include "DwcopyConsole.h"
#include "Win32Dwcopy.h"
#include "dqn.h"

#define WIN32_LEAN_AND_MEAN
#include <Windows.h>
#include <Shlwapi.h> // PathFileExistsW
#include <stdio.h>

const char *const GLOBAL_INI_SECTION_WATCH_LOCATIONS     = "WatchLocations";
const char *const GLOBAL_INI_SECTION_BACKUP_TO_LOCATIONS = "BackupToLocations";
const char *const GLOBAL_INI_PROPERTY_LOCATION           = "location";

// Returns the len of the actual path regardless of whether the input buffer is
// large enough or not.
FILE_SCOPE u32 config_get_path_internal(wchar_t *outBuf, u32 outBufSize)
{
	u32 len = 0;
	wchar_t programPath[1024] = {};
	u32 programPathLen =
	    win32_get_module_directory(programPath, DQN_ARRAY_COUNT(programPath));
	len += programPathLen;

	const wchar_t *DSYNC_INI_FILE = L"dwcopy.ini";
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
		    "of supplied buffer. Dwcopy ini path is not well defined.",
		    NULL);
		DQN_ASSERT(DQN_INVALID_CODE_PATH);
	}

	return len;
}

void dwcopy_config_write_to_disk(const DqnIni *const ini,
                                DqnPushBuffer *const buffer)
{
	DqnTempBuffer tempBuffer = dqn_push_buffer_begin_temp_region(buffer);
	i32 requiredSize         = dqn_ini_save(ini, NULL, 0);
	u8 *dataToWriteOut = (u8 *)dqn_push_buffer_allocate(buffer, requiredSize);
	dqn_ini_save(ini, (char *)dataToWriteOut, requiredSize);

	DqnFile configFile       = {};
	wchar_t configPath[1024] = {};
	config_get_path_internal(configPath, DQN_ARRAY_COUNT(configPath));
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
	}
	dqn_push_buffer_end_temp_region(tempBuffer);
}

FILE_SCOPE wchar_t **extract_ini_section_internal(const DqnIni *const ini,
                                                  DqnPushBuffer *const pushBuffer,
                                                  const char *const sectionName,
                                                  u32 *const numProperties)
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

// Returns the ini structure and the fileHandle. Pass in an empty fileHandle for
// it to fill out. It creates an ini file if it does not exist yet
DqnIni *const dwcopy_config_load_to_ini(DqnPushBuffer *const buffer,
                                       bool needWritePermission,
                                       DqnFile *const fileHandle)
{
	if (!fileHandle || !buffer) return NULL;

	wchar_t configPath[1024] = {};
	config_get_path_internal(configPath, DQN_ARRAY_COUNT(configPath));

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

void dwcopy_config_load_to_ini_close(DqnIni *const ini,
                                    DqnFile *const fileHandle)
{
	if (ini) dqn_ini_destroy(ini);
	if (fileHandle) dqn_file_close(fileHandle);
}

DwcopyLocations dwcopy_config_load(DqnPushBuffer *const buffer)
{
	DwcopyLocations locations = {};

	// Load CFG file
	DqnFile configFile = {};
	DqnIni *ini = dwcopy_config_load_to_ini(buffer, false, &configFile);

	// Fill out locations data
	{
		locations.backup = extract_ini_section_internal(
		    ini, buffer, GLOBAL_INI_SECTION_BACKUP_TO_LOCATIONS,
		    &locations.numBackup);
		wchar_t **watchLocations = extract_ini_section_internal(
		    ini, buffer, GLOBAL_INI_SECTION_WATCH_LOCATIONS,
		    &locations.numWatch);
		locations.watch = (DwcopyWatchPath *)dqn_push_buffer_allocate(
		    buffer, locations.numWatch * sizeof(DwcopyWatchPath));
		for (u32 i = 0; i < locations.numWatch; i++)
		{
			locations.watch[i].path                   = watchLocations[i];

			// TODO(doyle): Not being cleared to zero in the allocate.
			locations.watch[i].changeTimestamp = 0;
			locations.watch[i].numChanges      = 0;
		}
	}
	dwcopy_config_load_to_ini_close(ini, &configFile);

	return locations;
};
