package com.dqnt.amber;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

class PlaybackData {
    static final int AUDIO_NOT_IN_DB = -1;

    static class Playlist {
        String name;
        Uri uri;
        List<AudioFile> contents;

        int menuId;

        Playlist(String name) {
            this.name = name;

            menuId = -1;
            contents = new ArrayList<>();
        }

        Playlist(String name, Uri uri) {
            this.uri = uri;
            this.name = name;

            menuId = -1;
            contents = new ArrayList<>();
        }
    }

    static class AudioFile {
        long dbKey;
        Uri uri;
        String album;
        String albumArtist;
        String artist;
        String author;
        int bitrate;
        String cdTrackNumber;
        String composer;
        String date;
        String discNumber;
        int duration;
        String genre;
        String title;
        String writer;
        String year;

        // TODO(doyle): Remove out eventually
        long sizeInKb;

        AudioFile(long dbKey,
                  Uri uri,
                  String album,
                  String albumArtist,
                  String artist,
                  String author,
                  int bitrate,
                  String cdTrackNumber,
                  String composer,
                  String date,
                  String discNumber,
                  int duration,
                  String genre,
                  String title,
                  String writer,
                  String year,
                  long sizeInKb
        ) {
            this.dbKey = dbKey;
            this.uri = uri;
            this.album = album;
            this.albumArtist = albumArtist;
            this.artist = artist;
            this.author = author;
            this.bitrate = bitrate;
            this.cdTrackNumber = cdTrackNumber;
            this.composer = composer;
            this.date = date;
            this.discNumber = discNumber;
            this.duration = duration;
            this.genre = genre;
            this.title = title;
            this.writer = writer;
            this.year = year;
            this.sizeInKb = sizeInKb;
        }
    }
}
