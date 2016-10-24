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
    // NOTE(doyle): By implementing the BaseColumns interface, your inner class can inherit a
    // primary key field called _ID that some Android classes such as cursor adaptors will
    // expect it to have. It's not required, but this can help your database work harmoniously
    // with the Android framework.
    // public static final String COLUMN_NAME_ID = "_ID";
    static class Entry implements BaseColumns {
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

        static final String KEY_KNOWN_SIZE_IN_KB = "last_known_size";
    }

    static final String DB_NAME = "Amber.db";
    private static final int DB_VERSION = 2;
    private static final String DB_TABLE_CREATE =
        "CREATE TABLE " + Entry.TABLE_NAME + " ("
                + Entry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Entry.KEY_PATH             + " TEXT, "
                + Entry.KEY_ALBUM            + " TEXT, "
                + Entry.KEY_ALBUM_ARTIST     + " TEXT, "
                + Entry.KEY_ARTIST           + " TEXT, "
                + Entry.KEY_AUTHOR           + " TEXT, "
                + Entry.KEY_BITRATE          + " INTEGER, "
                + Entry.KEY_CD_TRACK_NUMBER  + " INTEGER, "
                + Entry.KEY_COMPOSER         + " TEXT, "
                + Entry.KEY_DATE             + " TEXT, "
                + Entry.KEY_DISC_NUMBER      + " INTEGER, "
                + Entry.KEY_DURATION         + " INTEGER, "
                + Entry.KEY_GENRE            + " TEXT, "
                + Entry.KEY_TITLE            + " TEXT, "
                + Entry.KEY_WRITER           + " TEXT, "
                + Entry.KEY_YEAR             + " INTEGER,"
                + Entry.KEY_KNOWN_SIZE_IN_KB + " INTEGER"
                + ")";


    private static AudioDatabase handle;
    static synchronized AudioDatabase getHandle(Context context) {
        if (handle == null) {
            handle = new AudioDatabase(context);
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

    synchronized void insertAudioFileToDb(final PlaybackData.AudioFile file) {
        if (Debug.CAREFUL_ASSERT(file != null, this, "File is null")) {

            if (Debug.CAREFUL_ASSERT(file.dbKey == -1, this,
                    "Inserting new audio files must not have a db key already defined: "
                            + file.dbKey)) {

                // NOTE(doyle): This gets cached according to docs, so okay to open every time
                SQLiteDatabase db = getWritableDatabase();
                if (Debug.CAREFUL_ASSERT(db != null, this,
                        "Could not get writable database")) {

                    ContentValues value = serialiseAudioFileToContentValues(file);
                    long dbKey = db.insert(Entry.TABLE_NAME, null, value);

                    Debug.CAREFUL_ASSERT(dbKey != -1, this,
                            "Could not insert audio file to db " + file.uri.getPath());

                    file.dbKey = dbKey;
                    db.close();
                }
            }
        }
    }

    void insertMultiAudioFileToDb(final List<PlaybackData.AudioFile> files) {
        if (Debug.CAREFUL_ASSERT(files != null, this, "List of files is null")) {
            for (PlaybackData.AudioFile audio: files) insertAudioFileToDb(audio);
        }
    }

    private ContentValues serialiseAudioFileToContentValues(AudioFile file)  {
        ContentValues result = new ContentValues();
        if (file.dbKey > 0) {
            result.put(Entry._ID, file.dbKey);
        }

        result.put(Entry.KEY_PATH, file.uri.getPath());
        result.put(Entry.KEY_ALBUM, file.album);
        result.put(Entry.KEY_ALBUM_ARTIST, file.albumArtist);
        result.put(Entry.KEY_ARTIST, file.artist);
        result.put(Entry.KEY_AUTHOR, file.author);
        result.put(Entry.KEY_BITRATE, file.bitrate);
        result.put(Entry.KEY_CD_TRACK_NUMBER, file.cdTrackNumber);
        result.put(Entry.KEY_COMPOSER, file.composer);
        result.put(Entry.KEY_DATE, file.date);
        result.put(Entry.KEY_DISC_NUMBER, file.discNumber);
        result.put(Entry.KEY_DURATION, file.duration);
        result.put(Entry.KEY_GENRE, file.genre);
        result.put(Entry.KEY_TITLE, file.title);
        result.put(Entry.KEY_WRITER, file.writer);
        result.put(Entry.KEY_YEAR, file.year);
        result.put(Entry.KEY_KNOWN_SIZE_IN_KB, file.sizeInKb);

        return result;
    }

    void updateAudioFileInDbWithKey(AudioFile file) {

        if (file == null) {
            Debug.LOG_W(this, "Attempted to update db with null file");
            return;
        }

        if (Debug.CAREFUL_ASSERT(file.dbKey > 0, this, "Cannot insert audio file with < 0 " +
                "primary key: " + file.dbKey)) {
            SQLiteDatabase db = this.getWritableDatabase();
            if (Debug.CAREFUL_ASSERT(db != null, this, "Could not get readable database")) {
                ContentValues value = serialiseAudioFileToContentValues(file);
                int result = db.update(Entry.TABLE_NAME, value, Entry._ID + " = " + file.dbKey, null);

                Debug.CAREFUL_ASSERT(result == 1, this,
                        "Db update affected more/less than one row: " + result);
            }

        }
    }

    enum CheckResult {
        DB_OPEN_FAILED,
        EXISTS,
        NOT_EXIST,
    }

    class EntryCheckResult {
        CheckResult result;
        List<AudioFile> entries;
    }

    void deleteAudioFileFromDbWithKey(long key) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(Entry.TABLE_NAME, Entry._ID + " = " + key, null);

        Debug.CAREFUL_ASSERT(result == 1, this, "Db deleted more/less than one row: "
                + result);
    }

    // NOTE: WhereClause is where you specify what fields, to check against, any "?" get replaced
    // the arguments in whereArgs.
    // String whereClause = field + " = ? ";
    // String whereArgs[] = new String[] { value };
    synchronized EntryCheckResult checkIfExistFromFieldWithValue(String whereClause,
                                                                 String[] whereArgs) {
        final SQLiteDatabase db = this.getReadableDatabase();

        EntryCheckResult entryCheck = new EntryCheckResult();
        entryCheck.result = CheckResult.DB_OPEN_FAILED;
        entryCheck.entries = new ArrayList<>();

        if (Debug.CAREFUL_ASSERT(db != null, this, "Could not get readable database handle")) {
            String tableName = Entry.TABLE_NAME;
            String tableColumns[] = null;
            String groupBy = null;
            String having = null;
            String orderBy = null;

            Cursor cursor = db.query(tableName, tableColumns, whereClause, whereArgs,
                    groupBy, having, orderBy);
            cursor.moveToFirst();
            entryCheck.result = CheckResult.EXISTS;
            while (!cursor.isAfterLast()) {
                AudioFile file = convertDbEntryToAudioFile(cursor);
                entryCheck.entries.add(file);
                cursor.moveToNext();
            }

            if (entryCheck.entries.size() == 0) entryCheck.result = CheckResult.NOT_EXIST;
            cursor.close();
            db.close();
        }

        return entryCheck;
    }

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
            Entry.KEY_YEAR,
            Entry.KEY_KNOWN_SIZE_IN_KB
    };

    private AudioFile convertDbEntryToAudioFile(Cursor cursor) {
        int cursorIndex = 0;

        // NOTE(doyle): Since DB primary keys start from 1
        int dbKey = (cursor.getInt(cursorIndex++));
        Uri uri = Uri.fromFile(new File(cursor.getString(cursorIndex++)));

        String album          = cursor.getString(cursorIndex++);
        String albumArtist    = cursor.getString(cursorIndex++);
        String artist         = cursor.getString(cursorIndex++);
        String author         = cursor.getString(cursorIndex++);
        int bitrate           = cursor.getInt(cursorIndex++);
        String cdTrackNumber  = cursor.getString(cursorIndex++);
        String composer       = cursor.getString(cursorIndex++);
        String date           = cursor.getString(cursorIndex++);
        String discNumber     = cursor.getString(cursorIndex++);
        int duration          = cursor.getInt(cursorIndex++);
        String genre          = cursor.getString(cursorIndex++);
        String title          = cursor.getString(cursorIndex++);
        String writer         = cursor.getString(cursorIndex++);
        String year           = cursor.getString(cursorIndex++);
        long sizeInKb         = cursor.getLong(cursorIndex++);

        Debug.CAREFUL_ASSERT(cursorIndex == projection.length, this,
                "Cursor index exceeded projection bounds");

        AudioFile file = new AudioFile(dbKey, uri, album, albumArtist, artist, author, bitrate,
                cdTrackNumber, composer, date, discNumber, duration, genre, title, writer,
                year, sizeInKb);
        return file;
    }

    ArrayList<PlaybackData.AudioFile> getAllAudioFiles() {
        SQLiteDatabase db = this.getReadableDatabase();
        ArrayList<PlaybackData.AudioFile> result = null;

        if (Debug.CAREFUL_ASSERT(db != null, this, "Could not get readable database")) {
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
                AudioFile file = convertDbEntryToAudioFile(cursor);
                result.add(file);
                cursor.moveToNext();
            }
            cursor.close();
            db.close();
        }

        return result;
    }
}
