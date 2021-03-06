#include <stdio.h>
#include <assert.h>
#include <SDL.h>
#include <Windows.h>

#include "txt.h"
#include "txt_math.h"

globalVariable b32 globalRunning = TRUE;

void initialiseArena(MemoryArena *arena, u32 size, u8 *base) {
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

void drawRectangle (Rect rect, u32 pixel_color,
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
			screen->backBuffer[(y + (u32)rect.pos.y)*(u32)screen->size.w + x
			                   + (u32)rect.pos.x] = pixel_color;
		}
	}
}

// TODO: Clean up parameters
void drawBitmap(Rect srcRect, Rect destRect, u32 *bmpFile,
                BmpFileHeader fileHeader, BmpBitmapHeader fileBitmapHeader,
                ScreenData *screen, SDL_PixelFormat *format) {

	(u8 *) bmpFile += (u8) fileHeader.bitmapOffset;
	u32 bytesPerPixel = fileBitmapHeader.bitsPerPixel / 8;

	// NOTE: Offset pointer to the dest rectangle in BMP data
	u32 numBytesInScanLine = fileBitmapHeader.width * bytesPerPixel;
	// 4 byte boundary padding for each scan line
	numBytesInScanLine += (fileBitmapHeader.width % 4);

	for (i32 y = (i32)srcRect.size.h; y >= 0; y--) {
		for (i32 x = 0; x < (i32)srcRect.size.w; x++) {
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
			i32 yRectToBMP = ((fileBitmapHeader.height - (i32)srcRect.pos.y - y)
			                  * numBytesInScanLine);
			i32 xRectToBMP = ((i32)srcRect.pos.x + x) * bytesPerPixel;

			i32 pos = yRectToBMP + xRectToBMP;

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
			screen->backBuffer[((y + (u32)destRect.pos.y)*(u32)screen->size.w)
			                   + (x + (u32)destRect.pos.x)] = pixelColor;
		}
	}
}

