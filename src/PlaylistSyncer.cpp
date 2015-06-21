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
	printf ("Playlist Syncer\n");

	if (argc < 2) {
		displayHelp();
		return EXIT_SUCCESS;
	}

	if (argc >= 2) {
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
						printf("appended directory sign: %s\n", target_dir);
					}
					break;
				}
				i--;
			}

			if (_mkdir(target_dir) == -1) printf ("Directory already exists");
			else printf ("Creating directory ..");
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
				printf("Failed to load .. %s", argv[j]);
			} else {
				uint32_t playlist_size = ftell(playlist);
				printf("%s .. size: %d\n", argv[j], playlist_size);
				//TODO: Change fseek && ftell to use WinAPI for safety see: https://www.securecoding.cert.org/confluence/display/c/FIO19-C.+Do+not+use+fseek()+and+ftell()+to+compute+the+size+of+a+regular+file
				fseek(playlist, 0, 0);

				char *filename;
				char src[MAX_PATH_SIZE] = {0};

				while (fgets(src, MAX_PATH_SIZE, playlist) != NULL) {
					int8_t i = 0;
					//TODO: Try change size of src to the size of the actual path to allow strlen to find
					//      \n character at end of filename 
					while (i < sizeof(src) || src[i] != '\0') {
						if (src[i] == '\n') {
							src[i] = '\0';
						}
						i++;
					}

					char *file_in_path = strrchr(src, '\\');
					if (file_in_path != NULL) {
						filename = file_in_path + sizeof(char);	
					} else {
						filename = src;	
					}

					char dest[MAX_PATH_SIZE] = {0};
					strcat(dest, target_dir);
					strcat(dest, filename);

					printf("Copying from .. %s\n", src);
					printf("Writing to .. %s\n", dest);
					if (CopyFile(src, dest, FALSE) == 0) printf("File could not be copied: %s\n", src);

				}
			}
			fclose(playlist);
		}
		return EXIT_SUCCESS;
	}
}
