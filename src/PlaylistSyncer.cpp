#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <direct.h>
#include <assert.h>

#include <windows.h>
#include <Shlwapi.h>

#include "PlaylistSyncer.h"

#define MAX_PATH_SIZE 260
#define CHAR_SPACE 32

#define ArrayCount(Array) (sizeof(Array) / sizeof(Array[0]))

/**
  References: 
  FPL Structure: https://github.com/tfriedel/foobar2000-export
  
  TODO Add M3U(8), FPL, CUE loading support
  TODO CLI Interface
  TODO Copy only the proper files to directory (actual sync)
  TODO Calculate hash for files
 
 */

bool debug = true;
char *audio_types[10] = {"mp3", "m4a", "flac", "ape", "opus", "ogg", "oga",
						"tta", "wav", "wma"};
char *target_dir;

void displayHelp() {
	printf("Usage: PlaylistSyncer <src playlist> <dest dir>\n");
}


file_attribute getFileAttributes(HANDLE file) {
	file_attribute file_info = {0};

	BY_HANDLE_FILE_INFORMATION file_attrib = {0};
	GetFileInformationByHandle(file, &file_attrib);

	file_info.size.u.LowPart = file_attrib.nFileSizeLow;
	file_info.size.u.HighPart  = file_attrib.nFileSizeHigh;

	FileTimeToSystemTime(&file_attrib.ftLastWriteTime, &file_info.write_times);	
	return file_info;
}

bool checkArrayForString(uint64_t array_size, char **array, char *find_string) {
	for (uint64_t i = 0; i < array_size; i++) {
		//TODO: For arrays[] the program is incrementing by character not by word
		if(strcmp(find_string, array[i]) == 0) {
			return true;
		}
	}
	return false;
}

void copyFilePrintErrors(char *src, char *dest) {
	printf("Copying from .. %s\n", src);
	printf("Writing to .. %s\n", dest);
	if (CopyFile(src, dest, FALSE) == 0) {
		DWORD error_code = GetLastError();
		printf("File could not be copied: %s\n", src);
		char *format_error_string;
		va_list args = NULL;
		FormatMessage(
				FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM,
				0,
				error_code,
				0,
				(LPTSTR) &format_error_string,
				0,
				&args);
		printf("Error: %s\n", format_error_string);
	}
	printf("\n");
}

void trimLeadingSpace(char *string) {
	while (string[0] == CHAR_SPACE) {
		string += sizeof(string[0]);
	}
}

void trimTrailingSpace(char *string) {
	int len = (int) strlen(string);
	while (len > 0) {
		if (string[len] == CHAR_SPACE) {
			string[len] = '\0';
		} else {
			break;
		}
		len--;
	}
}

void removeNonSyncedFiles(sync_state *state) {
	WIN32_FIND_DATA find_data = {};
	HANDLE find_handle;
	char target_dir_query[MAX_PATH_SIZE] = {0};
	strcat(target_dir_query, target_dir);
	strcat(target_dir_query, "*.*");
	if ((find_handle = FindFirstFile(target_dir_query, &find_data)) != INVALID_HANDLE_VALUE) {
		do {
			if (strcmp(find_data.cFileName, ".") == 0) {}
			else if (strcmp(find_data.cFileName, "..") == 0) {}
			else {
				if (find_data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
					if (debug) printf("Dir : %s\n", find_data.cFileName);
				} else {
					//PathFindExtension returns pointer to the preceding '.' char of extension
					char *file_extension = PathFindExtension(find_data.cFileName);
					file_extension += sizeof(*file_extension); //offset to actual extension

					bool isAudio = checkArrayForString(ArrayCount(audio_types),
													   audio_types, file_extension);
					if (isAudio) {
						//Found audio file in target directory, remove it for syncing purposes
						bool synced_file = checkArrayForString(state->filelist_size, state->synced_files,
								find_data.cFileName);
						//bool synced_file = true;
						if (!synced_file) {
							char audio_file_remove[MAX_PATH_SIZE] = {0};
							strcat(audio_file_remove, target_dir);
							strcat(audio_file_remove, find_data.cFileName);
							DeleteFile(audio_file_remove);
							printf("Removed file .. %s\n", find_data.cFileName);
						}
					}
				}
			}
		} while (FindNextFile(find_handle, &find_data) != 0);
	}
	FindClose(find_handle);
}

