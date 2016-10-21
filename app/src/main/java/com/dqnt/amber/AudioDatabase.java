package com.dqnt.amber;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.dqnt.amber.PlaybackData.AudioFile;

class AudioDatabase extends SQLiteOpenHelper {
    private static final Class ASSERT_TAG = AudioDatabase.class;

    // NOTE(doyle): By implementing the BaseColumns interface, your inner class can inherit a
    // primary key field called _ID that some Android classes such as cursor adaptors will
    // expect it to have. It's not required, but this can help your database work harmoniously
    // with the Android framework.
    // public static final String COLUMN_NAME_ID = "_ID";
    private static class Entry implements BaseColumns {
        static final String TABLE_NAME          = "AudioFile";
        static final String KEY_PATH            = "file_path";
        static final String KEY_ALBUM           = "album";
        static final String KEY_ALBUM_ARTIST    = "album_artist";
        static final String KEY_ARTIST          = "artist";
        static final String KEY_AUTHOR          = "author";
        static final String KEY_BITRATE         = "bitrate";
        static final String KEY_CD_TRACK_NUMBER = "cd_track_number";
        static final String KEY_COMPOSER        = "composer";
        static final String KEY_DATE            = "date";
        static final String KEY_DISC_NUMBER     = "disc_number";
        static final String KEY_DURATION        = "duration";
        static final String KEY_GENRE           = "genre";
        static final String KEY_TITLE           = "title";
        static final String KEY_WRITER          = "writer";
        static final String KEY_YEAR            = "year";
    }

    static final String DB_NAME = "Amber.db";
    private static final int DB_VERSION = 1;
    private static final String DB_TABLE_CREATE =
        "CREATE TABLE " + Entry.TABLE_NAME + " ("
                + Entry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Entry.KEY_PATH            + " TEXT, "
                + Entry.KEY_ALBUM           + " TEXT, "
                + Entry.KEY_ALBUM_ARTIST    + " TEXT, "
                + Entry.KEY_ARTIST          + " TEXT, "
                + Entry.KEY_AUTHOR          + " TEXT, "
                + Entry.KEY_BITRATE         + " INTEGER, "
                + Entry.KEY_CD_TRACK_NUMBER + " INTEGER, "
                + Entry.KEY_COMPOSER        + " TEXT, "
                + Entry.KEY_DATE            + " TEXT, "
                + Entry.KEY_DISC_NUMBER     + " INTEGER, "
                + Entry.KEY_DURATION        + " INTEGER, "
                + Entry.KEY_GENRE           + " TEXT, "
                + Entry.KEY_TITLE           + " TEXT, "
                + Entry.KEY_WRITER          + " TEXT, "
                + Entry.KEY_YEAR            + " INTEGER"
                + ")";


    private static AudioDatabase handle;
    static synchronized AudioDatabase getHandle(Context appContext) {
        if (handle == null) {
            handle = new AudioDatabase(appContext);
        }

        return handle;
    }

    private AudioDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(DB_TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        /* Create tables again */
        db.execSQL("DROP TABLE IF EXISTS " + DB_NAME);
        onCreate(db);
    }

    void insertAudioFileToDb(final PlaybackData.AudioFile file) {
        if (Debug.CAREFUL_ASSERT(file != null, ASSERT_TAG, "File is null")) {

            // NOTE(doyle): This gets cached according to docs, so okay to open every time
            SQLiteDatabase db = getWritableDatabase();
            if (Debug.CAREFUL_ASSERT(db != null, ASSERT_TAG, "Could not get writable database")) {
                ContentValues value = new ContentValues();
                value.put(Entry.KEY_PATH, file.uri.getPath());
                value.put(Entry.KEY_ALBUM, file.album);
                value.put(Entry.KEY_ALBUM_ARTIST, file.albumArtist);
                value.put(Entry.KEY_ARTIST, file.artist);
                value.put(Entry.KEY_AUTHOR, file.author);
                value.put(Entry.KEY_BITRATE, file.bitrate);
                value.put(Entry.KEY_CD_TRACK_NUMBER, file.cdTrackNumber);
                value.put(Entry.KEY_COMPOSER, file.composer);
                value.put(Entry.KEY_DATE, file.date);
                value.put(Entry.KEY_DISC_NUMBER, file.discNumber);
                value.put(Entry.KEY_DURATION, file.duration);
                value.put(Entry.KEY_GENRE, file.genre);
                value.put(Entry.KEY_TITLE, file.title);
                value.put(Entry.KEY_WRITER, file.writer);
                value.put(Entry.KEY_YEAR, file.year);

                db.insert(Entry.TABLE_NAME, null, value);
                db.close();
            }
        }
    }

    void insertMultiAudioFileToDb(final List<PlaybackData.AudioFile> files) {
        if (Debug.CAREFUL_ASSERT(files != null, ASSERT_TAG, "List of files is null")) {
            for (PlaybackData.AudioFile audio: files) insertAudioFileToDb(audio);
        }
    }

    ArrayList<PlaybackData.AudioFile> getAllAudioFiles() {
        SQLiteDatabase db = this.getReadableDatabase();
        ArrayList<PlaybackData.AudioFile> result = null;

        if (Debug.CAREFUL_ASSERT(db != null, ASSERT_TAG, "Could not get readable database")) {
            final String[] projection = {
                    Entry._ID,
                    Entry.KEY_PATH,
                    Entry.KEY_ALBUM,
                    Entry.KEY_ALBUM_ARTIST,
                    Entry.KEY_ARTIST,
                    Entry.KEY_AUTHOR,
                    Entry.KEY_BITRATE,
                    Entry.KEY_CD_TRACK_NUMBER,
                    Entry.KEY_COMPOSER,
                    Entry.KEY_DATE,
                    Entry.KEY_DISC_NUMBER,
                    Entry.KEY_DURATION,
                    Entry.KEY_GENRE,
                    Entry.KEY_TITLE,
                    Entry.KEY_WRITER,
                    Entry.KEY_YEAR
            };

            Cursor cursor = db.query(Entry.TABLE_NAME,
                    projection,
                    null,
                    null,
                    null,
                    null,
                    null);

            result = new ArrayList<>();
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                int cursorIndex = 0;

                // NOTE(doyle): Since DB primary keys start from 1
                int id = (cursor.getInt(cursorIndex++)) - 1;
                Uri uri = Uri.fromFile(new File(cursor.getString(cursorIndex++)));

                String album         = cursor.getString(cursorIndex++);
                String albumArtist   = cursor.getString(cursorIndex++);
                String artist        = cursor.getString(cursorIndex++);
                String author        = cursor.getString(cursorIndex++);
                int bitrate          = cursor.getInt(cursorIndex++);
                String cdTrackNumber = cursor.getString(cursorIndex++);
                String composer      = cursor.getString(cursorIndex++);
                String date          = cursor.getString(cursorIndex++);
                String discNumber    = cursor.getString(cursorIndex++);
                int duration         = cursor.getInt(cursorIndex++);
                String genre         = cursor.getString(cursorIndex++);
                String title         = cursor.getString(cursorIndex++);
                String writer        = cursor.getString(cursorIndex++);
                String year          = cursor.getString(cursorIndex++);

                Debug.CAREFUL_ASSERT(cursorIndex == projection.length, ASSERT_TAG,
                        "Cursor index exceeded projection bounds");

                AudioFile file = new AudioFile(id, uri, album, albumArtist, artist, author, bitrate,
                        cdTrackNumber, composer, date, discNumber, duration, genre, title, writer,
                        year);
                result.add(file);
                cursor.moveToNext();
            }

        }

        return result;
    }
}
