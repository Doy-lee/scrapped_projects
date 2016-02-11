#ifndef DSYNC_H
#define DSYNC_H

#include "dsync_def.h"

#define TIME_START_DATE 1900

#define MAX_TOKEN_LEN 20
#define MAX_SWITCH_LENGTH 32508

enum CFGTypes {
	INVALID = 0,
	BACKUP_LOC,
	HARDLINK,
	NUM_TYPES
};

typedef struct CFGToken {
	enum CFGTypes option;
	char *value;
	i32 valueLen;
} CFGToken;

typedef struct ProgramState {
	char **backupPaths;
	i32 numBackupPaths;
	b32 hardLink;
} ProgramState;

#endif
