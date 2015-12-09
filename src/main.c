#include <stdio.h>
#include <assert.h>
#include <SDL.h>
#include <Windows.h>

#define TRUE 1
#define FALSE 0

#define Kilobytes(Value) ((Value)*1024LL)
#define Megabytes(Value) (Kilobytes(Value)*1024LL)
#define Gigabytes(Value) (Megabytes(Value)*1024LL)
#define Terabytes(Value) (Gigabytes(Value)*1024LL)

#define internal static
#define inline __inline

typedef int8_t i8;
typedef int16_t i16;
typedef int32_t i32;
typedef int64_t i64;
typedef i32 b32;

typedef uint8_t u8;
typedef uint16_t u16;
typedef uint32_t u32;
typedef uint64_t u64;

typedef float r32;
typedef double r64;

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
	u32 *backBuffer;
	u32 width;
	u32 height;
} textScreenData;

typedef struct {
	u32 columns;
	u32 rows;
} textBuffer;

typedef struct {
	rect_t dimensions;
	i32 colX;
	i32 colY;
} textCaret;

typedef union {
	SDL_KeyboardEvent keys[5];
	struct {
		SDL_KeyboardEvent escape;
		SDL_KeyboardEvent arrowUp;
		SDL_KeyboardEvent arrowDown;
		SDL_KeyboardEvent arrowLeft;
		SDL_KeyboardEvent arrowRight;
		SDL_MouseButtonEvent mouse;
	};
} input;


internal void DrawRectangle (rect_t rect, u32 pixel_color,
                             textScreenData *screen) {
	assert(screen);

	if (rect.x < 0) {
		rect.x = 0;
	} else if (rect.x > screen->width) {
		rect.x = screen->width;
	}

	if (rect.y < 0) {
		rect.y = 0;
	} else if (rect.y > screen->height) {
		rect.y = screen->height;
	}

	for (int x = 0; x < rect.w; x++) {
		for (int y = 0; y < rect.h; y++) {
			screen->backBuffer[(y + rect.y)*screen->width + x + rect.x]
				= pixel_color;
		}
	}
}

#pragma pack(push)
#pragma pack(2)
typedef struct bmpFileHeader {
	u16 fileType;     /* File type, always 4D42h ("BM") */
	u32 fileSize;     /* Size of the file in bytes */
	u16 reserved1;    /* Always 0 */
	u16 reserved2;    /* Always 0 */
	u32 bitmapOffset; /* Starting position of image data in bytes */
} bmpFileHeader;

typedef struct bmpBitmapHeader {
	u32 size;            /* Size of this header in bytes */
	i32 width;           /* Image width in pixels */
	i32 height;          /* Image height in pixels */
	u16 planes;          /* Number of color planes */
	u16 bitsPerPixel;    /* Number of bits per pixel */
	u32 compression;     /* Compression methods used */
	u32 sizeOfBitmap;    /* Size of bitmap in bytes */
	i32 horzResolution;  /* Horizontal resolution in pixels per meter */
	i32 vertResolution;  /* Vertical resolution in pixels per meter */
	u16 colorsUsed;      /* Number of colors in the image */
	u32 colorsImportant; /* Minimum number of important colors */
} bmpBitmapHeader;

typedef struct bmpBitfieldMasks {
	u32 redMask;         /* Mask identifying bits of red component */
	u32 greenMask;       /* Mask identifying bits of green component */
	u32 blueMask;        /* Mask identifying bits of blue component */
} bmpBitfieldMasks;
#pragma pack(pop)