u32 *loadBmpFile(char *filePath, u32 *buffer) {
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
			// TODO: Assumes file size < 32bit
			u32 fileSize32bit = (u32)fileSize.QuadPart;
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

inline i32 getLastCharInBuffer(TextBuffer buffer) {
	// Get the last displayable character in the buffer, the caret can't pass
	// the last char
	i32 result = buffer.size-1;
	for (i32 i = 0; i < buffer.size-1; i++) {
		if (buffer.memory[i] != 0 && buffer.memory[i+1] == 0) {
			result = i+1;
		}
	}
	return result;
}

inline void canonicalisePosToBuffer(i32 *pos, TextBuffer buffer) {

	i32 lastCharInBuffer = getLastCharInBuffer(buffer);

	if (*pos <= 0) {
		*pos = 0;
	} else if (*pos >= lastCharInBuffer) {
		*pos = lastCharInBuffer;
	}
}

inline void canonicaliseCaretPos(TextCaret *caret, TextBuffer buffer,
                                          ScreenData screen) {
	canonicalisePosToBuffer(&caret->rawPos, buffer);
	caret->gridPos = convertRawPosToVec2(caret->rawPos,
	                                     (i32)screen.sizeInGlyphs.w);
}

void processEventLoop(ProgramState *state) {
	Input *input = &state->input;
	TextCaret *caret = state->caret;
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
			// TODO: redo
			//caret->gridPos.x = (i32)(input->mouse.x / fontSheet.glyphSize.w);
			//caret->gridPos.y = (i32)(input->mouse.y / fontSheet.glyphSize.h);
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
					i32 bufferMoveAmount = 0;

					i32 numCharsToStartOfLine = textBuffer->pos %
					                            (i32)screen->sizeInGlyphs.w;
					i32 prevLineLen = 0;
					i32 prevLineIndex = textBuffer->pos;
					prevLineIndex -= numCharsToStartOfLine;
					
					i32 limiter = prevLineIndex - (i32)screen->sizeInGlyphs.w;
					if (limiter <= 0) limiter = 0;

					// NOTE: Count backwards from current position until we hit
					// CRLF or buffer move amount is length of a full line
					for (i32 i = prevLineIndex; i > limiter; i--) {
						if (textBuffer->memory[i] == '\n' &&
						    (i-1) >= 0) {
							if (textBuffer->memory[i-1] == '\r') {
								prevLineLen += 2;
							}
						} else {
							prevLineLen++;
						}
					}

					bufferMoveAmount = numCharsToStartOfLine + 1;
					//textBuffer->pos -= (i32)screen->sizeInGlyphs.w;
					textBuffer->pos -= bufferMoveAmount;
				}
				break;

			case SDLK_DOWN:
				input->arrowDown = event.key;
				if (input->arrowDown.type == SDL_KEYDOWN) {
					textBuffer->pos += (i32)screen->sizeInGlyphs.w;
				}
				break;

			case SDLK_LEFT:
				input->arrowLeft = event.key;
				if (input->arrowLeft.type == SDL_KEYDOWN) {
					textBuffer->pos--;
				}
				break;

			case SDLK_RIGHT:
				input->arrowRight = event.key;
				if (input->arrowRight.type == SDL_KEYDOWN) {
					textBuffer->pos++;
				}
				break;

			// TODO: Complete return key implementation
			case SDLK_RETURN:
				if (event.key.type == SDL_KEYDOWN) {

					if (textBuffer->memory[textBuffer->pos] != 0) {
						for (i32 i = textBuffer->size-2; i >
						     textBuffer->pos; i--) {
							textBuffer->memory[i] = textBuffer->memory[i-2];
						}
					}

					// TODO: Determine file is DOS or UNIX and make platform
					// indepent!
					// NOTE: Insert CR(13) LF(10) to indicate new line
					textBuffer->memory[textBuffer->pos++] = '\r';
					textBuffer->memory[textBuffer->pos++] = '\n';

					state->currLineIndex++;
				}
				break;

			// PARSE input text
			default:
				printf("Key code: %d\n", code);
				input->key = event.key;
				if (event.key.type == SDL_KEYDOWN) {
					if (code == SDLK_BACKSPACE) {
						textBuffer->pos--;
						canonicalisePosToBuffer(&textBuffer->pos, *textBuffer);
						i32 shiftAmount = 0;
						
						// If CRLF found, backspace must remove both bytes
						if (textBuffer->memory[textBuffer->pos] == '\n') {
							textBuffer->pos--;
							canonicalisePosToBuffer(&textBuffer->pos,
							                        *textBuffer);
							if (textBuffer->memory[textBuffer->pos] == '\r') {
								shiftAmount = 2;
							} else {
								// TODO: An unmatched CR/LF has been found
								assert(TRUE);
							}
						} else {
							shiftAmount = 1;
						}

						for (i32 i = textBuffer->pos; i < textBuffer->size;
						     i++) {
							textBuffer->memory[i] = textBuffer->memory
							                                    [i+shiftAmount];
						}

					} else if (code == SDLK_SPACE ||
							(code >= SDLK_SPACE && code <= '~') ||
							(code >= '0' && code <= '9')) {

						// Bruteforce shift all array elements after out
						// current caret pos by 1
						if (textBuffer->memory[textBuffer->pos] != 0) {
							for (i32 i = textBuffer->size-1;
							     i > textBuffer->pos; i--) {
								textBuffer->memory[i] = textBuffer->memory[i-1];
							}
						}

						if (input->key.keysym.mod & KMOD_SHIFT) {
							i32 offset = 0;
							if (code >= 'a' && code <= 'z') offset = 'a' -'A';
							else if (code == '`') offset = '`' - '~';
							else if (code == '0') offset = '0' - ')';
							else if (code == '1') offset = '1' - '!';
							else if (code == '2') offset = '2' - '@';
							else if (code == '3') offset = '3' - '#';
							else if (code == '4') offset = '4' - '$';
							else if (code == '5') offset = '5' - '%';
							else if (code == '6') offset = '6' - '^';
							else if (code == '7') offset = '7' - '&';
							else if (code == '8') offset = '8' - '*';
							else if (code == '9') offset = '9' - '(';
							else if (code == '-') offset = '-' - '_';
							else if (code == '=') offset = '=' - '+';
							else if (code == '[') offset = '[' - '{';
							else if (code == ']') offset = ']' - '}';
							else if (code == '\\') offset = '\\' - '|';
							else if (code == ';') offset = ';' - ':';
							else if (code == '\'') offset = '\'' - '"';
							else if (code == ',') offset = ',' - '<';
							else if (code == '.') offset = '.' - '>';
							else if (code == '/') offset = '/' - '?';
							textBuffer->memory[textBuffer->pos++] = code -
							                                        offset;
						} else {
							textBuffer->memory[textBuffer->pos++] = code;
						}

						state->lineLength[state->currLineIndex]++;
					}
				}
				break;
		}
	}
}

