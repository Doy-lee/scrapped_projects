#ifndef DSYNC_H
#define DSYNC_H

#include "dqn.h"

typedef struct DwcopyWatchPath
{
	// TODO(doyle): For multi-threaded file monitoring, not implemented yet.
	HANDLE monitorHandle;
	HANDLE waitHandle;

	wchar_t *path;

	// "changeTimestamp" tracks the first instance the OS has detected a file
	//                   change. It gets reset to 0 after every backup.
	// "numChanges" tracks the number of file changes between backups
	f32 changeTimestamp;
	u32 numChanges;
} DwcopyWatchPath;

typedef struct DwcopyLocations
{
	wchar_t **backup;
	u32 numBackup;

	DwcopyWatchPath *watch;
	u32 numWatch;
} DwcopyLocations;

// Backup the files to the backup locations
// Pass in "path" and "backupLocations" strings consisting of "numBackupLocations"
// Returns without action if invalid args
void dwcopy_backup(wchar_t *const path, wchar_t **const backupLocations, const u32 numBackupLocations);

#endif
