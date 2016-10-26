package com.dqnt.amber;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.dqnt.amber.PlaybackData.AudioFile;
import com.dqnt.amber.PlaybackData.Playlist;

class AudioDatabase extends SQLiteOpenHelper {
    // NOTE(doyle): By implementing the BaseColumns interface, your inner class can inherit a
    // primary key field called _ID that some Android classes such as cursor adaptors will
    // expect it to have. It's not required, but this can help your database work harmoniously
    // with the Android framework.
    // public static final String COLUMN_NAME_ID = "_ID";
    class AudioFileEntry implements BaseColumns {
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

    private class PlaylistEntry implements BaseColumns {
        static final String TABLE_NAME          = "Playlist";
        static final String KEY_NAME            = "name";
        static final String KEY_PATH            = "file_path";
    }

    private class PlaylistContentsEntry implements BaseColumns {
        static final String TABLE_NAME                     = "PlaylistContent";
        static final String KEY_FOREIGN_KEY_TO_PLAYLIST    = "foreign_key_to_playlist";
        static final String KEY_FOREIGN_KEY_TO_AUDIO_FILE  = "foreign_key_to_audio_file";
    }

    static final String DB_NAME = "Amber.db";
    private static final int DB_VERSION = 4;
    private static final String[] DB_CREATE_COMMANDS = {
            // Audio File Table
            "CREATE TABLE " + AudioFileEntry.TABLE_NAME + " ("
                    + AudioFileEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + AudioFileEntry.KEY_PATH             + " TEXT, "
                    + AudioFileEntry.KEY_ALBUM            + " TEXT, "
                    + AudioFileEntry.KEY_ALBUM_ARTIST     + " TEXT, "
                    + AudioFileEntry.KEY_ARTIST           + " TEXT, "
                    + AudioFileEntry.KEY_AUTHOR           + " TEXT, "
                    + AudioFileEntry.KEY_BITRATE          + " INTEGER, "
                    + AudioFileEntry.KEY_CD_TRACK_NUMBER  + " INTEGER, "
                    + AudioFileEntry.KEY_COMPOSER         + " TEXT, "
                    + AudioFileEntry.KEY_DATE             + " TEXT, "
                    + AudioFileEntry.KEY_DISC_NUMBER      + " INTEGER, "
                    + AudioFileEntry.KEY_DURATION         + " INTEGER, "
                    + AudioFileEntry.KEY_GENRE            + " TEXT, "
                    + AudioFileEntry.KEY_TITLE            + " TEXT, "
                    + AudioFileEntry.KEY_WRITER           + " TEXT, "
                    + AudioFileEntry.KEY_YEAR             + " INTEGER, "
                    + AudioFileEntry.KEY_KNOWN_SIZE_IN_KB + " INTEGER"
                    + ")",

            // Playlist File Table
            "CREATE TABLE " + PlaylistEntry.TABLE_NAME + " ("
                    + PlaylistEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + PlaylistEntry.KEY_PATH             + " TEXT, "
                    + PlaylistEntry.KEY_NAME             + " TEXT "
                    + ")",

            // Playlist Contents Table
            "CREATE TABLE " + PlaylistContentsEntry.TABLE_NAME + " ("
                    + PlaylistContentsEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + PlaylistContentsEntry.KEY_FOREIGN_KEY_TO_PLAYLIST + " INTEGER, "
                    + PlaylistContentsEntry.KEY_FOREIGN_KEY_TO_AUDIO_FILE + " INTEGER, "

                    + "FOREIGN KEY (" + PlaylistContentsEntry.KEY_FOREIGN_KEY_TO_PLAYLIST + ") REFERENCES "
                    + PlaylistEntry.TABLE_NAME + " (" + PlaylistEntry._ID + "), "

                    + "FOREIGN KEY (" + PlaylistContentsEntry.KEY_FOREIGN_KEY_TO_AUDIO_FILE + ") REFERENCES "
                    + AudioFileEntry.TABLE_NAME + " (" + AudioFileEntry._ID + ")"

                    + ")",
    };

    private final String[] AUDIO_FILE_PROJECTION = {
            AudioFileEntry._ID,
            AudioFileEntry.KEY_PATH,
            AudioFileEntry.KEY_ALBUM,
            AudioFileEntry.KEY_ALBUM_ARTIST,
            AudioFileEntry.KEY_ARTIST,
            AudioFileEntry.KEY_AUTHOR,
            AudioFileEntry.KEY_BITRATE,
            AudioFileEntry.KEY_CD_TRACK_NUMBER,
            AudioFileEntry.KEY_COMPOSER,
            AudioFileEntry.KEY_DATE,
            AudioFileEntry.KEY_DISC_NUMBER,
            AudioFileEntry.KEY_DURATION,
            AudioFileEntry.KEY_GENRE,
            AudioFileEntry.KEY_TITLE,
            AudioFileEntry.KEY_WRITER,
            AudioFileEntry.KEY_YEAR,
            AudioFileEntry.KEY_KNOWN_SIZE_IN_KB,
    };