void drawCharacter(ScreenData *screen, FontSheet *fontSheet, v2 onScreenPos,
                   BmpHeaders *bmp, SDL_PixelFormat *format, char input) {
	v2 charCoords = convertRawPosToVec2(input, fontSheet->glyphsPerRow);
	Rect srcBmpRect = {0};
	srcBmpRect.pos = mulV2(fontSheet->glyphSize, charCoords);
	srcBmpRect.pos.x += fontSheet->paddingWidth * charCoords.x;
	srcBmpRect.size = fontSheet->glyphSize;

	Rect destBmpRect = {0};
	destBmpRect.pos = onScreenPos;
	destBmpRect.size = srcBmpRect.size;
	drawBitmap(srcBmpRect, destBmpRect, bmp->data, *bmp->fileHeader,
	           *bmp->bitmapHeader, screen, format);
}

void drawString(ScreenData *screen, FontSheet *fontSheet, v2 onScreenPos,
                BmpHeaders *bmp, SDL_PixelFormat *format, char* string,
                i32 stringLen) {
	// NOTE: String length must be inclusive of null terminator at end
	assert(string[stringLen-1] == 0);
	for (i32 i = 0; i < stringLen-1; i++) {
		drawCharacter(screen, fontSheet, onScreenPos, bmp, format, string[i]);
		onScreenPos.x += fontSheet->glyphSize.w;
	}
}

void drawStringArr(ScreenData *screen, FontSheet *fontSheet, v2 onScreenPos,
                BmpHeaders *bmp, SDL_PixelFormat *format, char *string[],
                i32 stringLen[], i32 numStrings) {

	r32 originalXPos = onScreenPos.x;
	for (i32 i = 0; i < numStrings; i++) {
		drawString(screen, fontSheet, onScreenPos, bmp, format, string[i],
		           stringLen[i]);
		onScreenPos.y += fontSheet->glyphSize.h;
		onScreenPos.x = originalXPos;
	}
}

void advanceCharacterPos(ScreenData *screen, v2 glyphSize, v2 *onScreenPos) {
	i32 maxX = (i32)screen->sizeInGlyphs.w * (i32)glyphSize.w;
	i32 maxY = (i32)screen->sizeInGlyphs.h * (i32)glyphSize.h;
	
	onScreenPos->x += glyphSize.w;

	if (onScreenPos->x >= maxX &&
		onScreenPos->y >= maxY) {
		// Stop printing out chars, can't display anymore in buffer
		return;
	} else if (onScreenPos->x >= maxX) {
		onScreenPos->x = 0;
		onScreenPos->y += glyphSize.h;
	}

	if (onScreenPos->y > maxY) {
		return;
	}
}

i32 tx_strlen(char *string) {
	i32 i = 0;
	i32 result = 0;

	while (string[i++] != 0) {
		result++;
	}

	// Include null-terminator as part of length
	result++;
	return result;
}

void DEBUG_renderGrid(ProgramState *state, SDL_PixelFormat format) {
	ScreenData *screen = state->screen;
	FontSheet *fontSheet = state->fontSheet;

	// Draw grid line
	u32 lineThickness = 1;
	u32 gridColour =  SDL_MapRGBA(&format, 0, 255, 0, 255);
	for (i32 x = 0; x < screen->sizeInGlyphs.w; x++) {
		Rect verticalLine = {x * fontSheet->glyphSize.w, 0.0f,
		                     (r32)lineThickness, screen->size.h};
		drawRectangle(verticalLine, gridColour, screen);
	}

	for (i32 y = 0; y < screen->sizeInGlyphs.h; y++) {
		Rect horizontalLine = {0.0f, y * fontSheet->glyphSize.h,
		                       screen->size.w, (r32)lineThickness};
		drawRectangle(horizontalLine, gridColour, screen);
	}
}

