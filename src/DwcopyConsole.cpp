#include "DwcopyConsole.h"

#include "Dwcopy.h"
#include "DwcopyConfig.h"
#include "Win32Dwcopy.h"

#include <Shlwapi.h> // PathFileExists, PathIsDirectory
#include <stdio.h>

void dwcopy_console_write(const wchar_t *const string, i32 len)
{
	DWORD numWritten = 0;
	if (len == -1)
	{
		win32_console_writew(string, dqn_wstrlen(string), &numWritten);
	}
	else
	{
		win32_console_writew(string, len, &numWritten);
	}
}

FILE_SCOPE void dwcopy_console_write_default_help()
{
	dwcopy_console_write(
	    L"Dwcopy by Doyle at github.com/doy-lee/dwcopy\n\n"
	    L"Usage: dwcopy {watch|backupto} <directory>\n\n"
	    L"BEWARE: Setting a watch directory as a backup directory will cause an infinite backing up routine\n\n"

	    L"Commands:\n"
	    L"backupto                <directory> - Add directory to, backup the watch directories to.\n"
	    L"help                                - Show this help dialog\n"
	    L"list                                - List the sync configuration\n"
	    L"remove {watch|backupto} <index>     - Remove the entry index from sync configuration.\n"
	    L"                                      Use 'dwcopy list' to view the indexes\n"
	    L"watch                   <directory> - Add directory to, the watch list for backing up.\n\n"
	    );
}

FILE_SCOPE void
dwcopy_console_print_out_section_internal(const DqnIni *const ini,
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
			dwcopy_console_write(output, len);
		}
	}
}

void dwcopy_console_handle_args(const i32 argc, wchar_t **const argv)
{
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
		dwcopy_console_write_default_help();
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
			DqnIni *ini = dwcopy_config_load_to_ini(&buffer, false, &configFile);
			dqn_file_close(&configFile);

			dwcopy_console_print_out_section_internal(
			    ini, GLOBAL_INI_SECTION_BACKUP_TO_LOCATIONS, L"Backup Locations");
			dwcopy_console_print_out_section_internal(
			    ini, GLOBAL_INI_SECTION_WATCH_LOCATIONS, L" Watch Locations");
		}
		else
		{
			dwcopy_console_write_default_help();
		}
	}
	else if (argc == 2)
	{
		if (PathFileExistsW(argv[1]))
		{
			if (!PathIsDirectoryW(argv[1]))
			{
				dwcopy_console_write(
				    L"Backup/Watch argument was not a "
				    L"directory\n Sorry we don't support "
				    L"individual watch on files at the moment.");
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
				dwcopy_console_write_default_help();
				return;
			}
			else
			{
				dwcopy_console_write_default_help();
				return;
			}

			if (mode == updatemode_watch || updatemode_backup)
			{
				DqnPushBuffer buffer = {};
				dqn_push_buffer_init(&buffer, DQN_KILOBYTE(512), 4);
				DqnFile configFile = {};
				DqnIni *ini =
				    dwcopy_config_load_to_ini(&buffer, true, &configFile);
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

				dwcopy_config_write_to_disk(ini, &buffer);
				dwcopy_config_load_to_ini_close(ini, &configFile);
			}
		}
		else
		{
			dwcopy_console_write(
			    L"Path to watch/backup was determined invalid by Windows\n");
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
				dwcopy_console_write_default_help();
				return;
			}

			DqnPushBuffer buffer = {};
			dqn_push_buffer_init(&buffer, DQN_KILOBYTE(512), 4);

			DqnFile configFile = {};
			DqnIni *ini = dwcopy_config_load_to_ini(&buffer, true, &configFile);
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
				dwcopy_console_write(output, len);
				return;
			}

			i32 indexToRemove = dqn_wstr_to_i32(argv[2], dqn_wstrlen(argv[2]));
			i32 indexCount    = dqn_ini_property_count(ini, sectionIndex);
			if (indexToRemove < 0 || indexToRemove > indexCount)
			{
				wchar_t output[1024] = {};
				i32 len = swprintf_s(output, DQN_ARRAY_COUNT(output),
				           L"Supplied index is invalid: %s", argv[1]);
				dwcopy_console_write(output, len);
				return;
			}
			dqn_ini_property_remove(ini, sectionIndex, indexToRemove);

			dwcopy_config_write_to_disk(ini, &buffer);
			dwcopy_config_load_to_ini_close(ini, &configFile);
		}
		else
		{
			dwcopy_console_write_default_help();
			return;
		}
	}
	else
	{
		dwcopy_console_write_default_help();
	}
}
