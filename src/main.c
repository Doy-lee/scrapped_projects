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
#define globalVariable static
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

globalVariable b32 globalRunning = TRUE;

typedef struct v2 {
	union {
		r32 x;
		r32 w;
	};
	union {
		r32 y;
		r32 h;
	};
} v2;

typedef struct Rect {
	v2 pos;
	v2 size;
} Rect;


inline internal v2 addV2(v2 a, v2 b) {
	v2 result;
	result.x = a.x + b.x;
	result.y = a.y + b.y;
	return result;
}

inline internal v2 subV2(v2 a, v2 b) {
	v2 result;
	result.x = a.x - b.x;
	result.y = a.y - b.y;
	return result;
}

inline internal v2 mulV2(v2 a, v2 b) {
	v2 result;
	result.x = a.x * b.x;
	result.y = a.y * b.y;
	return result;
}

inline internal v2 convertRawPosToVec2(u32 pos, u32 width) {
	v2 result = {0};
	result.x = pos % width;
	result.y = pos / width;
	return result;
}

inline internal u32 convertVec2ToRawPos(v2 pos, u32 width) {
	u32 result;
	result = width * pos.y;
	result += pos.x;
	return result;
}


typedef struct FileReadResult {
	void *contents;
	u32 size;
} FileReadResult;

typedef struct ScreenData {
	u32 *backBuffer;
	u32 bytesPerPixel;

	v2 size;
	v2 sizeInGlyphs;
} ScreenData;

typedef struct TextCaret {
	Rect rect;
	v2 gridPos;
	i32 rawPos;
} TextCaret;

typedef union Input {
	SDL_KeyboardEvent keys[7];
	struct {
		SDL_KeyboardEvent escape;
		SDL_KeyboardEvent arrowUp;
		SDL_KeyboardEvent arrowDown;
		SDL_KeyboardEvent arrowLeft;
		SDL_KeyboardEvent arrowRight;
		SDL_KeyboardEvent key;
		SDL_MouseButtonEvent mouse;
	};
} Input;

typedef struct FontSheet {
	u32 firstUpperAlphaChar;
	u32 firstLowerAlphaChar;
	u32 firstNumericChar;

	v2 glyphSize;
	u32 glyphsPerRow;
	u32 paddingWidth;
} FontSheet;

typedef struct MemoryArena {
	u32 size;
	u8 *base;
	u32 used;
} MemoryArena;

typedef struct TextBuffer {
	char *memory;
	u32 position;
	u32 size;
	u32 lastCharPos;
} TextBuffer;

typedef struct ProgramMemory {
	void *block;
	u32 size;
	b32 initialised;
} ProgramMemory;

typedef struct ProgramState {
	ScreenData *screen;
	TextBuffer *buffer;
	TextCaret *caret;
	FontSheet fontSheet;
	MemoryArena arena;
	Input input;
} ProgramState;

#pragma pack(push)
#pragma pack(2)
typedef struct BmpFileHeader {
	u16 fileType;     /* File type, always 4D42h ("BM") */
	u32 fileSize;     /* Size of the file in bytes */
	u16 reserved1;    /* Always 0 */
	u16 reserved2;    /* Always 0 */
	u32 bitmapOffset; /* Starting position of image data in bytes */
} BmpFileHeader;

typedef struct BmpBitmapHeader {
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
} BmpBitmapHeader;

typedef struct BmpBitfieldMasks {
	u32 redMask;         /* Mask identifying bits of red component */
	u32 greenMask;       /* Mask identifying bits of green component */
	u32 blueMask;        /* Mask identifying bits of blue component */
} BmpBitfieldMasks;
#pragma pack(pop)

internal void initialiseArena(MemoryArena *arena, u32 size, u8 *base) {
	arena->size = size;
	arena->base = base;
	arena->used = 0;
}

#define pushStruct(arena, type) (type *)pushSize(arena, sizeof(type))
#define pushArray(arena, count, type) (type *)pushSize(arena, (count)*sizeof(type))
void *pushSize(MemoryArena *arena, u32 size) {
	assert((arena->used + size) <= arena->size);
	void *result = arena->base + arena->used;
	arena->used += size;

	return(result);
}

