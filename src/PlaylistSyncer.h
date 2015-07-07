#include <stdint.h>
#include <Windows.h>

typedef struct _file_attribute {
	LARGE_INTEGER size;
	SYSTEMTIME write_times;
} file_attribute;

typedef struct _sync_state {
	int filelist_size;
	int filelist_index;
	char **synced_files;
} sync_state;

typedef struct _playlist_syncer {
	char *target_dir;
	char audio_types[10];
} playlist_syncer;

bool checkArrayForString(uint64_t array_size, char *array[], char *find_string);

void copyFilePrintErrors(char *src, char *dest);

void displayHelp();

file_attribute getFileAttributes(HANDLE file);

void trimLeadingSpace(char *string);

void trimTrailingSpace(char *string);

void removeNonSyncedFiles(sync_state *state);

int syncFileToDir(char *src);

char* extractFilenameFromPath(char *path);

int updateSyncState(sync_state *state, char* filename);

int playlistSyncer(int argc, char *argv[]);

