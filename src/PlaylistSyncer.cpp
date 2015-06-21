#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <direct.h>

#include <windows.h>

#define MAX_PATH_SIZE 260
#define CHAR_SPACE 32

/**
  References: 
  FPL Structure: https://github.com/tfriedel/foobar2000-export
  
  TODO Add M3U(8), FPL, CUE loading support
  TODO CLI Interface
  TODO Copy only the proper files to directory (actual sync)
  TODO Calculate hash for files
 
 */

static void displayHelp();

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

			if (_mkdir(target_dir) == -1) if (debug) printf ("Target directory already exists .. %s\n", target_dir);
			else if (debug) printf ("Creating directory .. %s\n", target_dir);
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
						printf("Copying from .. %s\n", src);
						printf("Writing to .. %s\n", dest);
						if (CopyFile(src, dest, FALSE) == 0) printf("File could not be copied: %s\n", src);		
					} else {
						//Determine if file attribs are different, fallback to byte-to-byte if attribs are equal
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

						SYSTEMTIME dest_file_write_time = {0};
						SYSTEMTIME src_file_write_time = {0};
						FileTimeToSystemTime(&dest_file_attrib.ftLastWriteTime, &dest_file_write_time);	
						FileTimeToSystemTime(&src_file_attrib.ftLastWriteTime, &src_file_write_time);	

						if (debug) printf("A pre-existing file has been found at the destination\n"); 
						if (debug) printf("========================================================\n");
						if (debug) printf("dest file: %s\n", dest); 
						if (debug) printf("dest file write: %d/%02d/%02d - %02d:%02d:%02d\n",
							dest_file_write_time.wYear, dest_file_write_time.wMonth, dest_file_write_time.wDay, 
							dest_file_write_time.wHour, dest_file_write_time.wMinute, dest_file_write_time.wSecond);
						if (debug) printf("========================================================\n");
						if (debug) printf("src file: %s\n", src); 
						if (debug) printf("src file write:  %d/%02d/%02d - %02d:%02d:%02d\n\n", 
							src_file_write_time.wYear, src_file_write_time.wMonth, src_file_write_time.wDay, 
							src_file_write_time.wHour, src_file_write_time.wMinute, src_file_write_time.wSecond);
					}

				}
			}
			fclose(playlist);
		}
		return EXIT_SUCCESS;
	}
}