void DEBUG_renderText(ProgramState *state, BmpHeaders *bmp, SDL_PixelFormat
                      format) {

	ScreenData *screen = state->screen;
	FontSheet *fontSheet = state->fontSheet;

	// Debug: currLineIndex
	char *str = "currLineIndex: ";
	v2 strPos = (v2){screen->size.w - 120, 10};
	drawString(screen, fontSheet, strPos, bmp, &format, str,
	           tx_strlen(str));

	// Debug: currLineIndex value
	strPos.x += fontSheet->glyphSize.w * (tx_strlen(str)-1);
	char lineIndexStr[5] = {0};
	sprintf_s(lineIndexStr, 5, "%d", state->currLineIndex);
	drawString(screen, fontSheet, strPos, bmp, &format, lineIndexStr,
	                 tx_strlen(lineIndexStr));

	// Debug: currLineLen
	str = "currLineLen: ";
	strPos = (v2){screen->size.w - 120, 10 + fontSheet->glyphSize.h};
	drawString(screen, fontSheet, strPos, bmp, &format, str,
	           tx_strlen(str));

	// Debug: currLineLen value
	strPos.x += fontSheet->glyphSize.w * (tx_strlen(str)-1);
	char currLineLenStr[5] = {0};
	sprintf_s(currLineLenStr, 5, "%d", state->lineLength[state->currLineIndex]);
	drawString(screen, fontSheet, strPos, bmp, &format, currLineLenStr,
	           tx_strlen(currLineLenStr));
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
	BmpHeaders bmp = {0};
	bmp.data = loadBmpFile(filePath,
	                       (u32 *)(state->arena.base + state->arena.used));
	u32 *bmpScanner = bmp.data;

	// NOTE: If bmpBitmapHeader.size is 40 then we have a BMP v3.x
	// http://www.fileformat.info/format/bmp/egff.htm
	bmp.fileHeader = ((BmpFileHeader *) bmpScanner)++;
	bmp.bitmapHeader = ((BmpBitmapHeader *) bmpScanner)++;

	BmpBitfieldMasks fileBitfieldMasks = {0};
	fileBitfieldMasks = *((BmpBitfieldMasks *) bmpScanner);

	// Update arena manually because size of BMP is determined post file load
	pushSize(&state->arena, bmp.fileHeader->fileSize);

	state->fontSheet = pushStruct(&state->arena, FontSheet);
	FontSheet *fontSheet = state->fontSheet;
	fontSheet->glyphSize = (v2) {6, 13};
	fontSheet->glyphsPerRow = 16;
	fontSheet->paddingWidth = 1 * (i32)fontSheet->glyphSize.w;

	// Initialise screen
	state->screen = pushStruct(&state->arena, ScreenData);
	ScreenData *screen = state->screen;
	screen->size = (v2) {990, 720};
	screen->bytesPerPixel = 4;
	// Allocate size for screen backbuffer
	screen->backBuffer = pushSize(&state->arena,
	                              (i32)screen->size.w * (i32)screen->size.h *
	                              screen->bytesPerPixel);

	screen->sizeInGlyphs.w = (r32)((i32)(screen->size.w/
	                                     fontSheet->glyphSize.w));
	screen->sizeInGlyphs.h = (r32)((i32)(screen->size.h/
	                                     fontSheet->glyphSize.h));
	//screen->sizeInGlyphs.w = 5.0f;
	//screen->sizeInGlyphs.h = 5.0f;

	// Initialise caret
	state->caret = pushStruct(&state->arena, TextCaret);
	TextCaret *caret = state->caret;
	caret->rect.size.w = fontSheet->glyphSize.w/2;
	caret->rect.size.h = fontSheet->glyphSize.h;
	Input input = {0};

	// Create the onscreen text buffer
	state->onScreenBuffer = pushStruct(&state->arena, TextBuffer);
	TextBuffer *onScreenBuffer = state->onScreenBuffer;
	onScreenBuffer->size = (i32)screen->sizeInGlyphs.w *
	                       (i32)screen->sizeInGlyphs.h;
	onScreenBuffer->memory = pushSize(&state->arena, onScreenBuffer->size);

	// Use remaining space for text buffer
	state->buffer = pushStruct(&state->arena, TextBuffer);
	TextBuffer *textBuffer = state->buffer;
	textBuffer->size = (i32)screen->sizeInGlyphs.w *
	                   (i32)screen->sizeInGlyphs.h;
	textBuffer->memory = pushSize(&state->arena, textBuffer->size);
	textBuffer->pos = 0;

	for (i32 i = 0; i < MAX_SCREEN_BUFFER_LINES; i++) {
		state->lineLength[i] = 0;
	}
	state->currLineIndex = 0;

	// SDL Initialisation
	SDL_Window *win = SDL_CreateWindow("Text Editor", SDL_WINDOWPOS_UNDEFINED,
	                                   SDL_WINDOWPOS_UNDEFINED,
	                                   (i32)screen->size.w,
	                                   (i32)screen->size.h, 0);
	assert(win);
	SDL_Renderer *renderer = SDL_CreateRenderer(win, 0, SDL_RENDERER_SOFTWARE);
	assert(renderer);

	SDL_PixelFormat *format = SDL_AllocFormat(SDL_PIXELFORMAT_RGBA8888);
	SDL_Texture *screenTex = SDL_CreateTexture(renderer, format->format,
	                                           SDL_TEXTUREACCESS_STREAMING,
	                                           (i32)screen->size.w,
	                                           (i32)screen->size.h);
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
		       (i32)screen->size.w * (i32)screen->size.h
		       * screen->bytesPerPixel);

		processEventLoop(state);

		// DEBUG TEXT
		static i32 pos;
		if (pos != textBuffer->pos) {
			pos = textBuffer->pos;
			printf("textBuffer->pos: %d\n", textBuffer->pos);
		}

		canonicalisePosToBuffer(&textBuffer->pos, *state->buffer);

		// Convert text buffer to onscreen buffer
		assert(onScreenBuffer->size <= textBuffer->size);
		i32 screenBufferIndex = 0;
		i32 textBufferIndex = 0;
		while (screenBufferIndex < onScreenBuffer->size) {
			if (textBuffer->memory[textBufferIndex] == '\r' &&
				(textBufferIndex+1) < textBuffer->size) {
				if (textBuffer->memory[textBufferIndex+1] == '\n') {
					textBufferIndex += 2;

					i32 lenToEndOfLine = (i32)screen->sizeInGlyphs.w -
					                     (screenBufferIndex %
					                     (i32)screen->sizeInGlyphs.w);

					if (!((screenBufferIndex + lenToEndOfLine) >
					       onScreenBuffer->size)) {
						for (i32 i = 0; i < lenToEndOfLine; i++) {
							onScreenBuffer->memory[screenBufferIndex++] = ' ';
						}
					}
				}
			} else {
				onScreenBuffer->memory[screenBufferIndex++] =
				                         textBuffer->memory[textBufferIndex++];
			}

			if (textBufferIndex > textBuffer->size) break;
		}

		caret->rawPos = 0;

		// Map textBuffer position to our onscreen caret
		assert(textBuffer->pos <= textBuffer->size);
		for (i32 i = 0; i < textBuffer->pos; i++) {
			if(textBuffer->memory[i] == '\r' &&
			   (i+1) < textBuffer->size) {

				if (textBuffer->memory[i+1] == '\n') {
					v2 pos = convertRawPosToVec2(caret->rawPos,
					                             (i32)screen->sizeInGlyphs.w);
					i32 lenToEndOfLine = (i32)screen->sizeInGlyphs.w -
					                     (i32)pos.x;
					caret->rawPos += (lenToEndOfLine);
					i++;
				} else {
					caret->rawPos++;
				}
			} else {
				caret->rawPos++;
			}
		}
		canonicaliseCaretPos(state->caret, *state->onScreenBuffer,
		                     *state->screen);

		// TODO: Input has lag
		v2 onScreenPos = {0};
		for (i32 i = 0; i < onScreenBuffer->size; i++) {
			u32 input = onScreenBuffer->memory[i];
			if (input >= SDLK_SPACE && input <= '~') {
				drawCharacter(screen, fontSheet, onScreenPos, &bmp, format,
				              input);
				advanceCharacterPos(screen, fontSheet->glyphSize, &onScreenPos);
			}
		}

		// Draw screen caret
		u32 caretColor = SDL_MapRGBA(format, 255, 0, 0, 255);

		// Map screen columns/rows to pixels on screen
		caret->rect.pos.x = caret->gridPos.x * fontSheet->glyphSize.w;
		caret->rect.pos.y = caret->gridPos.y * fontSheet->glyphSize.h;

		drawRectangle(caret->rect, caretColor, screen);

		//DEBUG_renderGrid(state, *format);
		DEBUG_renderText(state, &bmp, *format);

		SDL_UpdateTexture(screenTex, NULL, screen->backBuffer,
		                  (i32)screen->size.w*sizeof(i32));
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
