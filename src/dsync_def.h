#ifndef DSYNC_DEF_H
#define DSYNC_DEF_H

#include <stdint.h>
#include <assert.h>

#define TRUE 1
#define FALSE 0

#define Kilobytes(Value) ((Value)*1024LL)
#define Megabytes(Value) (Kilobytes(Value)*1024LL)
#define Gigabytes(Value) (Megabytes(Value)*1024LL)
#define Terabytes(Value) (Gigabytes(Value)*1024LL)

#define inline __inline

typedef int32_t i32;
typedef i32 b32;

#endif
