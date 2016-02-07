#ifndef DSYNC_H
#define DSYNC_H

#include "dsync_def.h"

#define TIME_START_DATE 1900

#define MAX_TOKEN_LEN 20
#define MAX_SWITCH_LENGTH 32508

enum CFGTypes {
	INVALID = 0,
	COMPRESSION,
	BACKUP_LOC,
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
} ProgramState;

inline i32 trimAroundStr(char *src, i32 srcLen, const char charsToTrim[],
                         const i32 charsToTrimSize);
CFGToken generateTokenIfValid(char *tokenString, char *tokenValue,
                              i32 tokenLen);
CFGToken *parseCFGFile(char *cfgBuffer, i32 cfgSize, i32 *numTokens);
char *getDirectoryName(char *absPath);

#endif