    private final String[] PLAYLIST_PROJECTION = {
            PlaylistEntry._ID,
            PlaylistEntry.KEY_NAME,
            PlaylistEntry.KEY_PATH
    };

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
        for (String command : DB_CREATE_COMMANDS) db.execSQL(command);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        /* Create tables again */
        db.execSQL("DROP TABLE IF EXISTS " + AudioFileEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + PlaylistEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + PlaylistContentsEntry.TABLE_NAME);
        onCreate(db);
    }

    /***********************************************************************************************
     * AUDIO FILE FUNCTIONS
     **********************************************************************************************/
    // TODO(doyle): Synchronisation problem on all db manipulation methods, since all tables sharing same db
    synchronized void insertAudioFileToDb(final PlaybackData.AudioFile file) {
        if (Debug.CAREFUL_ASSERT(file != null, this, "File is null")) {
            if (Debug.CAREFUL_ASSERT(file.dbKey == -1, this,
                    "Inserting new audio files must not have a db key already defined: "
                            + file.dbKey)) {

                // NOTE(doyle): This gets cached according to docs, so okay to open every time
                SQLiteDatabase db = getWritableDatabase();
                if (Debug.CAREFUL_ASSERT(db != null, this, "Could not get writable database")) {

                    ContentValues value = serialiseAudioFileToContentValues(file);
                    long dbKey = db.insert(AudioFileEntry.TABLE_NAME, null, value);

                    Debug.CAREFUL_ASSERT(dbKey != -1, this,
                            "Could not insert audio file to db " + file.uri.getPath());

                    file.dbKey = dbKey;
                    db.close();
                }
            }
        }
    }

    private ContentValues serialiseAudioFileToContentValues(AudioFile file)  {
        ContentValues result = new ContentValues();
        if (file.dbKey > 0) {
            result.put(AudioFileEntry._ID, file.dbKey);
        }

        result.put(AudioFileEntry.KEY_PATH, file.uri.getPath());
        result.put(AudioFileEntry.KEY_ALBUM, file.album);
        result.put(AudioFileEntry.KEY_ALBUM_ARTIST, file.albumArtist);
        result.put(AudioFileEntry.KEY_ARTIST, file.artist);
        result.put(AudioFileEntry.KEY_AUTHOR, file.author);
        result.put(AudioFileEntry.KEY_BITRATE, file.bitrate);
        result.put(AudioFileEntry.KEY_CD_TRACK_NUMBER, file.cdTrackNumber);
        result.put(AudioFileEntry.KEY_COMPOSER, file.composer);
        result.put(AudioFileEntry.KEY_DATE, file.date);
        result.put(AudioFileEntry.KEY_DISC_NUMBER, file.discNumber);
        result.put(AudioFileEntry.KEY_DURATION, file.duration);
        result.put(AudioFileEntry.KEY_GENRE, file.genre);
        result.put(AudioFileEntry.KEY_TITLE, file.title);
        result.put(AudioFileEntry.KEY_WRITER, file.writer);
        result.put(AudioFileEntry.KEY_YEAR, file.year);
        result.put(AudioFileEntry.KEY_KNOWN_SIZE_IN_KB, file.sizeInKb);

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
                int result = db.update(AudioFileEntry.TABLE_NAME, value, AudioFileEntry._ID + " = " + file.dbKey, null);

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
        int result = db.delete(AudioFileEntry.TABLE_NAME, AudioFileEntry._ID + " = " + key, null);

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
            String tableName = AudioFileEntry.TABLE_NAME;
            String tableColumns[] = null;
            String groupBy = null;
            String having = null;
            String orderBy = null;

            Cursor cursor = db.query(tableName, tableColumns, whereClause, whereArgs,
                    groupBy, having, orderBy);
            cursor.moveToFirst();
            entryCheck.result = CheckResult.EXISTS;
            while (!cursor.isAfterLast()) {
                AudioFile file = convertCursorEntryToAudioFile(cursor);
                entryCheck.entries.add(file);
                cursor.moveToNext();
            }

            if (entryCheck.entries.size() == 0) entryCheck.result = CheckResult.NOT_EXIST;
            cursor.close();
            db.close();
        }

