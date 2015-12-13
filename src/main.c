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

// TODO: Split into platform layer and independant layer

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
	SDL_KeyboardEvent keys[7];
	struct {
		SDL_KeyboardEvent escape;
		SDL_KeyboardEvent arrowUp;
		SDL_KeyboardEvent arrowDown;
		SDL_KeyboardEvent arrowLeft;
		SDL_KeyboardEvent arrowRight;
		SDL_KeyboardEvent alphabetKey;
		SDL_MouseButtonEvent mouse;
	};
} input;


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

typedef struct {
	u32 size;
	u8 *base;
	u32 used;
} memoryArena;

internal void initialiseArena(memoryArena *arena, u32 size, u8 *base) {
	arena->size = size;
	arena->base = base;
	arena->used = 0;
}

#define pushStruct(arena, type) (type *)pushSize(arena, sizeof(type))
#define pushArray(arena, count, type) (type *)pushSize(arena, (count)*sizeof(type))
void *pushSize(memoryArena *arena, u32 size) {
	assert((arena->used + size) <= arena->size);
	void *result = arena->base + arena->used;
	arena->used += size;

	return(result);
}

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

// TODO: Clean up parameters
internal void drawBitmap(rect_t srcRect, rect_t destRect, u32 *bmpFile, bmpFileHeader fileHeader, bmpBitmapHeader fileBitmapHeader, textScreenData *screen, SDL_PixelFormat *format) {

	(u8 *) bmpFile += (u8) fileHeader.bitmapOffset;
	u32 bytesPerPixel = fileBitmapHeader.bitsPerPixel / 8;

	// NOTE: Offset pointer to the dest rectangle in BMP data
	u32 numBytesInScanLine = fileBitmapHeader.width * bytesPerPixel;
	// 4 byte boundary padding for each scan line
	numBytesInScanLine += (fileBitmapHeader.width % 4);

	for (int y = srcRect.h; y >= 0; y--) {
		for (int x = 0; x < srcRect.w; x++) {
			// NOTE: BMP is stored from left to right, bottom to top
			//       BB GG RR | 24 bits per pixel

			// TODO: Determine the bit-shift amount to create a mask at
			// runtime. Only applicable for 16bit/32bit BMPS
			//u8 red = (*bmpScanner & fileBitfieldMasks.redMask) >> 24;
			//u8 green = (*bmpScanner & fileBitfieldMasks.greenMask) >> 16;
			//u8 blue = (*bmpScanner & fileBitfieldMasks.blueMask) >> 8;
			
			// Find the position in BMP data where our source rect begins
			// As BMP stores bottom to top and rects are supplied in top-bottom
			// we need to convert from one coordinate system to another
			u32 yRectToBMP = ((fileBitmapHeader.height - srcRect.y - y) *
			            numBytesInScanLine);
			u32 xRectToBMP = (srcRect.x + x) * bytesPerPixel;

			u32 pos = yRectToBMP + xRectToBMP;

			u8 blue = ((u8 *) bmpFile)[pos];
			u8 green = ((u8 *) bmpFile)[pos+1];
			u8 red = ((u8 *) bmpFile)[pos+2];

			// NOTE: Magic colour to simulate transparency is purple!
			if (red == 255 && green == 0 && blue == 255) {
				red = 0;
				blue = 0;
			}

			// TODO: Transparency! Alpha blending!
			u32 pixelColor = SDL_MapRGB(format, red, green, blue);
			screen->backBuffer[((y + destRect.y)*screen->width) + (x + destRect.x)] = pixelColor;
		}
	}
}

internal u32 *loadBmpFile(char *filePath, u32 *buffer) {
	HANDLE fileHandle =
		CreateFile(filePath, // lpFileName
		           GENERIC_READ,           // dwDesiredAccess
		           0,                      // dwShareMode
		           NULL,                   // lpSecurityAttributes,
		           OPEN_EXISTING,          // dwCreationDisposition,
		           FILE_ATTRIBUTE_NORMAL,  // dwFlagsAndAttributes,
		           NULL                    // hTemplateFile
		           );

	if (fileHandle != INVALID_HANDLE_VALUE) {
		LARGE_INTEGER fileSize = {0};

		if (GetFileSizeEx(fileHandle, &fileSize)) {
			u32 fileSize32bit = fileSize.QuadPart;
			u32 numBytesRead = 0;

			if (ReadFile(fileHandle, buffer, fileSize32bit, &numBytesRead,
						NULL) && numBytesRead == fileSize32bit) {
				return buffer;
			} else {
				// TODO: Error handling, read failed
			}
		} else {
			// TODO: Error handling, get file size failed
		}
	} else {
		// TODO: Error handling, file handle could not be created
	}
	return NULL;
}

