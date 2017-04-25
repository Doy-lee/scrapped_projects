package com.dqnt.amber;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.File;
import java.util.List;

import com.dqnt.amber.Models.AudioFile;

class Util {
    private static final Class DEBUG_TAG = Util.class;
    private static String extractAudioKeyData(MediaMetadataRetriever retriever, int key) {
        String field = retriever.extractMetadata(key);
        if (field == null) field = "Unknown";
        return field;
    }

    static boolean flagIsSet(int value, int flag) {
        boolean result = false;
        if ((value & flag) == flag) {
            result = true;
        }

        return result;
    }


    static AudioFile extractAudioMetadata(Context context, MediaMetadataRetriever mmr,
                                          Uri uri) {

        if (uri == null) return null;
        if (mmr == null) return null;
        if (context == null) return null;

        mmr.setDataSource(context, uri);

        // NOTE(doyle): API
        // developer.android.com/reference/android/media/MediaMetadataRetriever.html
        String album = extractAudioKeyData(mmr, MediaMetadataRetriever.METADATA_KEY_ALBUM);
        String albumArtist = extractAudioKeyData(mmr,
                MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
        String artist = extractAudioKeyData(mmr, (MediaMetadataRetriever.METADATA_KEY_ARTIST));
        String author = extractAudioKeyData(mmr, MediaMetadataRetriever.METADATA_KEY_AUTHOR);

        String bitrateStr = extractAudioKeyData(mmr, MediaMetadataRetriever.METADATA_KEY_BITRATE);
        int bitrate = 0;
        if (bitrateStr.compareTo("Unknown") != 0) {
            bitrate = Integer.parseInt(bitrateStr);
        }
        String cdTrackNum = extractAudioKeyData
                (mmr, MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
        String composer = extractAudioKeyData(mmr, MediaMetadataRetriever.METADATA_KEY_COMPOSER);
        String date = extractAudioKeyData(mmr, MediaMetadataRetriever.METADATA_KEY_DATE);
        String discNum = extractAudioKeyData(mmr, MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER);

        String durationStr = extractAudioKeyData(mmr, MediaMetadataRetriever.METADATA_KEY_DURATION);
        int duration = 0;
        if (durationStr.compareTo("Unknown") != 0) {
            duration = Integer.parseInt(durationStr);
        }

        String genre = extractAudioKeyData(mmr, MediaMetadataRetriever.METADATA_KEY_GENRE);
        String title = extractAudioKeyData(mmr, MediaMetadataRetriever.METADATA_KEY_TITLE);
        String writer = extractAudioKeyData(mmr, MediaMetadataRetriever.METADATA_KEY_WRITER);
        String year = extractAudioKeyData(mmr, MediaMetadataRetriever.METADATA_KEY_YEAR);

        File file = new File(uri.getPath());
        long sizeInKb =  (file.length() / 1024);
        Debug.LOG_V(DEBUG_TAG, "File: " + file.getName() + ", Parsed audio: " + artist +  " - " + title);

        byte[] coverArt = mmr.getEmbeddedPicture();
        Bitmap bitmap = null;
        if (coverArt != null) {
            bitmap = BitmapFactory.decodeByteArray(coverArt, 0, coverArt.length);
        }

        AudioFile result = new AudioFile(Models.AUDIO_NOT_IN_DB,
                                         uri,

                                         // TODO(doyle): Revise how to store bitmap stuff
                                         bitmap,
                                         null,

                                         album,
                                         albumArtist,
                                         artist,
                                         author,
                                         bitrate,
                                         cdTrackNum,
                                         composer,
                                         date,
                                         discNum,
                                         duration,
                                         genre,
                                         title,
                                         writer,
                                         year,
                                         sizeInKb);

        return result;
    }
    static void getFilesFromDirRecursive(List<File> list, File root) {
        File[] dir = root.listFiles();
        for (File file : dir) {
            if (file.isDirectory()) {
                getFilesFromDirRecursive(list, file);
            } else {
                list.add(file);
            }
        }
    }
}
