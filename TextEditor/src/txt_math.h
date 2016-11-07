#ifndef TXT_MATH_H
#define TXT_MATH_H

#include "txt_def.h"

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

extern inline v2 addV2(v2 a, v2 b);
extern inline v2 subV2(v2 a, v2 b);
extern inline v2 mulV2(v2 a, v2 b);
extern inline v2 convertRawPosToVec2(u32 pos, u32 width);
extern inline u32 convertVec2ToRawPos(v2 pos, u32 width);

#endif