int main(int argc, char* argv[]) {
	SDL_Init(SDL_INIT_VIDEO);

	// Program initialisation
	textScreenData screen = {0};
	screen.width = 1000;
	screen.height = 750;
	screen.backBuffer = (u32 *) calloc(screen.width * screen.height,
	                                   sizeof(u32));
	assert(screen.backBuffer);

	u32 glyphWidth = 30;
	u32 glyphHeight = 50;

	textCaret caret = {0};
	caret.dimensions.w = glyphWidth/2;
	caret.dimensions.h = glyphHeight;

	textBuffer textBuffer = {0};
	textBuffer.columns = (screen.width/glyphWidth)- 1;
	textBuffer.rows = (screen.height/glyphHeight) - 1;

	input input = {0};

	// TODO: We don't use this yet!
	// 1 megabyte
	u32 programMemorySize = Megabytes(8);
	u32 *programMemory = VirtualAlloc(NULL,
	                                  programMemorySize,
	                                  MEM_COMMIT | MEM_RESERVE,
	                                  PAGE_READWRITE);
	// SDL Initialisation
	SDL_Window *win = SDL_CreateWindow("Text Editor", SDL_WINDOWPOS_UNDEFINED,
	                                   SDL_WINDOWPOS_UNDEFINED, screen.width,
	                                   screen.height, 0);
	assert(win);
	SDL_Renderer *renderer = SDL_CreateRenderer(win, 0, SDL_RENDERER_SOFTWARE);
	assert(renderer);

	SDL_PixelFormat *format = SDL_AllocFormat(SDL_PIXELFORMAT_RGB888);
	SDL_Texture *screenTex = SDL_CreateTexture(renderer, format->format,
	                                           SDL_TEXTUREACCESS_STREAMING,
	                                           screen.width, screen.height);
	assert(screenTex);

	HANDLE fileHandle =
		CreateFile("../content/test.bmp", // lpFileName
		           GENERIC_READ,           // dwDesiredAccess
		           0,                      // dwShareMode
		           NULL,                   // lpSecurityAttributes,
		           OPEN_EXISTING,          // dwCreationDisposition,
		           FILE_ATTRIBUTE_NORMAL,  // dwFlagsAndAttributes,
		           NULL                    // hTemplateFile
		           );

	bmpFileHeader fileHeader = {0};
	bmpBitmapHeader fileBitmapHeader = {0};
	bmpBitfieldMasks fileBitfieldMasks = {0};
	u32 *bmpScanner = programMemory;
	
	if (fileHandle != INVALID_HANDLE_VALUE) {
		LARGE_INTEGER fileSize = {0};

		if (GetFileSizeEx(fileHandle, &fileSize)) {
			u32 fileSize32bit = fileSize.QuadPart;
			u32 numBytesRead = 0;

			if (ReadFile(fileHandle, programMemory, fileSize32bit, &numBytesRead,
						NULL) && numBytesRead == fileSize32bit) {

				fileHeader = *((bmpFileHeader *) bmpScanner)++;
				// NOTE: If bmpBitmapHeader.size is 40 then we have a BMP v3.x
				// http://www.fileformat.info/format/bmp/egff.htm
				fileBitmapHeader = *((bmpBitmapHeader *) bmpScanner)++;
				fileBitfieldMasks = *((bmpBitfieldMasks *) bmpScanner);

			} else {
				// TODO: Error handling, read failed
			}
		} else {
			// TODO: Error handling, get file size failed
		}
	} else {
		// TODO: Error handling, file handle could not be created
	}

	
	b32 globalRunning = TRUE;


	u32 pixelColor =  SDL_MapRGB(format, 0, 0, 255);
	DrawRectangle(caret.dimensions, pixelColor, &screen);

	// 1000ms in 1s. To target 120 fps we need to display a frame every 8.3ms
	u32 targetFramesPerSecond = 120;
	u32 timingTargetMS = (u32) 1000.0f / targetFramesPerSecond;
	u32 lastUpdateTimeMS = SDL_GetTicks();
	u32 inputParsingMSCounter = 0;
	while (globalRunning) {

		// Clear screen
		memset(screen.backBuffer, 0,
		       screen.width * screen.height * sizeof(u32));

		bmpScanner = programMemory;
		(u8 *) bmpScanner += (u8) fileHeader.bitmapOffset;
		for (int y = fileBitmapHeader.height; y >= 0; y--) {
			for (int x = 0; x < fileBitmapHeader.width; x++) {
				// NOTE: BMP is stored from left to right, bottom to top
				//       BB GG RR | 24 bits per pixel

				// TODO: Determine the bit-shift amount to create a mask at
				// runtime. Only applicable for 16bit/32bit BMPS
				//u8 red = (*bmpScanner & fileBitfieldMasks.redMask) >> 24;
				//u8 green = (*bmpScanner & fileBitfieldMasks.greenMask) >> 16;
				//u8 blue = (*bmpScanner & fileBitfieldMasks.blueMask) >> 8;
				//((u8 *) bmpScanner) += 3;

				u8 blue = *((u8 *) bmpScanner)++;
				u8 green = *((u8 *) bmpScanner)++;
				u8 red = *((u8 *) bmpScanner)++;

				u32 pixelColor = SDL_MapRGB(format, red, green, blue);
				screen.backBuffer[(y * screen.width) + x] = pixelColor;
			}

			// NOTE: BMPs may have padding for each scan-line to ensure a 4 byte
			// boundary.
			if (fileBitmapHeader.width % 4 != 0) {
				((u8 *)bmpScanner) += (fileBitmapHeader.width % 4);
			}
		}

		SDL_Event event;
		while (SDL_PollEvent(&event)) {
			if (event.type == SDL_QUIT) {
				globalRunning = TRUE;
				break;
			}

			// Early exit by ignoring events we don't care about
			if (event.type != SDL_KEYDOWN &&
			    event.type != SDL_KEYUP &&
			    event.type != SDL_MOUSEBUTTONDOWN &&
			    event.type != SDL_MOUSEBUTTONUP) {
				break;
			}

			if (event.type == SDL_MOUSEBUTTONDOWN) {
				input.mouse = event.button;
				caret.colX = (input.mouse.x / glyphWidth);
				caret.colY = (input.mouse.y / glyphHeight);
			}

			// Extract key state
			SDL_Keycode code = event.key.keysym.sym;
			switch (code) {
				case SDLK_ESCAPE:
					input.escape = event.key;
					globalRunning = (event.type == SDL_KEYDOWN);
				break;

				case SDLK_UP:
					input.arrowUp = event.key;
					if (input.arrowUp.type == SDL_KEYDOWN) {
						caret.colY -= 1;
					}
				break;

				case SDLK_DOWN:
					input.arrowDown = event.key;
					if (input.arrowDown.type == SDL_KEYDOWN) {
						caret.colY += 1;
					}
				break;
				
				case SDLK_LEFT:
					input.arrowLeft = event.key;
					if (input.arrowLeft.type == SDL_KEYDOWN) {
						caret.colX -= 1;
					}
				break;

				case SDLK_RIGHT:
					input.arrowRight = event.key;
					if (input.arrowRight.type == SDL_KEYDOWN) {
						caret.colX += 1;
					}
				break;

				default:
				break;
			}
		}

		// Bounds checking for caret
		if (caret.colX < 0) {
			if (caret.colY == 0) {
				caret.colX = 0;
			} else {
				caret.colX = textBuffer.columns;
			}
			caret.colY--;
		} else if (caret.colX >= textBuffer.columns &&
		           caret.colY >= textBuffer.rows) {
			caret.colX = textBuffer.columns;
			caret.colY = textBuffer.rows;
		} else if (caret.colX > textBuffer.columns) {
			caret.colX = 0;
			caret.colY++;
		}

		if (caret.colY < 0) {
			caret.colY = 0;
		} else if (caret.colY > textBuffer.rows) {
			caret.colY = textBuffer.rows;
		}

		// Map screen columns/rows to pixels on screen
		caret.dimensions.x = caret.colX * glyphWidth;
		caret.dimensions.y = caret.colY * glyphHeight;

		// Draw grid line
		/*
		u32 lineThickness = 1;
		u32 gridColour =  SDL_MapRGB(format, 0, 255, 0);
		for (int x = 0; x < textBuffer.columns+1; x++) {
			rect_t verticalLine = {x * glyphWidth, 0, lineThickness,
			                       screen.height};
			DrawRectangle(verticalLine, gridColour, &screen);
		}

		for (int y = 0; y < textBuffer.rows+1; y++) {
			rect_t horizontalLine = {0, y * glyphHeight,
			                         screen.width, lineThickness};
			DrawRectangle(horizontalLine, gridColour, &screen);
		}
		*/


		// Draw to screen
		DrawRectangle(caret.dimensions, 255, &screen);

		SDL_UpdateTexture(screenTex, NULL, screen.backBuffer,
		                  screen.width*sizeof(u32));
		SDL_RenderClear(renderer);
		SDL_RenderCopy(renderer, screenTex, NULL, NULL);

		u32 currentTimeMS = SDL_GetTicks();
		u32 elapsedTimeMS = currentTimeMS - lastUpdateTimeMS;
		if (elapsedTimeMS < timingTargetMS) {
			// NOTE: SDL Delay has a granularity of at least 10ms meaning we
			// will most definitely miss a frame if our timing target is 8ms
			SDL_Delay(timingTargetMS - elapsedTimeMS);
		}

		SDL_RenderPresent(renderer);
		lastUpdateTimeMS = SDL_GetTicks();
	}
	return 1;
}
