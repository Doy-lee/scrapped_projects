#include <stdio.h>
#include <assert.h>
#include <SDL.h>
#include <windows.h>

#define TRUE 1
#define FALSE 0

#define SCREEN_WIDTH 1024
#define SCREEN_HEIGHT 768

#define Kilobytes(Value) ((Value)*1024LL)
#define Megabytes(Value) (Kilobytes(Value)*1024LL)
#define Gigabytes(Value) (Megabytes(Value)*1024LL)
#define Terabytes(Value) (Gigabytes(Value)*1024LL)

#define internal static
#define inline __inline

typedef uint64_t u64;
typedef uint32_t u32;
typedef int32_t i32;
typedef i32 b32;

typedef struct {
	int x;
	int y;
	int w;
	int h;
} rect_t;

typedef struct {
	void *contents;
	u32 size;
} file_read_result;

typedef struct {
	u32 dataLength;
	u32 type;
	byte *data;
	u32 crc;
} pngChunk;

typedef struct {
	u32 width;        /* Width of image in pixels */
	u32 height;       /* Height of image in pixels */
	BYTE bitDepth;      /* Bits per pixel or per sample */
	BYTE colorType;     /* Color interpretation indicator */
	BYTE compression;   /* Compression type indicator */
	BYTE filter;        /* Filter type indicator */
	BYTE interlace;     /* Type of interlacing scheme used */
} ihdrChunk;

internal void DrawRectangle (rect_t rect, u32 pixel_color, u32 *screen_pixels) {
	assert(screen_pixels);
	for (int x = 0; x < rect.h; x++) {
		for (int y = 0; y < rect.w; y++) {
			screen_pixels[(y + rect.y)*SCREEN_WIDTH + x + rect.x] = pixel_color;
		}
	}
}

internal file_read_result platformLoadFileToMemory (char *filePath,
	                                                void *memory) {
	file_read_result result = {0};

	HANDLE fileHandle = CreateFile(filePath,
	                               GENERIC_READ,
	                               0,
	                               NULL,
	                               OPEN_EXISTING,
	                               FILE_ATTRIBUTE_NORMAL,
	                               NULL);

	// TODO: Read file is not workgin!
	if (fileHandle != INVALID_HANDLE_VALUE)  {
		LARGE_INTEGER size;
		GetFileSizeEx(fileHandle, &size);

		// TODO: Only reads in 32-bit size files e.g. < 4gb
		result.size = (u32) size.QuadPart;
		
		void *buffer = malloc(result.size);

		u32 bytesRead = 0;
		ReadFile(fileHandle,
		         memory,
		         result.size,
		         &bytesRead,
		         NULL);

		if (bytesRead != result.size) {
			//TODO: Error handling
		} else {
			result.contents = memory;
		}
		
	} else {
		// TODO: Error handling
	}

	CloseHandle(fileHandle);

	return result;
}

internal pngChunk extractPngChunk (u32 *pngBufferPointer) {
	pngChunk result = {0};
	result.dataLength = reverse32BitEndianess(*pngBufferPointer++);
	result.type = reverse32BitEndianess(*pngBufferPointer++);

	// TODO: Use our program allocated memory! not the heap
	byte *chunkData = (byte *) malloc(sizeof(byte) * result.dataLength);
	for (int i = 0; i < result.dataLength; i++) {
		chunkData[i] = *((byte *) pngBufferPointer);
		((byte *) pngBufferPointer)++;
	}

	result.data = chunkData;
	result.crc = *(pngBufferPointer++);

	return result;
}

internal inline u32 reverse32BitEndianess (u32 data) {
	u32 result = ((((data >> 24) & 0xFF) << 0 )|
	              (((data >> 16) & 0xFF) << 8 )|
	              (((data >> 8 ) & 0xFF) << 16)|
	              (((data >> 0 ) & 0xFF) << 24));
	return result;
}

