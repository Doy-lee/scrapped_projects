package com.dqnt.amber;

import android.net.Uri;

/**
 * Created by Doyle on 12/10/2016.
 */
class AudioFile {
    int id;
    Uri uri;
    String album;
    String albumArtist;
    String artist;
    String author;
    String bitrate;
    String cdTrackNumber;
    String composer;
    String date;
    String discNumber;
    String duration;
    String genre;
    String title;
    String writer;
    String year;

    public AudioFile(int id,
                     Uri uri,
                     String album,
                     String albumArtist,
                     String artist,
                     String author,
                     String bitrate,
                     String cdTrackNumber,
                     String composer,
                     String date,
                     String discNumber,
                     String duration,
                     String genre,
                     String title,
                     String writer,
                     String year
    ) {
        this.id = id;
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
    }
}
