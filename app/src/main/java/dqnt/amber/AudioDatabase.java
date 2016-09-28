package dqnt.amber;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by Doyle on 29/09/2016.
 */

public class AudioDatabase extends SQLiteOpenHelper {
    // NOTE(doyle): By implementing the BaseColumns interface, your inner class can inherit a
    // primary key field called _ID that some Android classes such as cursor adaptors will
    // expect it to have. It's not required, but this can help your database work harmoniously
    // with the Android framework.
    // public static final String COLUMN_NAME_ID = "_ID";
    public static class Entry implements BaseColumns {
        public static final String DB_NAME = "Amber";
        public static final String TABLE_NAME = "AudioFile";
        public static final String KEY_PATH = "file_path";
        public static final String KEY_TITLE = "title";
        public static final String KEY_ARTIST = "artist";
        public static final String KEY_YEAR = "year";
    }

    private static final int DB_VERSION = 1;
    private static final String DB_TABLE_CREATE =
        "CREATE TABLE " + Entry.TABLE_NAME + " (" + Entry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                                               + Entry.KEY_PATH + " TEXT, "
                                               + Entry.KEY_ARTIST + " TEXT, "
                                               + Entry.KEY_TITLE  + " TEXT, "
                                               + Entry.KEY_YEAR   + " TEXT, "
                                               + ")";
        // "CREATE TABLE " + DATABASE_TABLE_NAME + " (" + KEY_WORD + " TEXT, " + KEY_DEFINITION + " TEXT);";

    public AudioDatabase(Context context) {
        super(context, Entry.DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(DB_TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        /* Create tables again */
        db.execSQL("DROP TABLE IF EXISTS " + Entry.DB_NAME);
        onCreate(db);
    }

    public void insertAudioFileToDb(AudioFile files[]) {
        SQLiteDatabase db = this.getWritableDatabase();

        for (AudioFile file: files) {
            ContentValues value = new ContentValues();
            value.put(Entry.KEY_PATH, file.uri.getPath());
            value.put(Entry.KEY_TITLE, file.title);
            value.put(Entry.KEY_ARTIST, file.artist);
            value.put(Entry.KEY_YEAR, file.year);

            db.insert(Entry.TABLE_NAME, null, value);
        }

        db.close();
    }

    private final String[] projection = {Entry.KEY_PATH, Entry.KEY_ARTIST, Entry.KEY_TITLE,
                                         Entry.KEY_YEAR};
    public ArrayList<AudioFile> getAllAudioFiles() {

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(Entry.TABLE_NAME,
                                 projection,
                                 null,
                                 null,
                                 null,
                                 null,
                                 null);

        int audioId = 0;
        ArrayList<AudioFile> result = new ArrayList<>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            int id = cursor.getInt(0);
            Uri uri = Uri.fromFile(new File(cursor.getString(1)));

            String tmp = "Unknown";
            String album = tmp;
            String albumArtist = tmp;
            String artist = cursor.getString(2);
            String author = tmp;
            String bitrate = tmp;
            String cdTrackNumber = tmp;
            String composer = tmp;
            String date = tmp;
            String discNumber = tmp;
            String duration = tmp;
            String genre = tmp;
            String title = cursor.getString(3);
            String writer = tmp;
            String year = cursor.getString(4);
            AudioFile file = new AudioFile(id, uri, album, albumArtist, artist, author, bitrate,
                    cdTrackNumber, composer, date, discNumber, duration, genre, title, writer, year);
            result.add(file);
        }

        return result;
    }
}