int main(int argc, char* argv[]) {
	SDL_Init(SDL_INIT_VIDEO);

	// Program initialisation
	textScreenData screen = {0};
	screen.width = 1000;
	screen.height = 750;
	screen.backBuffer = (u32 *) calloc(screen.width * screen.height,
	                                   sizeof(u32));
	assert(screen.backBuffer);

	u32 glyphWidth = 18;
	u32 glyphHeight = 18;

	textCaret caret = {0};
	caret.dimensions.w = glyphWidth/2;
	caret.dimensions.h = glyphHeight;

	textBuffer textBuffer = {0};
	textBuffer.columns = (screen.width/glyphWidth)- 1;
	textBuffer.rows = (screen.height/glyphHeight) - 1;

	input input = {0};

	// TODO: Maintain some persistent program state?
	u32 programMemorySize = Megabytes(8);
	u32 *programMemory = VirtualAlloc(NULL,
	                                  programMemorySize,
	                                  MEM_COMMIT | MEM_RESERVE,
	                                  PAGE_READWRITE);
	memoryArena programArena;
	initialiseArena(&programArena, programMemorySize, (u8 *)programMemory);

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

	char *filePath = "../content/cla_font.bmp";

	// TODO: Possible buffer overflow check!
	u32 *bmpFile = loadBmpFile(filePath, programMemory);
	u32 *bmpScanner = bmpFile;

	bmpFileHeader fileHeader = {0};
	bmpBitmapHeader fileBitmapHeader = {0};
	bmpBitfieldMasks fileBitfieldMasks = {0};
	fileHeader = *((bmpFileHeader *) bmpScanner)++;
	// NOTE: If bmpBitmapHeader.size is 40 then we have a BMP v3.x
	// http://www.fileformat.info/format/bmp/egff.htm
	fileBitmapHeader = *((bmpBitmapHeader *) bmpScanner)++;
	fileBitfieldMasks = *((bmpBitfieldMasks *) bmpScanner);

	// Update arena manually because size of BMP is determined post file load
	u32 *temp = pushSize(&programArena, fileHeader.fileSize);

	b32 globalRunning = TRUE;

	u32 pixelColor =  SDL_MapRGB(format, 0, 0, 255);
	//DrawRectangle(caret.dimensions, pixelColor, &screen);

	// 1000ms in 1s. To target 120 fps we need to display a frame every 8.3ms
	u32 targetFramesPerSecond = 120;
	u32 timingTargetMS = (u32) 1000.0f / targetFramesPerSecond;
	u32 lastUpdateTimeMS = SDL_GetTicks();
	u32 inputParsingMSCounter = 0;

	// Glyph information
	u32 firstUpperAlphabetCharX = 1;
	u32 firstUpperAlphabetCharY = 4;
	u32 firstLowerAlphabetCharX = 1;
	u32 firstLowerAlphabetCharY = 6;
	u32 bmpCharX = 1;
	u32 bmpCharY = 4;
	u32 bmpFontGlyphSize = 18;

	while (globalRunning) {

		// Clear screen
		memset(screen.backBuffer, 0,
		       screen.width * screen.height * sizeof(u32));

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
					if (code >= 'a' && code <= 'z') {
						input.alphabetKey = event.key;
						if (input.alphabetKey.type == SDL_KEYDOWN) {
							caret.colX += 1;
						}
					}
				break;
			}
		}

		// TODO: Input has lag
		if (input.alphabetKey.type == SDL_KEYDOWN) {
			u32 deltaFromInitialChar = input.alphabetKey.keysym.sym - 'a';

			bmpCharX = firstUpperAlphabetCharX + deltaFromInitialChar;
			bmpCharY = firstUpperAlphabetCharY;

			// Check if all the characters are located in one row or
			// not otherwise we'll need some wrapping logic to move
			// the bmp character selector down a row
			u32 numGlyphsPerRow =
				fileBitmapHeader.width/bmpFontGlyphSize;

			u32 numAlphabetCharsInRow =
				numGlyphsPerRow - firstLowerAlphabetCharX;

			if (numAlphabetCharsInRow < 26) {
				if (bmpCharX >= 16) {
					bmpCharY++;
					bmpCharX = bmpCharX % 16;
				}
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

		// BMP drawing
		rect_t srcBmpRect = {0};
		srcBmpRect.x = bmpFontGlyphSize * bmpCharX;
		srcBmpRect.y = bmpFontGlyphSize * bmpCharY;
		// NOTE: glyph on bmp is 18x18
		srcBmpRect.w = bmpFontGlyphSize;
		srcBmpRect.h = bmpFontGlyphSize;

		rect_t destBmpRect = {0};
		destBmpRect.x = caret.dimensions.x;
		destBmpRect.y = caret.dimensions.y;
		destBmpRect.w = srcBmpRect.w;
		destBmpRect.h = srcBmpRect.h;

		drawBitmap(srcBmpRect, destBmpRect, bmpFile, fileHeader, fileBitmapHeader, &screen, format);

		// Draw to screen
		//DrawRectangle(caret.dimensions, 255, &screen);

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