int syncFileToDir(char *src, sync_state *state) {
	char *filename = extractFilenameFromPath(src);
	char dest[MAX_PATH_SIZE] = {0};
	strcat(dest, target_dir);
	strcat(dest, filename);

	HANDLE dest_file = CreateFile(dest,
			GENERIC_READ | GENERIC_WRITE,
			0,
			NULL,
			OPEN_EXISTING,
			FILE_ATTRIBUTE_NORMAL,
			NULL);
	if (dest_file == INVALID_HANDLE_VALUE) {
		//File doesn't exist yet, copy over
		CloseHandle(dest_file);
		copyFilePrintErrors(src, dest);
	} else {
		//Determine if preexisting file is the same as the file we're going to copy
		HANDLE src_file = CreateFile(src,
				GENERIC_READ | GENERIC_WRITE,
				0,
				NULL,
				OPEN_EXISTING,
				FILE_ATTRIBUTE_NORMAL,
				NULL);

		file_attribute dest_file_attribs = getFileAttributes(dest_file);
		file_attribute src_file_attribs = getFileAttributes(src_file);
		CloseHandle(dest_file);
		CloseHandle(src_file);

		if (debug) {
			printf("A pre-existing file has been found at the destination\n"); 
			printf("========================================================\n");
			printf("dest file: %s\n", dest);
			printf("dest file size: %d\n", dest_file_attribs.size);
			printf("dest file write: %d/%02d/%02d - %02d:%02d:%02d\n",
					dest_file_attribs.write_times.wYear,
					dest_file_attribs.write_times.wMonth, dest_file_attribs.write_times.wDay,
					dest_file_attribs.write_times.wHour, dest_file_attribs.write_times.wMinute,
					dest_file_attribs.write_times.wSecond);
			printf("========================================================\n");
			printf("src file: %s\n", src);
			printf("src file size: %d\n", src_file_attribs.size);
			printf("src file write:  %d/%02d/%02d - %02d:%02d:%02d\n",
					src_file_attribs.write_times.wYear, src_file_attribs.write_times.wMonth,
					src_file_attribs.write_times.wDay, src_file_attribs.write_times.wHour,
					src_file_attribs.write_times.wMinute, src_file_attribs.write_times.wSecond);
			printf("========================================================\n");
		}

		//TODO: We only check file attribs for equality, maybe add byte-to-byte check
		if ((src_file_attribs.size.QuadPart == dest_file_attribs.size.QuadPart) &&
				(src_file_attribs.write_times.wYear == dest_file_attribs.write_times.wYear &&
				 src_file_attribs.write_times.wMonth == dest_file_attribs.write_times.wMonth &&
				 src_file_attribs.write_times.wDay == dest_file_attribs.write_times.wDay &&
				 src_file_attribs.write_times.wHour == dest_file_attribs.write_times.wHour &&
				 src_file_attribs.write_times.wMinute == dest_file_attribs.write_times.wMinute &&
				 src_file_attribs.write_times.wSecond == dest_file_attribs.write_times.wSecond)) {
			printf("Pre-existing file is a duplicate, skip copy\n\n");
		} else {
			printf("Pre-existing does not match source file, overwriting ..\n");
			copyFilePrintErrors(src, dest);
		}
	}
	return (updateSyncState(state, filename)) ? EXIT_SUCCESS : EXIT_FAILURE;

}

char* extractFilenameFromPath(char *path) {
	char *filename;
	char *dir_index = strrchr(path, '\\');
	if (dir_index != NULL) {
		//Dir index points to the last '\\' symbol in src path
		filename = dir_index + sizeof path[0];
	} else {
		filename = path;
	}
	return filename;
}

int updateSyncState(sync_state *state, char* filename) {
	//add file copied over to list of files we synced
	if (state->filelist_index >= state->filelist_size) {
		state->filelist_size++;
		assert(state->filelist_size > 0);
		state->synced_files = (char **) realloc
			(state->synced_files, state->filelist_size * sizeof (state->synced_files));
		if (state->synced_files != NULL) {
			state->synced_files[state->filelist_size-1] =
				(char *) malloc (sizeof(char)*MAX_PATH_SIZE);
		} else {
			printf("Error: Could not allocate enough space for directory listing\n");
			free(state->synced_files);
			return EXIT_FAILURE;
		}
	}
	strcpy(state->synced_files[state->filelist_index++], filename);
	return EXIT_SUCCESS;
}

