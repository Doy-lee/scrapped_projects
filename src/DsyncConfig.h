#ifndef DSYNC_CONFIG_H
#define DSYNC_CONFIG_H

#include "Dsync.h"

typedef struct DqnIni  DqnIni;
typedef struct DqnFile DqnFile;
typedef struct DqnPushBuffer DqnPushBuffer;

extern const char *const GLOBAL_INI_SECTION_WATCH_LOCATIONS;
extern const char *const GLOBAL_INI_SECTION_BACKUP_TO_LOCATIONS;
extern const char *const GLOBAL_INI_PROPERTY_LOCATION;

// Writes an ini file to disk using "ini" and temporary memory from "buffer"
void dsync_config_write_to_disk(const DqnIni *const ini, DqnPushBuffer *const buffer);

// Does heavylifting to load the config file into internal representation in one function
// Returns a DsyncLocation with allocated memory from buffer permanently
DsyncLocations dsync_config_load(DqnPushBuffer *const buffer);

// Pass in a push buffer "buffer" to allocate temp memory from and a "fileHandle" to fill out with the config file.
// Returns the "ini" struct filled with the data or NULL if invalid args.
// config..close() is a helper that closes the handles/ini for you.
DqnIni *const  dsync_config_load_to_ini      (DqnPushBuffer *const buffer, const bool needWritePermission, DqnFile *const fileHandle);
void           dsync_config_load_to_ini_close(DqnIni *const ini, DqnFile *const fileHandle);

#endif
