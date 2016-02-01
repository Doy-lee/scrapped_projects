#ifndef TXT_H
#define TXT_H

#include "txt_def.h"
#include "txt_math.h"

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
	i32 pos;
	i32 size;
	i32 lastCharPos;
} TextBuffer;

typedef struct ProgramMemory {
	void *block;
	u32 size;
	b32 initialised;
} ProgramMemory;

typedef struct ProgramState {
	ScreenData *screen;
	TextBuffer *buffer;
	TextBuffer *onScreenBuffer;
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

typedef struct BmpHeaders {
	BmpFileHeader *fileHeader;
	BmpBitmapHeader *bitmapHeader;
	u32 *data;
} BmpHeaders;

#endif