int main(int argc, char* argv[]) {
	SDL_Init(SDL_INIT_VIDEO);

	SDL_Window *win = SDL_CreateWindow("Text Editor", SDL_WINDOWPOS_UNDEFINED,
	                                   SDL_WINDOWPOS_UNDEFINED, SCREEN_WIDTH,
	                                   SCREEN_HEIGHT, 0);
	assert(win);
	SDL_Renderer *renderer = SDL_CreateRenderer(win, 0, SDL_RENDERER_SOFTWARE);
	assert(renderer);

	SDL_PixelFormat *format = SDL_AllocFormat(SDL_PIXELFORMAT_RGB888);
	SDL_Texture *screen = SDL_CreateTexture(renderer, format->format,
	                                        SDL_TEXTUREACCESS_STREAMING,
	                                        SCREEN_WIDTH, SCREEN_HEIGHT);
	assert(screen);

	u32 *screen_pixels = (u32 *) calloc(SCREEN_WIDTH * SCREEN_HEIGHT,
	                                    sizeof(u32));
	assert(screen_pixels);

	b32 done = FALSE;

	b32 pressed_up = FALSE;
	b32 pressed_down = FALSE;
	b32 pressed_left = FALSE;
	b32 pressed_right = FALSE;

	// 1 megabyte
	u32 programMemorySize = Megabytes(8);
	u32 *programMemory = VirtualAlloc(NULL,
	                                  programMemorySize,
	                                  MEM_COMMIT | MEM_RESERVE,
	                                  PAGE_READWRITE);

	rect_t square = {0, 0, 30, 30};
	u32 pixel_color =  SDL_MapRGB(format, 0, 0, 255);
	DrawRectangle(square, 255, screen_pixels);

	// TODO: File reads only read into the base address of our program memory
	file_read_result file = platformLoadFileToMemory("../content/cla_font.png",
	                                                 programMemory);

	u32 *pngScanner = file.contents;
	u64 pngHeader = *((u64 *)(pngScanner))++;


	// TODO: Skip palette chunk for now assuming that user has a truecolour
	// display i.e. no need to have an indexed palette for image recreation

	// Parse IHDR data
	pngChunk ihdrHeader = extractPngChunk(pngScanner);
	u32 *ihdrPointer = (u32 *) ihdrHeader.data;
	ihdrChunk ihdrData = {0};
	ihdrData.width = reverse32BitEndianess(*ihdrPointer++);
	ihdrData.height = reverse32BitEndianess(*ihdrPointer++);
	ihdrData.bitDepth = *((byte *) ihdrPointer)++;
	ihdrData.colorType = *((byte *) ihdrPointer)++;
	ihdrData.compression = *((byte *) ihdrPointer)++;
	ihdrData.filter = *((byte *) ihdrPointer)++;
	ihdrData.interlace = *((byte *) ihdrPointer)++;

	// NOTE: Move png buffer pointer ahead data length bytes and move the
	// pointer 3, 32 bit integer times to advance to next chunk, where 3 is the
	// datalength, type, crc fields.
	pngScanner = (u32 *) (((byte *)(pngScanner)) + ihdrHeader.dataLength);
	pngScanner += 3;

	pngChunk nextHeader = extractPngChunk(pngScanner);

	while (!done) {

		SDL_Event event;
		while (SDL_PollEvent(&event)) {
			if (event.type == SDL_QUIT) {
				done = TRUE;
				break;
			}

			if (event.type != SDL_KEYDOWN && event.type != SDL_KEYUP) {
				break;
			}

			SDL_Keycode code = event.key.keysym.sym;
			switch (code) {
				case SDLK_ESCAPE:
					done = (event.type == SDL_KEYDOWN);
				break;

				case SDLK_UP:
					pressed_up = (event.type == SDL_KEYDOWN);
				break;

				case SDLK_DOWN:
					pressed_down = (event.type == SDL_KEYDOWN);
				break;
				
				case SDLK_LEFT:
					pressed_left = (event.type == SDL_KEYDOWN);
				break;

				case SDLK_RIGHT:
					pressed_right = (event.type == SDL_KEYDOWN);
				break;

				default:
				break;
			}
		}

		memset(screen_pixels, 0, SCREEN_WIDTH * SCREEN_HEIGHT * sizeof(u32));

		if (pressed_up) {
			square.y -= 1;
		}

		if (pressed_down) {
			square.y += 1;
		}

		if (pressed_left) {
			square.x -= 1;
		}

		if (pressed_right) {
			square.x += 1;
		}

		DrawRectangle(square, 255, screen_pixels);

		SDL_UpdateTexture(screen, NULL, screen_pixels,
		                  SCREEN_WIDTH*sizeof(u32));
		SDL_RenderClear(renderer);
		SDL_RenderCopy(renderer, screen, NULL, NULL);
		SDL_RenderPresent(renderer);
		SDL_Delay(50);
	}

	free(ihdrHeader.data);
	return 1;
}
