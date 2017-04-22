#ifndef DSYNC_CONSOLE_H
#define DSYNC_CONSOLE_H

#include "dqn.h"

// Writes to the associated console if possible
// Pass in "string" and optionally "len" of the string. If left to -1, strlen is used
void dsync_console_write      (const wchar_t *const string, i32 len = -1);

// Handle arguments passed in as the form of argc, argv like a CLI application
void dsync_console_handle_args(const i32 argc, wchar_t **const argv);

#endif
