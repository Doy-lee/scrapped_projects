package com.dqnt.amber;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

class AudioDatabase extends SQLiteOpenHelper {
    private static final String TAG = AudioDatabase.class.getName();

    // NOTE(doyle): By implementing the BaseColumns interface, your inner class can inherit a
    // primary key field called _ID that some Android classes such as cursor adaptors will
    // expect it to have. It's not required, but this can help your database work harmoniously
    // with the Android framework.
    // public static final String COLUMN_NAME_ID = "_ID";
    private static class Entry implements BaseColumns {
        static final String TABLE_NAME = "AudioFile";
        static final String KEY_PATH = "file_path";
        static final String KEY_TITLE = "title";
        static final String KEY_ARTIST = "artist";
        static final String KEY_YEAR = "year";
    }

    public static final String DB_NAME = "Amber.db";
    private static final int DB_VERSION = 1;
    private static final String DB_TABLE_CREATE =
        "CREATE TABLE " + Entry.TABLE_NAME + " (" + Entry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                                               + Entry.KEY_PATH + " TEXT, "
                                               + Entry.KEY_ARTIST + " TEXT, "
                                               + Entry.KEY_TITLE  + " TEXT, "
                                               + Entry.KEY_YEAR   + " TEXT"
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

    void insertAudioFileToDb(final AudioFile file) {
        if (Debug.CAREFUL_ASSERT(file != null, TAG, "insertAudioFileToDb(): File is null")) {

            // NOTE(doyle): This gets cached according to docs, so okay to open every time
            SQLiteDatabase db = getWritableDatabase();
            if (Debug.CAREFUL_ASSERT(db != null, TAG, "insertAudioFileToDb(): Could not get " +
                    "writable database")) {
                ContentValues value = new ContentValues();
                value.put(Entry.KEY_PATH, file.uri.getPath());
                value.put(Entry.KEY_TITLE, file.title);
                value.put(Entry.KEY_ARTIST, file.artist);
                value.put(Entry.KEY_YEAR, file.year);

                db.insert(Entry.TABLE_NAME, null, value);
                db.close();
            }
        }
    }

    void insertMultiAudioFileToDb(final List<AudioFile> files) {
        if (Debug.CAREFUL_ASSERT(files != null, TAG,
                "insertMultiAudioFileToDb(): List of files is null")) {
            for (AudioFile audio: files) insertAudioFileToDb(audio);
        }
    }

    ArrayList<AudioFile> getAllAudioFiles() {
        SQLiteDatabase db = this.getReadableDatabase();
        ArrayList<AudioFile> result = null;

        if (Debug.CAREFUL_ASSERT(db != null, TAG, "getAllAudioFiles(): Could not get readable" +
                "database")) {
            final String[] projection = {Entry._ID,
                                         Entry.KEY_PATH,
                                         Entry.KEY_ARTIST,
                                         Entry.KEY_TITLE,
                                         Entry.KEY_YEAR};

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

                String tmp = "Unknown";
                String album = tmp;
                String albumArtist = tmp;
                String artist = cursor.getString(cursorIndex++);
                String author = tmp;
                String bitrate = tmp;
                String cdTrackNumber = tmp;
                String composer = tmp;
                String date = tmp;
                String discNumber = tmp;
                String duration = tmp;
                String genre = tmp;
                String title = cursor.getString(cursorIndex++);
                String writer = tmp;
                String year = cursor.getString(cursorIndex++);

                Debug.CAREFUL_ASSERT(cursorIndex == projection.length, TAG,
                        "getAllAudioFiles(): Cursor index exceeded projection bounds");

                AudioFile file = new AudioFile(id, uri, album, albumArtist, artist, author, bitrate,
                        cdTrackNumber, composer, date, discNumber, duration, genre, title, writer, year);
                result.add(file);
                cursor.moveToNext();
            }

        }

        return result;
    }
}
