#ifndef DSYNC_H
#define DSYNC_H

#define TIME_START_DATE 1900

#define MAX_TOKEN_LEN 20
#define MAX_SWITCH_LENGTH 32508

#include "dqn.h"

enum ConfigType {
	ConfigType_Invalid = 0,
	ConfigType_Compression,
	ConfigType_BackupLocation,
	ConfigType_Count,
};

typedef struct ConfigToken {
	enum ConfigType option;
	char *value;
	i32 valueLen;
} ConfigToken;

typedef struct ProgramState {
	char **backupPaths;
	i32 numBackupPaths;
} ProgramState;

#endif
