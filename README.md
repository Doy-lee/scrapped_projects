# PlaylistSyncer
An initial CLI approach to sync playlist files to a destination (and eventually MTP devices). It looks for files specified in supported playlists and copies over to the designated destination. Any pre-existing audio files not belonging to the files in the playlist are deleted.

Currently supported playlists
* m3u

Audio Sync List
* ape
* flac
* m4a
* mp3
* oga
* ogg
* opus
* tta
* wav
* wma

The program will look for these files in the destination directory and remove if they aren't a part of the playlist file. Files in playlist are copied as long as they are accessible irrespective of file format.

## Usage
PlaylistSyncer \<playlist file> \<destination directory>

### Example

* PlaylistSyncer music.m3u myMusic
* PlaylistSyncer *.m3u "syncing folder"

Will copy music referenced in music.m3u to a "myMusic" folder in the current directory.

### Notes

* The program at the moment does not work with unicode characters.
* If no directory is specified, the program defaults sync to the current directory
* Note that if choosing a pre-existing directory, or shared directory any pre-existing audio files will be deleted
