# PlaylistSyncer
An initial CLI approach to sync playlist files to a destination (and eventually MTP devices). It looks for files specified in supported playlists and copies over to the designated destination.

Currently supported files
* m3u

## Usage
PlaylistSyncer <playlist file> <destination directory>

### Example

PlaylistSyncer music.m3u myMusic

Will copy music referenced in music.m3u to a "myMusic" folder in the current directory.
