#ifndef WIN32_DSYNC_H
#define WIN32_DSYNC_H

#include "dqn.h"

#define WIN32_LEAN_AND_MEAN
#include <Windows.h>

// Create a unique timestamp that will take use exactly 18 characters of the form YYYY-MM-DD_HHMMSS
// Pass in an empty "buf" with length of "bufLen".
// Returns the number of characters copied into "buf" or "0" if invalid args
#define WIN32_UNIQUE_TIMESTAMP_MAX_LEN 18
u32 win32_create_unique_timestamp(char *const buf, const i32 bufLen);

// Make a path specify a directory i"f it isn't already. I.e. C:\folder\file.txt -> C:\folder\
// Pass in an empty "buffer" with length "bufferLen" and "path" to convert.
// Returns the number of characters copied into "buffer" or "0" if invalid args.
u32 win32_make_path_to_directory(const wchar_t *const path, wchar_t *const buf,
                                 const u32 bufLen);

// Get the directory which Dsync is running in.
// Pass in an empty "buffer" with length "bufferLen".
// Returns the number of characters put into the buffer or "0" if invalid args.
u32 win32_get_module_directory(wchar_t *const buf, const u32 bufLen);

// Write to the associated console, if there is one associated with the process.
// Pass in the "string" to write with length "len" and optionally pass in a dword to receive the
// number of chracters succesfully written.
// Returns false if not possible.
bool win32_console_writew (const wchar_t *const string, DWORD len, DWORD *const numWritten);
#endif
