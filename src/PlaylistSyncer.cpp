#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <assert.h>
#include <direct.h>
#include <windows.h>
#include <Strsafe.h>

#define MAX_PATH_SIZE 260
/**
  References: 
  		FPL Structure: https://github.com/tfriedel/foobar2000-export
  
  TODO Add M3U(8), FPL, CUE loading support
  TODO CLI Interface
  TODO Copy only the proper files to directory (actual sync)
  TODO Calculate hash for files
 
 */

int main(int argc, char *argv[]) {
	FILE *playlist = fopen("sample_data\\soulhouse.m3u", "r");
	fseek(playlist, 0, SEEK_END);

	if (playlist == NULL) {
		printf("failed to load");
	} else {
		uint32_t playlist_size = ftell(playlist);
		printf("size %d\n", playlist_size);
		//TODO: Change fseek && ftell to use WinAPI for safety see: https://www.securecoding.cert.org/confluence/display/c/FIO19-C.+Do+not+use+fseek()+and+ftell()+to+compute+the+size+of+a+regular+file
		fseek(playlist, 0, 0);
		
		char *filename;
		char music_path[MAX_PATH_SIZE] = {0};

		while (fgets(music_path, MAX_PATH_SIZE, playlist) != NULL) {
			uint32_t i = 0;

			//TODO: Try change size of music_path to the size of the actual path to allow strlen to find
			//      \n character at end of filename 
			while (i < sizeof(music_path) || music_path[i] != '\0') {
				if (music_path[i] == '\n') {
					music_path[i] = '\0';
				}
				i++;
			}

			char *file_in_path = strrchr(music_path, '\\');
			if (file_in_path != NULL) {
				filename = file_in_path + sizeof(char);	
			} else {
				filename = music_path;	
			}
			
			char target_dir[MAX_PATH_SIZE] = {0};
			strcat(target_dir, "test\\");
			strcat(target_dir, filename);
			printf("writing to .. %s\n", target_dir);
			CopyFile(music_path, target_dir, TRUE);
			
			//NOTE: This only copies a few bytes of the file before terminating, figure out why!
			/**
			FILE *music_file = fopen(music_path, "r");
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
				printf("file not found: %s", music_path);
			}*/
		}
	}
	fclose(playlist);

	return EXIT_SUCCESS;
}
