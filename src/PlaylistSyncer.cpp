#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <direct.h>
#include <assert.h>

#include <windows.h>
#include <Shlwapi.h>

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

static void displayHelp();
static void copyFilePrintErrors(char *src, char *dest);

static void displayHelp() {
	printf("Usage: PlaylistSyncer <src playlist> <dest dir>\n");
}

int main(int argc, char *argv[]) {
	bool debug = true;

	printf ("Playlist Syncer\n");

	if (argc < 2) {
		displayHelp();
		return EXIT_SUCCESS;
	}

	if (argc >= 2) {

		if (debug) {
			printf("rcved arguments:\n");
			for (uint16_t k = 0; k < argc; k++) {
				printf(" 	%s\n", argv[k]);	
			}
			printf("\n\n");
		}
		char *audio_sync_list[10];
		audio_sync_list[0] = "mp3";
		audio_sync_list[1] = "m4a";
		audio_sync_list[2] = "flac";
		audio_sync_list[3] = "ape";
		audio_sync_list[4] = "opus";
		audio_sync_list[5] = "ogg";
		audio_sync_list[6] = "oga";
		audio_sync_list[7] = "tta";
		audio_sync_list[8] = "wav";
		audio_sync_list[9] = "wma";

		//NOTE: Enumerate list of files in target directory for checking what files to sync
		uint64_t filelist_size = 16;
		char (*target_dir_files)[MAX_PATH_SIZE];
		target_dir_files = (char(*)[MAX_PATH_SIZE]) malloc(sizeof(*target_dir_files)*filelist_size);
		assert(target_dir_files != NULL);
		
		char *target_dir;
		int8_t num_files = 0;
		//if last argument is not a openable file, target directory is specified 
		if (fopen(argv[argc-1], "r") == NULL) {
			target_dir = argv[argc-1];
			num_files = argc-1;

			//Pointer arithmetic to trim leading whitespace
			while (target_dir[0] == CHAR_SPACE) {
				target_dir += sizeof(char);
			}

			//Trim trailing whitespace and append directory '\\' if required
			int32_t i = (int32_t) ((strlen(target_dir))-1);
			while (i > 0) {
				if (target_dir[i] == CHAR_SPACE) {
					target_dir[i] = '\0';
				} else if (target_dir[i] != '\0') {
					if (target_dir[i] != '\\') {
						strcat(target_dir, "\\");
						if (debug) printf("appended directory sign to target directory: %s\n", target_dir);
					}
					break;
				}
				i--;
			}

			if (_mkdir(target_dir) == -1) {
				if (debug) printf ("Target directory already exists .. %s\n", target_dir);

				/** TODO: Move this to where the file gets copied
					We need to store a list of the files we copy over, then iterate over directory to find
					all AUDIO FILES that weren't just copied over and delete them

				WIN32_FIND_DATA find_data = {};
				HANDLE find_handle;
				uint64_t file_index = 0;
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
								if (file_index >= filelist_size) {
									filelist_size++;
									assert(filelist_size > 0);
									char (*temp)[MAX_PATH_SIZE] = NULL;
									temp = (char (*)[MAX_PATH_SIZE]) realloc(target_dir_files, sizeof(*target_dir_files)*filelist_size);
									if (temp != NULL) {
										target_dir_files = temp;
									} else {
										printf("Error: Could not allocate enough space for directory listing\n");
										free(target_dir_files);
										return EXIT_FAILURE;
									}
								}
								strcpy(target_dir_files[file_index++], find_data.cFileName);
							}
						}
					} while (FindNextFile(find_handle, &find_data) != 0);
				}
				FindClose(find_handle);
				for (uint64_t i = 0; i < filelist_size; i++) {
					printf("File: %s\n", target_dir_files[i]);
				}
				**/
			}
		} else {
			target_dir = "";
			num_files = argc;
		}

		//num files to cycle through depends on if last argument specified is directory or playlist
		int32_t j = 1;
		for (j = 1; j < num_files; j++) {
			FILE *playlist = fopen(argv[j], "r");
			fseek(playlist, 0, SEEK_END);

			if (playlist == NULL) {
				printf("Failed to load .. %s\n", argv[j]);
			} else {

				//TODO: Remove this and add cleaner sync!!
				//dirty syncing, delete all pre-existing audio files then copy over
				WIN32_FIND_DATA find_data = {};
				HANDLE find_handle;
				uint64_t file_index = 0;
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
								file_extension += sizeof(char); //offset to actual extension

								bool isAudio = false;
								for (uint8_t i = 0; i < ArrayCount(audio_sync_list); i++) {
									if (strcmp(file_extension, audio_sync_list[i]) == 0) {
										isAudio = true;
										break;
									}
								}
								if (isAudio) {
									//Found audio file in target directory, remove it for syncing purposes
									char audio_file_remove[MAX_PATH_SIZE] = {0};
									strcat(audio_file_remove, target_dir);
									strcat(audio_file_remove, find_data.cFileName);
									DeleteFile(audio_file_remove);
								}
							}
						}
					} while (FindNextFile(find_handle, &find_data) != 0);
				}
				FindClose(find_handle);

				uint32_t playlist_size = ftell(playlist);
				if (debug) printf("Loaded playlist %s .. size: %d\n\n", argv[j], playlist_size);
				//TODO: Change fseek && ftell to use WinAPI for safety see: https://www.securecoding.cert.org/confluence/display/c/FIO19-C.+Do+not+use+fseek()+and+ftell()+to+compute+the+size+of+a+regular+file
				fseek(playlist, 0, 0);

				char *filename;
				char src[MAX_PATH_SIZE] = {0};

				while (fgets(src, MAX_PATH_SIZE, playlist) != NULL) {

					//Get rid of \n appended at end of line
					uint16_t str_index = 0;
					uint16_t dir_index = -1;
					while (src[str_index] != '\0') {
						if (src[str_index] == '\\') {
							dir_index = str_index;
						} else if (src[str_index] == '\n') {
							src[str_index] = '\0';
							break;
						}
						str_index++;
					}

					//Dir index points to the last '\\' symbol in src path
					if (dir_index != -1) {
						filename = src + ((dir_index+1)*sizeof(char));
					} else {
						filename = src;
					}
					
					//Check source file exists
					WIN32_FIND_DATA find_file_result = {0};
					if (FindFirstFile(src, &find_file_result) == INVALID_HANDLE_VALUE) {
						printf("File not found, skipping file: %s\n", src);
					} else {
						char dest[MAX_PATH_SIZE] = {0};
						strcat(dest, target_dir);
						strcat(dest, filename);

						copyFilePrintErrors(src, dest);
					}

					/** Proper file check
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

						BY_HANDLE_FILE_INFORMATION dest_file_attrib = {0};
						BY_HANDLE_FILE_INFORMATION src_file_attrib = {0};
						GetFileInformationByHandle(dest_file, &dest_file_attrib);
						GetFileInformationByHandle(src_file, &src_file_attrib);
						CloseHandle(src_file);
						CloseHandle(dest_file);

						_LARGE_INTEGER dest_size = {0};
						dest_size.u.LowPart = dest_file_attrib.nFileSizeLow;
						dest_size.u.HighPart = dest_file_attrib.nFileSizeHigh;
						_LARGE_INTEGER src_size = {0};
						src_size.u.LowPart = src_file_attrib.nFileSizeLow;
						src_size.u.HighPart = src_file_attrib.nFileSizeHigh;

						SYSTEMTIME dest_file_write_time = {0};
						SYSTEMTIME src_file_write_time = {0};
						FileTimeToSystemTime(&dest_file_attrib.ftLastWriteTime, &dest_file_write_time);	
						FileTimeToSystemTime(&src_file_attrib.ftLastWriteTime, &src_file_write_time);	

						if (debug) {
							printf("A pre-existing file has been found at the destination\n"); 
							printf("========================================================\n");
							printf("dest file: %s\n", dest); 
							printf("dest file size: %d\n", dest_size);
							printf("dest file write: %d/%02d/%02d - %02d:%02d:%02d\n",
									dest_file_write_time.wYear, dest_file_write_time.wMonth, dest_file_write_time.wDay, 
									dest_file_write_time.wHour, dest_file_write_time.wMinute, dest_file_write_time.wSecond);
							printf("========================================================\n");
							printf("src file: %s\n", src); 
							printf("src file size: %d\n", src_size);
							printf("src file write:  %d/%02d/%02d - %02d:%02d:%02d\n", 
									src_file_write_time.wYear, src_file_write_time.wMonth, src_file_write_time.wDay, 
									src_file_write_time.wHour, src_file_write_time.wMinute, src_file_write_time.wSecond);
						}

						if (dest_size.u.LowPart == src_size.u.LowPart && dest_size.u.HighPart == src_size.u.HighPart) {
							//TODO: We only check file attribs for equality, maybe add byte-to-byte check
							if (debug) printf("========================================================\n");
							if (debug) printf("File sizes are equal\n");
							if (src_file_write_time.wYear == dest_file_write_time.wYear &&
								src_file_write_time.wMonth == dest_file_write_time.wMonth &&
								src_file_write_time.wDay == dest_file_write_time.wDay &&
								src_file_write_time.wHour == dest_file_write_time.wHour &&
								src_file_write_time.wMinute == dest_file_write_time.wMinute &&
								src_file_write_time.wSecond == dest_file_write_time.wSecond) {
									if (debug) printf("Last file write timestamps are equal\n");
									printf("Pre-existing file is a duplicate, skip copy\n\n");
							} else {
								printf("Pre-existing does not match source file, overwriting ..\n");
								copyFilePrintErrors(src, dest);
							}
						} else {
							printf("Pre-existing does not match source file, overwriting ..\n");
							copyFilePrintErrors(src, dest);
						}
						**/

				}
			}
			fclose(playlist);
		}
		return EXIT_SUCCESS;
	}
}

static void copyFilePrintErrors(char *src, char *dest) {
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