internal void DrawRectangle (Rect rect, u32 pixel_color,
                             ScreenData *screen) {
	assert(screen);

	if (rect.pos.x < 0) {
		rect.pos.x = 0;
	} else if (rect.pos.x > screen->size.w) {
		rect.pos.x = screen->size.w;
	}

	if (rect.pos.y < 0) {
		rect.pos.y = 0;
	} else if (rect.pos.y > screen->size.h) {
		rect.pos.y = screen->size.h;
	}

	for (u32 x = 0; x < rect.size.w; x++) {
		for (u32 y = 0; y < rect.size.h; y++) {
			screen->backBuffer[(y + (u32)rect.pos.y)*(u32)screen->size.w + x + (u32)rect.pos.x]
				= pixel_color;
		}
	}
}

// TODO: Clean up parameters
internal void drawBitmap(Rect srcRect, Rect destRect, u32 *bmpFile, BmpFileHeader fileHeader, BmpBitmapHeader fileBitmapHeader, ScreenData *screen, SDL_PixelFormat *format) {

	(u8 *) bmpFile += (u8) fileHeader.bitmapOffset;
	u32 bytesPerPixel = fileBitmapHeader.bitsPerPixel / 8;

	// NOTE: Offset pointer to the dest rectangle in BMP data
	u32 numBytesInScanLine = fileBitmapHeader.width * bytesPerPixel;
	// 4 byte boundary padding for each scan line
	numBytesInScanLine += (fileBitmapHeader.width % 4);

	for (int y = srcRect.size.h; y >= 0; y--) {
		for (int x = 0; x < srcRect.size.w; x++) {
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
			u32 yRectToBMP = ((fileBitmapHeader.height - srcRect.pos.y - y) *
			            numBytesInScanLine);
			u32 xRectToBMP = (srcRect.pos.x + x) * bytesPerPixel;

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
			u32 pixelColor = SDL_MapRGBA(format, red, green, blue, 255);
			screen->backBuffer[((y + (u32)destRect.pos.y)*(u32)screen->size.w) + (x + (u32)destRect.pos.x)] = pixelColor;
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

internal inline void canonicaliseCaretPos(TextCaret *caret, TextBuffer buffer,
                                          ScreenData screen) {

	// Get the last displayable character in the buffer, the caret can't pass
	// the last char
	u32 lastCharInBuffer = 0;
	for (int i = 0; i < buffer.size-1; i++) {
		if (buffer.memory[i] == 0 && buffer.memory[i-1] != 0) {
			lastCharInBuffer = i;
		}
	}

	if (caret->rawPos <= 0) {
		caret->rawPos = 0;
// TODO: Undefined behaviour when text is greater than displayable buffer
	} else if (caret->rawPos >= lastCharInBuffer) {
		caret->rawPos = lastCharInBuffer;
	}

	caret->gridPos = convertRawPosToVec2(caret->rawPos,
	                                     (u32)screen.sizeInGlyphs.w);
}

internal void processEventLoop(ProgramState *state) {
	Input *input = &state->input;
	TextCaret *caret = state->caret;
	FontSheet fontSheet = state->fontSheet;
	ScreenData *screen = state->screen;
	TextBuffer *textBuffer = state->buffer;

	SDL_Event event;
	while (SDL_PollEvent(&event)) {
		if (event.type == SDL_QUIT) {
			globalRunning = FALSE;
			break;
		}

		// TODO: Do we really care about mouse/key up at this point?
		// Early exit by ignoring events we don't care about
		if (event.type != SDL_KEYDOWN &&
				event.type != SDL_KEYUP &&
				event.type != SDL_MOUSEBUTTONDOWN &&
				event.type != SDL_MOUSEBUTTONUP) {
			break;
		}

		if (event.type == SDL_MOUSEBUTTONDOWN) {
			input->mouse = event.button;
			caret->gridPos.x = (u32)(input->mouse.x / fontSheet.glyphSize.w);
			caret->gridPos.y = (u32)(input->mouse.y / fontSheet.glyphSize.h);
		}

		// Extract key state
		SDL_Keycode code = event.key.keysym.sym;
		switch (code) {
			case SDLK_ESCAPE:
				input->escape = event.key;
				globalRunning = (event.type == SDL_KEYDOWN);
				break;

			case SDLK_UP:
				input->arrowUp = event.key;
				if (input->arrowUp.type == SDL_KEYDOWN) {
					// NOTE: Special case where if we are at the top of the
					// document, pressing up should not move the caret
					if (caret->rawPos - (i32)screen->sizeInGlyphs.w >= 0) {
						caret->rawPos -= screen->sizeInGlyphs.w;
					}
				}
				break;

			case SDLK_DOWN:
				input->arrowDown = event.key;
				if (input->arrowDown.type == SDL_KEYDOWN) {
					caret->rawPos += screen->sizeInGlyphs.w;
				}
				break;

			case SDLK_LEFT:
				input->arrowLeft = event.key;
				if (input->arrowLeft.type == SDL_KEYDOWN) {
					caret->rawPos--;
				}
				break;

			case SDLK_RIGHT:
				input->arrowRight = event.key;
				if (input->arrowRight.type == SDL_KEYDOWN) {
					caret->rawPos++;
				}
				break;

			// TODO: Complete return key implementation
			case SDLK_RETURN:
				if (event.key.type == SDL_KEYDOWN) {
					u32 lenToEndOfLine =
						screen->sizeInGlyphs.w -
						(caret->rawPos % (u32)screen->sizeInGlyphs.w) ;
					caret->rawPos += screen->sizeInGlyphs.w;
				}
				break;

			default:
				printf("Key code: %d\n", code);
				input->key = event.key;
				if (event.key.type == SDL_KEYDOWN) {
					if (code == SDLK_BACKSPACE) {
						caret->rawPos--;
						canonicaliseCaretPos(state->caret,
						                     *state->buffer, *state->screen);

						textBuffer->memory[caret->rawPos] = 0;
						for (int i = caret->rawPos; i < textBuffer->size; i++) {
							textBuffer->memory[i] = textBuffer->memory[i+1];
						}
					} else if (code == SDLK_SPACE ||
							(code >= 'a' && code <= 'z') ||
							(code >= '0' && code <= '9')) {

						// Bruteforce shift all array elements after out
						// current caret pos by 1
						if (textBuffer->memory[caret->rawPos] != 0) {
							for (int i = textBuffer->size-1; i > caret->rawPos; i--) {
								textBuffer->memory[i] = textBuffer->memory[i-1];
							}
						}

						if (input->key.keysym.mod & KMOD_SHIFT) {
							// offset to capital letter
							textBuffer->memory[caret->rawPos++] = code - 32;
						} else {
							textBuffer->memory[caret->rawPos++] = code;
						}
					}
				}
				break;
		}
	}
}

internal b32 convertInputToFontCoords(u32 input, FontSheet fontSheet,
                                      ScreenData *screen, v2 *result) {

	// TODO: Group into an object possibly
	u32 initialCharLoc = {0};
	u32 deltaFromInitialCharLoc = 0;
	u32 seriesLength = 0;

	// Generate the x,y coordinate of the character to extract
	if (input >= 'a' && input <= 'z') {
		initialCharLoc = fontSheet.firstLowerAlphaChar;
		deltaFromInitialCharLoc = input - 'a';
		seriesLength = 'a' - 'z';
	} else if  (input >= 'A' && input <= 'Z') {
		initialCharLoc = fontSheet.firstUpperAlphaChar;
		deltaFromInitialCharLoc = input - 'A';
		seriesLength = 'A' - 'Z';
	} else if (input >= '0' && input <= '9') {
		initialCharLoc = fontSheet.firstNumericChar;
		deltaFromInitialCharLoc = input - '0';
		seriesLength = '0' - '9';
	} else {
		return FALSE;
	}

	u32 inputRawSheetCoords = initialCharLoc + deltaFromInitialCharLoc;
	*result = convertRawPosToVec2(inputRawSheetCoords, fontSheet.glyphsPerRow);
	return TRUE;

}

int main(int argc, char* argv[]) {
	SDL_Init(SDL_INIT_VIDEO);

	// Program initialisation
	ProgramMemory memory = {0};
	memory.size = Megabytes(8);
	memory.block = VirtualAlloc(NULL,
	                            memory.size,
	                            MEM_COMMIT | MEM_RESERVE,
	                            PAGE_READWRITE);

	// Assign program state to designated memory block
	ProgramState *state = (ProgramState *)memory.block;
	initialiseArena(&state->arena, memory.size - sizeof(ProgramState),
	                (u8 *)memory.block + sizeof(ProgramState));

	// Load BMP
	char *filePath = "../content/xterm613.bmp";
	u32 *bmpFile = loadBmpFile(filePath,
	                           (u32 *)(state->arena.base + state->arena.used));
	u32 *bmpScanner = bmpFile;

	BmpFileHeader fileHeader = {0};
	BmpBitmapHeader fileBitmapHeader = {0};
	BmpBitfieldMasks fileBitfieldMasks = {0};
	fileHeader = *((BmpFileHeader *) bmpScanner)++;
	// NOTE: If bmpBitmapHeader.size is 40 then we have a BMP v3.x
	// http://www.fileformat.info/format/bmp/egff.htm
	fileBitmapHeader = *((BmpBitmapHeader *) bmpScanner)++;
	fileBitfieldMasks = *((BmpBitfieldMasks *) bmpScanner);

	// Update arena manually because size of BMP is determined post file load
	pushSize(&state->arena, fileHeader.fileSize);

	FontSheet fontSheet = state->fontSheet;
	fontSheet.glyphSize = (v2) {6, 13};
	fontSheet.glyphsPerRow = 16;
	fontSheet.paddingWidth = 1 * fontSheet.glyphSize.w;

	fontSheet.firstUpperAlphaChar = convertVec2ToRawPos((v2){1, 4}, fontSheet.glyphsPerRow);
	fontSheet.firstLowerAlphaChar = convertVec2ToRawPos((v2){1, 6}, fontSheet.glyphsPerRow);
	fontSheet.firstNumericChar = convertVec2ToRawPos((v2){0, 3}, fontSheet.glyphsPerRow);

	// Initialise screen
	state->screen = pushStruct(&state->arena, ScreenData);
	ScreenData *screen = state->screen;
	screen->size = (v2) {990, 720};
	screen->bytesPerPixel = 4;
	// Allocate size for screen backbuffer
	screen->backBuffer =
		pushSize(&state->arena,
				 (u32)screen->size.w * (u32)screen->size.h * screen->bytesPerPixel);

	screen->sizeInGlyphs.w = (u32)(screen->size.w/fontSheet.glyphSize.w);
	screen->sizeInGlyphs.h = (u32)(screen->size.h/fontSheet.glyphSize.h);

	// Initialise caret
	state->caret = pushStruct(&state->arena, TextCaret);
	TextCaret *caret = state->caret;
	caret->rect.size.w = fontSheet.glyphSize.w/2;
	caret->rect.size.h = fontSheet.glyphSize.h;
	Input input = {0};

	// Use remaining space for text buffer
	state->buffer = pushStruct(&state->arena, TextBuffer);
	TextBuffer *textBuffer = state->buffer;
	textBuffer->size = screen->sizeInGlyphs.w * screen->sizeInGlyphs.h;
	textBuffer->memory = pushSize(&state->arena, textBuffer->size);


	// SDL Initialisation
	SDL_Window *win = SDL_CreateWindow("Text Editor", SDL_WINDOWPOS_UNDEFINED,
	                                   SDL_WINDOWPOS_UNDEFINED, screen->size.w,
	                                   screen->size.h, 0);
	assert(win);
	SDL_Renderer *renderer = SDL_CreateRenderer(win, 0, SDL_RENDERER_SOFTWARE);
	assert(renderer);

	SDL_PixelFormat *format = SDL_AllocFormat(SDL_PIXELFORMAT_RGBA8888);
	SDL_Texture *screenTex = SDL_CreateTexture(renderer, format->format,
	                                           SDL_TEXTUREACCESS_STREAMING,
	                                           screen->size.w, screen->size.h);
	assert(screenTex);

	u32 pixelColor =  SDL_MapRGBA(format, 0, 0, 255, 255);
	// 1000ms in 1s. To target 120 fps we need to display a frame every 8.3ms
	u32 targetFramesPerSecond = 120;
	u32 timingTargetMS = (u32) 1000.0f / targetFramesPerSecond;
	u32 lastUpdateTimeMS = SDL_GetTicks();
	u32 inputParsingMSCounter = 0;

	while (globalRunning) {

		// TODO: Dirty clear screen e.g. only clear areas that have changed
		// Clear screen
		memset(screen->backBuffer, 0,
		       screen->size.w * screen->size.h * screen->bytesPerPixel);

		processEventLoop(state);
		canonicaliseCaretPos(state->caret, *state->buffer, *state->screen);

		// Map screen columns/rows to pixels on screen
		caret->rect.pos.x = caret->gridPos.x * fontSheet.glyphSize.w;
		caret->rect.pos.y = caret->gridPos.y * fontSheet.glyphSize.h;

		// TODO: Input has lag
		// TODO: Perhaps we need to assume that all font sheets align characters
		// to the ascii table standard, this would minimise the need to handle
		// mutliple cases
		v2 textBufferPos = {0};
		for (int i = 0; i < textBuffer->size; i++) {
			v2 charCoords;
			if (convertInputToFontCoords(textBuffer->memory[i], fontSheet, screen,
			                          &charCoords)) {
				// TODO: Abstract/clean up here
				Rect srcBmpRect = {0};
				srcBmpRect.pos = mulV2(fontSheet.glyphSize, charCoords);
				srcBmpRect.pos.x += fontSheet.paddingWidth * charCoords.x;
				srcBmpRect.size = fontSheet.glyphSize;

				if (textBufferPos.x >= screen->sizeInGlyphs.w &&
						textBufferPos.y >= screen->sizeInGlyphs.h) {
					// Stop printing out chars, can't display anymore in buffer
					break;
				} else if (textBufferPos.x >= screen->sizeInGlyphs.w) {
					textBufferPos.x = 0;
					textBufferPos.y++;
				}

				if (textBufferPos.y > screen->sizeInGlyphs.h) {
					break;
				}

				Rect destBmpRect = {0};
				destBmpRect.pos = mulV2(textBufferPos, fontSheet.glyphSize);
				destBmpRect.size = srcBmpRect.size;
				drawBitmap(srcBmpRect, destBmpRect, bmpFile, fileHeader,
						fileBitmapHeader, screen, format);
			}
			textBufferPos.x++;
		}

		// Draw screen caret
		u32 caretColor = SDL_MapRGBA(format, 255, 0, 0, 255);
		DrawRectangle(caret->rect, caretColor, screen);

		// Draw grid line
		//u32 lineThickness = 1;
		//u32 gridColour =  SDL_MapRGBA(format, 0, 255, 0, 255);
		//for (int x = 0; x < screen->sizeInGlyphs.w; x++) {
		//	Rect verticalLine = {x * fontSheet.glyphSize.w, 0, lineThickness,
		//	                     screen->size.h};
		//	DrawRectangle(verticalLine, gridColour, screen);
		//}

		//for (int y = 0; y < screen->sizeInGlyphs.h; y++) {
		//	Rect horizontalLine = {0, y * fontSheet.glyphSize.h,
		//	                       screen->size.w, lineThickness};
		//	DrawRectangle(horizontalLine, gridColour, screen);
		//}


		SDL_UpdateTexture(screenTex, NULL, screen->backBuffer,
		                  screen->size.w*sizeof(u32));
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
