#include "txt_math.h"

inline v2 addV2(v2 a, v2 b) {
	v2 result;
	result.x = a.x + b.x;
	result.y = a.y + b.y;
	return result;
}

inline v2 subV2(v2 a, v2 b) {
	v2 result;
	result.x = a.x - b.x;
	result.y = a.y - b.y;
	return result;
}

inline v2 mulV2(v2 a, v2 b) {
	v2 result;
	result.x = a.x * b.x;
	result.y = a.y * b.y;
	return result;
}

inline v2 convertRawPosToVec2(u32 pos, u32 width) {
	v2 result = {0};
	result.x = (r32)(pos % width);
	result.y = (r32)(pos / width);
	return result;
}

inline u32 convertVec2ToRawPos(v2 pos, u32 width) {
	u32 result;
	result = (u32)((r32)width * pos.y);
	result += (u32)pos.x;
	return result;
}
