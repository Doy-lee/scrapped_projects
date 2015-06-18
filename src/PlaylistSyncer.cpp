#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <assert.h>
#include <direct.h>
#include <windows.h>
#include <Strsafe.h>

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

int main(int argc, char *argv[]) {
	if (argc >= 2) {
		uint32_t j = 0;
		for (j = 1; j < argc-1; j++) {
			FILE *playlist = fopen(argv[j], "r");
			fseek(playlist, 0, SEEK_END);

			if (playlist == NULL) {
				printf("Failed to load .. %s", argv[1]);
			} else {
				uint32_t playlist_size = ftell(playlist);
				printf("%s .. size: %d\n", argv[1], playlist_size);
				//TODO: Change fseek && ftell to use WinAPI for safety see: https://www.securecoding.cert.org/confluence/display/c/FIO19-C.+Do+not+use+fseek()+and+ftell()+to+compute+the+size+of+a+regular+file
				fseek(playlist, 0, 0);

				char *filename;
				char src[MAX_PATH_SIZE] = {0};

				char *target_dir;
				//TODO: handle arguments better
				//if dest dir not specified, default to current directory
				if (argc == 2) {
					target_dir = "";
				} else {
					target_dir = argv[(argc-1)];
				}

				printf("sizeof(target_dir): %d\n", strlen(target_dir));
				int32_t i = 0;

				//Pointer arithmetic to trim leading whitespace
				while (target_dir[0] == CHAR_SPACE) {
					target_dir += sizeof(char);
				}

				//Trim trailing whitespace and append directory '\\' if required
				i = (int32_t) ((strlen(target_dir))-1);
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

				while (fgets(src, MAX_PATH_SIZE, playlist) != NULL) {
					i = 0;
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

					printf("copying from .. %s\n", src);
					printf("writing to .. %s\n", dest);
					CopyFile(src, dest, TRUE);

					//NOTE: This only copies a few bytes of the file before terminating, figure out why!
					/**
					  FILE *music_file = fopen(src, "r");
					  fseek(music_file, 0, 0);
					  if (music_file != NULL) {
					  FILE *target_file = fopen(target_dir, "w+");
					  int byte;
					  printf("writing to .. %s\n", target_dir);
					  uint32_t count = 0;
					  while ((byte = fgetc(music_file)) != EOF) {
					  fputc(byte, target_file);
					  byte = fgetc(music_file);
					  }
					  fclose(target_file);	
					  fclose(music_file);
					  } else {
					  printf("file not found: %s", src);
					  }*/
				}
			}
			fclose(playlist);
		}
		return EXIT_SUCCESS;
	} else {
		printf("Usage: PlaylistSyncer <src playlist> <dest dir>\n");
		return EXIT_FAILURE;
	}
}