        return entryCheck;
    }

    private AudioFile convertCursorEntryToAudioFile(Cursor cursor) {
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

        Debug.CAREFUL_ASSERT(cursorIndex == AUDIO_FILE_PROJECTION.length, this,
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
            Cursor cursor = db.query(AudioFileEntry.TABLE_NAME,
                    AUDIO_FILE_PROJECTION,
                    null,
                    null,
                    null,
                    null,
                    null);

            result = new ArrayList<>();
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                AudioFile file = convertCursorEntryToAudioFile(cursor);
                result.add(file);
                cursor.moveToNext();
            }
            cursor.close();
            db.close();
        }

        return result;
    }

    private AudioFile getAudioFileEntryFromKey(SQLiteDatabase db, int key) {
        AudioFile result = null;

        if (db == null) {
            Debug.CAREFUL_ASSERT(false, this, "Db argument was null");
            return result;
        }

        String tableName = AudioFileEntry.TABLE_NAME;
        String tableColumns[] = null;
        String whereClause = AudioFileEntry._ID + " = ? ";
        String whereArgs[] = new String[] { String.valueOf(key) };
        String groupBy = null;
        String having = null;
        String orderBy = null;

        Cursor cursor = db.query(tableName, tableColumns, whereClause, whereArgs, groupBy, having,
                orderBy);
        cursor.moveToFirst();
        result = convertCursorEntryToAudioFile(cursor);
        cursor.close();

        return result;
    }

    /***********************************************************************************************
     * PLAYLIST FUNCTIONS
     **********************************************************************************************/

    private ContentValues serialisePlaylistToContentValues(Playlist playlist) {
        ContentValues result = new ContentValues();
        result.put(PlaylistEntry.KEY_PATH, playlist.uri.getPath());
        result.put(PlaylistEntry.KEY_NAME, playlist.name);

        return result;
    }

    synchronized void insertPlaylistFileToDb(Playlist playlist) {
        if (Debug.CAREFUL_ASSERT(playlist != null, this, "Playlist is null")) {
            SQLiteDatabase db = getWritableDatabase();

            if (Debug.CAREFUL_ASSERT(db != null, this, "Could not get writable database")) {
                ContentValues playlistValue = serialisePlaylistToContentValues(playlist);
                long playlistDbKey = db.insert(PlaylistEntry.TABLE_NAME, null, playlistValue);

                if (playlistDbKey == -1)  {
                    Debug.CAREFUL_ASSERT(false, this,
                            "Playlist could not be inserted to db exiting early");
                    return;
                }

                List<AudioFile> playlistContents = playlist.contents;
                for (AudioFile file: playlistContents) {
                    if (Debug.CAREFUL_ASSERT(file.dbKey != -1, this,
                            "Playlist entry has no db entry " + file.uri.getPath())) {
                        ContentValues playlistFileValue = new ContentValues();
                        playlistFileValue.put
                                (PlaylistContentsEntry.KEY_FOREIGN_KEY_TO_AUDIO_FILE, file.dbKey);
                        playlistFileValue.put
                                (PlaylistContentsEntry.KEY_FOREIGN_KEY_TO_PLAYLIST, playlistDbKey);

                        long key =
                                db.insert(PlaylistContentsEntry.TABLE_NAME, null, playlistFileValue);

                        if (key == -1) {
                            Debug.CAREFUL_ASSERT(false, this,
                                    "Playlist entry could not be inserted to db."
                                            + file.uri.getPath());
                        }
                    }
                }

                db.close();
            }

        }
    }

    ArrayList<Playlist> getAllPlaylists() {
        SQLiteDatabase db = this.getReadableDatabase();
        ArrayList<Playlist> result = null;

        if (Debug.CAREFUL_ASSERT(db != null, this, "Could not get readable database")) {
            Cursor cursor = db.query(PlaylistEntry.TABLE_NAME, PLAYLIST_PROJECTION,
                    null, null, null, null, null);

            result = new ArrayList<>();
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                int cursorIndex = 0;

                int playlistKey = cursor.getInt(cursorIndex++);
                String name = cursor.getString(cursorIndex++);
                Uri uri = Uri.fromFile(new File(cursor.getString(cursorIndex++)));

                Debug.CAREFUL_ASSERT(cursorIndex == PLAYLIST_PROJECTION.length, this,
                        "Cursor index exceeded projection bounds");

                Playlist playlist = new Playlist(name, uri);
                playlist.contents = getPlaylistContents(db, playlistKey);
                result.add(playlist);
                cursor.moveToNext();
            }

            cursor.close();
        }

        return result;
    }

    private List<AudioFile> getPlaylistContents(SQLiteDatabase db, int playlistKey) {
        List<AudioFile> result = null;
        if (db == null) {
            Debug.CAREFUL_ASSERT(false, this, "Db argument was null");
            return result;
        }

        String tableName = PlaylistContentsEntry.TABLE_NAME;
        String tableColumns[] = new String[]
                { PlaylistContentsEntry.KEY_FOREIGN_KEY_TO_AUDIO_FILE };
        String whereClause = PlaylistContentsEntry.KEY_FOREIGN_KEY_TO_PLAYLIST + " = ? ";
        String whereArgs[] = new String[] { String.valueOf(playlistKey) };
        String groupBy = null;
        String having = null;
        String orderBy = null;
        Cursor cursor =
                db.query(tableName, tableColumns, whereClause, whereArgs, groupBy, having, orderBy);
        cursor.moveToFirst();

        result = new ArrayList<>();
        while (!cursor.isAfterLast()) {
            int audioKey = cursor.getInt(0);
            AudioFile file = getAudioFileEntryFromKey(db, audioKey);
            if (file != null) result.add(file);
            cursor.moveToNext();
        }

        cursor.close();
        return result;
    }
}