int playlistSyncer(int argc, char *argv[]) {
	if (debug) {
		printf("rcved arguments:\n");
		for (int k = 0; k < argc; k++) {
			printf(" 	%s\n", argv[k]);	
		}
		printf("\n");
	}

	int num_files = 0;
	//if last argument is not a openable file, target directory is specified
	if (fopen(argv[argc-1], "r") == NULL) {
		target_dir = argv[argc-1];
		num_files = argc-1;
		assert(strlen(target_dir) <= MAX_PATH_SIZE);

		trimLeadingSpace(target_dir);
		trimTrailingSpace(target_dir);
		if (target_dir[strlen(target_dir)-1] != '\\') {
			strcat(target_dir, "\\");
			if (debug) printf("appended directory sign to target directory: %s\n", target_dir);
		}

		if (_mkdir(target_dir) == -1) {
			if (debug) printf ("Target directory already exists .. %s\n", target_dir);

		}
	} else {
		target_dir = "";
		num_files = argc;
	}

	sync_state *state = (sync_state *) malloc(sizeof sync_state);
	assert(state != NULL);
	state->filelist_size = 1;
	state->filelist_index = 0;
	state->synced_files = (char **) malloc(state->filelist_size * sizeof *(state->synced_files));

	assert(state->synced_files != NULL);
	for (int i = 0; i < state->filelist_size; i++) {
		state->synced_files[i] = (char *) malloc (sizeof(char)*MAX_PATH_SIZE);
	}

	for (int i = 1; i < num_files; i++) {
		FILE *playlist = fopen(argv[i], "r");

		if (playlist == NULL) {
			printf("Failed to load .. %s\n", argv[i]);
		} else {
			fseek(playlist, 0, SEEK_END);
			int playlist_size = ftell(playlist);
			if (debug) printf("Loaded playlist %s .. size: %d\n\n", argv[i], playlist_size);
			//TODO: Change fseek && ftell to use WinAPI for safety see: https://www.securecoding.cert.org/confluence/display/c/FIO19-C.+Do+not+use+fseek()+and+ftell()+to+compute+the+size+of+a+regular+file
			fseek(playlist, 0, 0);

			char *src = (char *) malloc(MAX_PATH_SIZE * sizeof *src);
			assert(src != NULL);

			//iterate over each entry in playlist
			while (fgets(src, MAX_PATH_SIZE, playlist) != NULL) {
				//remove at prefix: non leading a-z characters in path
				int src_offset = 0;
				while (!((src[src_offset] >= 65 && src[src_offset] <= 90) ||
							(src[src_offset] >= 97 && src[src_offset] <= 122))) {
					src_offset += sizeof(src[0]);
				}
				char *src_sanitised = src + src_offset;

				//Get rid of \n appended at end of line
				int str_index = (int) (strlen(src_sanitised) - 1);
				assert(src_sanitised[str_index] == '\n');
				src_sanitised[str_index] = '\0';

				trimTrailingSpace(src_sanitised);

				//Check source file exists
				WIN32_FIND_DATA find_file_result = {0};
				if (FindFirstFile(src_sanitised, &find_file_result) == INVALID_HANDLE_VALUE) {
					printf("File not found, skipping file: %s\n", src_sanitised);
				} else {
					if (!(syncFileToDir(src_sanitised, state))) {
						return EXIT_FAILURE;
					}
				}
			}
			fclose(playlist);
			free(src);
		}
	}

	removeNonSyncedFiles(state);
	for (uint64_t i = 0; i < state->filelist_size; i++) {
		printf("Synced File: %s\n", state->synced_files[i]);
	}
	return EXIT_SUCCESS;
}

int main(int argc, char *argv[]) {
	printf ("Playlist Syncer\n");

	if (argc < 2) {
		displayHelp();
		return EXIT_SUCCESS;
	} else if (argc >= 2) {
		if (playlistSyncer(argc, argv) == EXIT_FAILURE) {
			printf("Program exited with errors");
			return EXIT_FAILURE;
		} else {
			return EXIT_SUCCESS;
		}
	}
}

