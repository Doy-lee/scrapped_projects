package com.dqnt.amber;


import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import com.dqnt.amber.PlaybackData.AudioFile;
import com.dqnt.amber.PlaybackData.Playlist;

import static com.dqnt.amber.Debug.ASSERT;
import static com.dqnt.amber.Debug.CAREFUL_ASSERT;
import static com.dqnt.amber.Debug.LOG_D;

public class MainActivity extends AppCompatActivity {
    private static final int AMBER_READ_EXTERNAL_STORAGE_REQUEST = 1;
    private AudioFileAdapter audioFileAdapter;

    private static final int AMBER_VERSION = 1;

    private class PlaySpec {
        List<AudioFile> allAudioFiles;

        // NOTE(doyle): Permanent list that has all the files the app has scanned, separate from the
        // list index because we clear the playlist list on load from DB, so save having to recreate
        // a library list every time we init from DB.
        Playlist libraryList;
        List<Playlist> playlistList;

        Playlist activePlaylist;

        PlaySpec() {

            allAudioFiles = new ArrayList<>();
            playlistList = new ArrayList<>();

            libraryList = new Playlist("Library");
            libraryList.contents = allAudioFiles;

            activePlaylist = libraryList;
        }
    }
    PlaySpec playSpec;

    private AudioService audioService;
    private boolean serviceBound = false;

    private Playlist queuedPlaylist = null;
    private int queuedPlaylistIndex = -1;

    private AudioService.Response audioServiceResponse;

    /*
     ***********************************************************************************************
     * INITIALISATION CODE
     ***********************************************************************************************
     */


    // NOTE(doyle): When the Android system creates the connection between the client and service,
    // (bindService()) it calls onServiceConnected() on the ServiceConnection, to deliver the
    // IBinder that the client can use to communicate with the service. (i.e. callback).
    private ServiceConnection audioConnection = new ServiceConnection() {
        // NOTE(doyle): Use the binder to get access to the audio service, i.e. playback control
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudioService.LocalBinder binder = (AudioService.LocalBinder) service;
            audioService = binder.getService();
            audioService.listener = audioServiceResponse;
            serviceBound = true;

            if (Debug.DEBUG_MODE) {
                Toast.makeText(getApplicationContext(), "Service Bound", Toast.LENGTH_SHORT).show();
            }

            if (queuedPlaylist != null) {
                if (Debug.CAREFUL_ASSERT(queuedPlaylistIndex != -1, this,
                        "Playlist queued, but no index specified")) {
                    enqueueToPlayer(queuedPlaylistIndex);
                    queuedPlaylistIndex = -1;
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    private class PlayBarItems {
        Button skipNextButton;
        Button playPauseButton;
        Button skipPreviousButton;
        Button shuffleButton;
        Button repeatButton;
        TextView currentSongTextView;

        SeekBar seekBar;
    }

    private Handler handler;
    PlayBarItems playBarItems;
    Debug.UiUpdateAndRender debugRenderer;
    NavigationView navigationView;
    private void amberCreate() {
        final Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDefaultDisplayHomeAsUpEnabled(true);

        final DrawerLayout drawerLayout = (DrawerLayout)
                findViewById(R.id.activity_main_drawer_layout);
        navigationView = (NavigationView) findViewById(R.id.activity_main_navigation_view);
        navigationView.setNavigationItemSelectedListener
                (new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        switch (item.getItemId()) {

                            case R.id.menu_main_drawer_album: {
                                toolbar.setTitle(getString(R.string.menu_main_drawer_album));
                            } break;

                            case R.id.menu_main_drawer_artist: {
                                toolbar.setTitle(getString(R.string.menu_main_drawer_artist));
                            } break;

                            case R.id.menu_main_drawer_library: {
                                playSpec.activePlaylist = playSpec.libraryList;
                                audioFileAdapter.audioList = playSpec.libraryList.contents;
                                audioFileAdapter.notifyDataSetChanged();
                            } break;

                            default: {
                            } break;
                        }

                        drawerLayout.closeDrawers();
                        return true;
                    }
                });

        // Setting the RelativeLayout as our content view
        playSpec = new PlaySpec();
        audioFileAdapter = new AudioFileAdapter(this, null);

        Menu drawerMenu = navigationView.getMenu();
        for (int i = 0; i < drawerMenu.size(); i++) {
            MenuItem item = drawerMenu.getItem(i);
            if (item.isChecked()) {
                switch (item.getItemId()) {
                    case R.id.menu_main_drawer_album: {
                        ASSERT(false);
                    } break;

                    case R.id.menu_main_drawer_artist: {
                        ASSERT(false);
                    } break;

                    case R.id.menu_main_drawer_library: {
                        audioFileAdapter.audioList = playSpec.libraryList.contents;
                        audioFileAdapter.notifyDataSetChanged();
                    } break;
                }

                break;
            }
        }

        ListView audioFileListView = (ListView) findViewById(R.id.main_list_view);
        audioFileListView.setAdapter(audioFileAdapter);


        /*******************************************************************************************
         * DEBUG INITIALISATION
         ******************************************************************************************/
        {
            SharedPreferences sharedPref = PreferenceManager.
                    getDefaultSharedPreferences(getApplicationContext());

            if (Debug.RESET_DB) this.deleteDatabase(AudioDatabase.DB_NAME);
            if (Debug.RESET_CONFIG) {
                sharedPref.edit().clear().apply();
                sharedPref.edit().putString(getString(R.string.pref_music_path_key),
                        "/storage/emulated/0/Music").apply();
            }

            AudioDatabase dbHandle = AudioDatabase.getHandle(this);
            if (Debug.RESET_PLAYLIST) {
                dbHandle.deleteAllEntries(AudioDatabase.TableType.PLAYLIST);
                dbHandle.deleteAllEntries(AudioDatabase.TableType.PLAYLIST_CONTENTS);
            }
        }

        handler = new Handler();
        debugRenderer = new Debug.UiUpdateAndRender(this, handler, 10) {
            @Override
            public void renderElements() {
                pushVariable("Amber Version", AMBER_VERSION);
                pushClass(audioService, true, false);
                pushClass(this, true, false);

                Playlist activePlaylist = playSpec.activePlaylist;
                pushVariable("Active Playlist", activePlaylist.name);

                pushText(Debug.GENERATE_COUNTER_STRING());
            }
        };

        // NOTE(doyle): Only ask for permissions if version >= Android M (API 23)
        int readPermissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            // TODO(doyle): If user continually denies permission, show permission rationale
            if (readPermissionCheck == PackageManager.PERMISSION_DENIED) {
                String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE};
                ActivityCompat.requestPermissions(this, permissions,
                        AMBER_READ_EXTERNAL_STORAGE_REQUEST);
            }
        }

        if (readPermissionCheck == PackageManager.PERMISSION_GRANTED) {
            queryDeviceForAudioData();
        }


        playBarItems = new PlayBarItems();
        playBarItems.playPauseButton = (Button) findViewById(R.id.play_bar_play_button);
        playBarItems.skipNextButton = (Button) findViewById(R.id.play_bar_skip_next_button);
        playBarItems.skipPreviousButton = (Button) findViewById(R.id.play_bar_skip_previous_button);
        playBarItems.seekBar = (SeekBar) findViewById(R.id.play_bar_seek_bar);
        playBarItems.shuffleButton = (Button) findViewById(R.id.play_bar_shuffle_button);
        playBarItems.repeatButton = (Button) findViewById(R.id.play_bar_repeat_button);
        playBarItems.currentSongTextView = (TextView)
                findViewById(R.id.play_bar_current_song_text_view);

        playBarItems.playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (serviceBound) {
                    if (audioService.playState == AudioService.PlayState.PLAYING) {
                        v.setBackgroundResource(R.drawable.ic_play);
                        audioService.pauseMedia();
                    } else if (audioService.playState == AudioService.PlayState.PAUSED){
                        v.setBackgroundResource(R.drawable.ic_pause);
                        audioService.playMedia();
                    }
                }
            }
        });

        playBarItems.skipNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (serviceBound) {
                    audioService.skipToNextOrPrevious(AudioService.PlaybackSkipDirection.NEXT);
                }
            }
        });

        playBarItems.skipPreviousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (serviceBound) {
                    audioService.skipToNextOrPrevious(AudioService.PlaybackSkipDirection.PREVIOUS);
                }
            }
        });

        playBarItems.repeatButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (serviceBound) {
                    audioService.repeat = !audioService.repeat;
                    Drawable background = ContextCompat.getDrawable
                            (v.getContext(), R.drawable.ic_repeat);
                    int color;
                    if (audioService.repeat) {
                        color = ContextCompat.getColor(v.getContext(), R.color.colorAccent);
                    } else {
                        color = ContextCompat.getColor(v.getContext(), R.color.black_87_percent);
                    }

                    background.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                    v.setBackground(background);
                }
            }
        });

        playBarItems.shuffleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (serviceBound) {
                    audioService.shuffle = !audioService.shuffle;
                    Drawable background = ContextCompat.getDrawable
                            (v.getContext(), R.drawable.ic_shuffle);
                    int color;
                    if (audioService.shuffle) {
                        color = ContextCompat.getColor(v.getContext(), R.color.colorAccent);
                    } else {
                        color = ContextCompat.getColor(v.getContext(), R.color.black_87_percent);
                    }

                    background.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                    v.setBackground(background);
                }
            }
        });

        playBarItems.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (serviceBound && fromUser) {
                    audioService.seekTo(progress);
                    audioService.resumePosition = progress;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        audioServiceResponse = new AudioService.Response() {
            @Override
            public void audioHasStartedPlayback() {
                // NOTE: Fall through to another function for organisation
                whenAudioHasStarted();
            }
        };

    }

    private void queryDeviceForAudioData() {
        AudioDatabase dbHandle = AudioDatabase.getHandle(this);
        GetAudioFromDevice task = new GetAudioFromDevice(this, playSpec, dbHandle,
                audioFileAdapter, navigationView.getMenu());
        task.execute();
    }

    /*
     ***********************************************************************************************
     * FRAGMENT LIFECYCLE BEHAVIOUR
     ***********************************************************************************************
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        amberCreate();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!serviceBound) {
            LOG_D(this, "Starting audio service");
            Intent playerIntent = new Intent(this, AudioService.class);
            startService(playerIntent);
            bindService(playerIntent, audioConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) { unbindService(audioConnection); }
        Log.d("DEBUG", Debug.GENERATE_COUNTER_STRING());
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (!Debug.DEBUG_MODE) {
            menu.removeItem(R.id.action_debug_overlay);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch (id) {
            case R.id.action_settings: {
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            }

            case R.id.action_exit: {
                if (serviceBound) {
                    unbindService(audioConnection);
                    audioService.stopSelf();
                }
                System.exit(0);
            } break;

            case R.id.action_debug_overlay: {
                if (debugRenderer != null) debugRenderer.isRunning = !debugRenderer.isRunning;
            } break;

            case R.id.action_rescan_music: {
                // TODO(doyle): For now just drop all the data in the db
                AudioDatabase dbHandle = AudioDatabase.getHandle(this);
                playSpec.allAudioFiles.clear();
                audioFileAdapter.notifyDataSetChanged();
                GetAudioFromDevice task = new GetAudioFromDevice
                        (this, playSpec, dbHandle, audioFileAdapter, navigationView.getMenu());
                task.execute();
            } break;

            default: {
                Debug.CAREFUL_ASSERT
                        (false, this, "Unrecognised item id selected in options menu: " + id);
            } break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case AMBER_READ_EXTERNAL_STORAGE_REQUEST: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length == 0) {
                    Debug.LOG_I(this, "Read external storage permission request cancelled");
                    return;
                }

                if (grantResults.length > 1) {
                    Debug.LOG_W(this, "Read external storage permission request has unexpected argument " +
                            "results expected length 1, got " + grantResults.length +
                            ". Ignoring additional arguments");
                }

                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) queryDeviceForAudioData();
                else Debug.LOG_I(this, "Read external storage permission request denied");
                return;
            }
            default: {
                Debug.CAREFUL_ASSERT(false, this, "Request code not handle: " + requestCode);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("ServiceState", serviceBound);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        serviceBound = savedInstanceState.getBoolean("ServiceState");
    }


    /*
     ***********************************************************************************************
     * ASYNC TASKS FOR QUERYING AUDIO
     ***********************************************************************************************
     */

    private static class GetAudioFromDevice extends AsyncTask<Void, Void, Void> {
        private WeakReference<Activity> weakContext;
        private WeakReference<PlaySpec> weakPlaySpec;
        private WeakReference<AudioFileAdapter> weakAudioFileAdapter;
        private WeakReference<Menu> weakSideMenu;

        private AudioDatabase dbHandle;

        GetAudioFromDevice(Activity context, PlaySpec playSpec, AudioDatabase dbHandle,
                           AudioFileAdapter audioFileAdapter, Menu sideMenu) {
            this.dbHandle = dbHandle;

            this.weakContext = new WeakReference<>(context);
            this.weakAudioFileAdapter = new WeakReference<>(audioFileAdapter);
            this.weakPlaySpec = new WeakReference<>(playSpec);
            this.weakSideMenu = new WeakReference<>(sideMenu);

        }

        private AudioFile updateAndCheckAgainstDbList(Context context,
                                                      MediaMetadataRetriever retriever,
                                                      File newFile) {

            AudioFile result = null;
            if (newFile == null || !newFile.exists()) {
                Debug.LOG_W(this, "File is null or does not exist, cannot check/add against db");
                return null;
            }

            String checkFields = AudioDatabase.AudioFileEntry.KEY_PATH + " =  ? ";
            String[] checkArgs = new String[] {
                newFile.getPath(),
            };

            Uri newFileUri = Uri.fromFile(newFile);
            long newFileSizeInKb = newFile.length() / 1024;
            AudioDatabase.EntryCheckResult dbCheck =
                    dbHandle.checkIfExistFromFieldWithValue(checkFields, checkArgs);

            if (dbCheck.result == AudioDatabase.CheckResult.EXISTS) {

                // NOTE(doyle): If a result exists, then we check the file sizes to ensure they
                // still match
                if (dbCheck.entries.size() == 1) {
                    AudioFile dbAudioFile = dbCheck.entries.get(0);

                    if (dbAudioFile.sizeInKb == newFileSizeInKb) {
                        result = dbAudioFile;

                    } else {
                        // NOTE(doyle): File has changed since last db scan, update old entry
                        AudioFile newAudio =
                                Util.extractAudioMetadata(context, retriever, newFileUri);
                        newAudio.dbKey = dbAudioFile.dbKey;

                        dbHandle.updateAudioFileInDbWithKey(newAudio);
                        result = newAudio;
                    }

                    Debug.LOG_V(this, "Parsed audio: " +
                            result.artist + " - " + result.title);
                } else if (dbCheck.entries.size() > 1) {
                    // NOTE(doyle): There are multiple matching entries in the db with the uri.
                    // This is likely an invalid situation, not sure how it could occur, so play it
                    // safe, delete all entries and reinsert the file
                    Debug.LOG_W(this, "Unexpected multiple matching uri entries in db");
                    Debug.LOG_W(this, "New file: " + newFile.getPath() + " "
                            + newFileSizeInKb);

                    for (int i = 0; i < dbCheck.entries.size(); i++) {
                        AudioFile checkAgainst = dbCheck.entries.get(i);

                        if (newFileSizeInKb != checkAgainst.sizeInKb) {
                            Debug.LOG_W(this, "Multiple entries matched: " +
                                    checkAgainst.uri.getPath() + " " + checkAgainst.sizeInKb);
                            dbHandle.deleteAudioFileFromDbWithKey(checkAgainst.dbKey);
                        }
                    }

                    result = Util.extractAudioMetadata(context, retriever, newFileUri);
                    dbHandle.insertAudioFileToDb(result);
                } else {
                    Debug.CAREFUL_ASSERT(false, this, "Error! An empty db check " +
                            "result should not occur when marked existing!");
                }

            } else if (dbCheck.result == AudioDatabase.CheckResult.NOT_EXIST) {
                result = Util.extractAudioMetadata(context, retriever, newFileUri);
                dbHandle.insertAudioFileToDb(result);
            }

            return result;
        }

        // TODO(doyle): Binary search
        private int findAudioFileInList(List<AudioFile> list, AudioFile findFile) {

            for (int i = 0; i < list.size(); i++) {
                AudioFile checkAgainst = list.get(i);
                if (findFile.artist.equals(checkAgainst.artist) &&
                        findFile.title.equals(checkAgainst.title) &&
                        findFile.sizeInKb == checkAgainst.sizeInKb) {
                    return i;
                }
            }

            return -1;

        }

        private void quickLoadFromDb(PlaySpec playSpec) {
            List<AudioFile> fileListFromDb = dbHandle.getAllAudioFiles();
            List<Playlist> playlistListFromDb = dbHandle.getAllPlaylists();

            if (fileListFromDb != null) {
                if (fileListFromDb.size() > 0) {
                    playSpec.allAudioFiles.clear();
                    playSpec.allAudioFiles.addAll(fileListFromDb);
                }
            }

            if (playlistListFromDb != null) {
                if (playlistListFromDb.size() > 0) {
                    playSpec.playlistList.clear();
                    playSpec.playlistList.addAll(playlistListFromDb);
                }
            }

            publishProgress();
        }

        @Override
        protected Void doInBackground(Void... params) {
            /*
             ******************************
             * READ AUDIO FROM MEDIA STORE
             ******************************
             */
            Activity context = weakContext.get();
            if (context == null) {
                Debug.LOG_W(this, "Context got GC'ed. MediaStore not scanned");
                return null;
            }

            PlaySpec playSpec = weakPlaySpec.get();
            if (playSpec == null) {
                Debug.LOG_W(this, "playSpec got GC'ed early, scan ending early");
                return null;
            }

            // NOTE(doyle): Load whatever is in the DB first and let the user interact with that,
            // then recheck information is valid afterwards
            quickLoadFromDb(playSpec);

            // After load, delete any invalid entries first- so that scanning new files has a
            // smaller list to compare to
            for (AudioFile audioFile: playSpec.allAudioFiles) {
                File file = new File(audioFile.uri.getPath());
                if (!file.exists()) {
                    dbHandle.deleteAudioFileFromDbWithKey(audioFile.dbKey);
                    playSpec.allAudioFiles.remove(audioFile);
                }
            }

            for (Playlist playlist: playSpec.playlistList) {
                File file = new File(playlist.uri.getPath());
                if (!file.exists()) {
                    dbHandle.deletePlaylistFromDbWithKey(playlist.dbKey);
                    playSpec.playlistList.remove(playlist);
                }
            }

            /* Start scanning from device */
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            ContentResolver musicResolver = context.getContentResolver();
            Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);

            if (Debug.CAREFUL_ASSERT(musicCursor != null, this,
                    "Cursor could not be resolved from ContentResolver")) {
                if (musicCursor.moveToFirst()) {
                    do {
                        // NOTE(doyle): For media store files, we need an absolute path NOT a
                        // content URI. Since serialising a content uri does not preserve the
                        // content flags, content cannot be played back in MediaPlayer
                        int absPathIndex = musicCursor.
                                getColumnIndex(MediaStore.Audio.Media.DATA);
                        if (Debug.CAREFUL_ASSERT(absPathIndex != -1, this,
                                "Mediastore file does not have an absolute path field")) {
                            String absPath = musicCursor.getString(absPathIndex);
                            File file = new File(absPath);

                            updateAndCheckAgainstDbList(context, retriever, file);
                        }
                    } while (musicCursor.moveToNext());
                } else {
                    Debug.LOG_V(this, "No music files found by MediaStore");
                }
                musicCursor.close();
            }

            /***************************************************************************************
             * READ AUDIO FROM USER DEFINED PATH
             **************************************************************************************/
            /* Get music from the path set in preferences */
            // TODO(doyle): Add callback handling on preference change
            SharedPreferences sharedPref = PreferenceManager.
                    getDefaultSharedPreferences(context);
            String musicPath = sharedPref.getString("pref_music_path_key", "");
            File musicDir = new File(musicPath);

            List<File> listOfPlaylistsFiles = new ArrayList<>();
            if (musicDir.exists() && musicDir.canRead()) {
                LOG_D(this, "Music directory exists and is readable: " + musicDir.getAbsolutePath());

                ArrayList<File> fileList = new ArrayList<>();
                Util.getFilesFromDirRecursive(fileList, musicDir);

                for (File file : fileList) {
                    // TODO(doyle): Better file support
                    String fileName = file.getName();

                    if (fileName.endsWith(".opus")) {
                        updateAndCheckAgainstDbList(context, retriever, file);
                    } else if (fileName.endsWith(".m3u") || fileName.endsWith(".m3u8")) {
                        if (file.canRead()) {
                            listOfPlaylistsFiles.add(file);
                        } else {
                            Debug.LOG_D(this, "Insufficient permission to read playlist: "
                                    + file.getAbsolutePath());
                        }
                    }
                }

            } else {
                Debug.LOG_W(this, "Could not find/read music directory: " + musicDir);
            }

            List<AudioFile> filesFromDb = dbHandle.getAllAudioFiles();
            playSpec.allAudioFiles.clear();
            playSpec.allAudioFiles.addAll(filesFromDb);
            List<AudioFile> allAudioFiles = playSpec.allAudioFiles;

            /* Sort list */
            Collections.sort(allAudioFiles, new Comparator<AudioFile>() {
                public int compare(AudioFile a, AudioFile b) {
                    return a.title.compareTo(b.title);
                }
            });

            class RawPlaylist {
                private String name;
                private List<AudioFile> contents;
                private RawPlaylist() { contents = new ArrayList<>(); }
            }

            for (File playlistFile: listOfPlaylistsFiles) {
                RawPlaylist rawPlaylist = new RawPlaylist();

                String playlistName = playlistFile.getName();
                int fileExtensionIndex = playlistName.lastIndexOf('.');
                rawPlaylist.name = playlistName.substring(0, fileExtensionIndex);

                try {
                    File entryFolder =
                            playlistFile.getParentFile().getParentFile();
                    BufferedReader reader = new BufferedReader(new FileReader(playlistFile));
                    String playlistEntry;

                    while ((playlistEntry = reader.readLine()) != null) {

                        // NOTE(doyle): Windows uses back-slashes, Android is unix so need /
                        playlistEntry = playlistEntry.replace('\\', '/');
                        playlistEntry = playlistEntry.trim();

                        File actualFile;

                        /* Determine if playlist is in absolute paths or relative */
                        File testIfAbsolutePath = new File(playlistEntry);
                        if (testIfAbsolutePath.exists()) {
                            actualFile = testIfAbsolutePath;

                        } else {
                            if (playlistEntry.startsWith("/"))
                                playlistEntry = playlistEntry.substring(1);
                            actualFile = new File(entryFolder, playlistEntry);
                        }

                        AudioFile file = updateAndCheckAgainstDbList(context, retriever,
                                actualFile);
                        if (file != null) {
                            rawPlaylist.contents.add(file);
                        }
                    }

                    Playlist newPlaylist =
                            new Playlist(rawPlaylist.name, Uri.fromFile(playlistFile));
                    // TODO(doyle): Improve from linear search
                    /* Replace file entries with references to the global audio list */
                    for (AudioFile findFile: rawPlaylist.contents) {
                        int index = findAudioFileInList(allAudioFiles, findFile);
                        ASSERT(index != -1);

                        newPlaylist.contents.add(allAudioFiles.get(index));
                    }

                    String newPlaylistPath = newPlaylist.uri.getPath();
                    boolean matched = false;
                    // TODO(doyle): Improve from linear search
                    for (Playlist list: playSpec.playlistList) {
                        if (newPlaylistPath.equals(list.uri.getPath())) {
                            matched = true;

                            // TODO(doyle): For now just delete playlist and regenerate
                            dbHandle.deletePlaylistContentsFromDbWithPlaylistKey(list.dbKey);
                            dbHandle.insertPlaylistContentToPlaylistKey(newPlaylist.contents,
                                    list.dbKey);
                            break;
                        }
                    }

                    /* Else if not matched, it's a new playlist- insert into db */
                    if (!matched) dbHandle.insertPlaylistFileToDb(newPlaylist);
                } catch (FileNotFoundException e) {
                    CAREFUL_ASSERT(false, this, "Could not find file, " +
                            "should never happen!");
                } catch (IOException e) {
                    Debug.LOG_D(this, "IOException on parsing playlist: "
                            + playlistFile.getAbsolutePath());
                }
            }

            retriever.release();

            playSpec.playlistList.clear();
            playSpec.playlistList = dbHandle.getAllPlaylists();
            return null;
        }

        @Override
        protected void onCancelled() {
            // TODO(doyle): We don't push the remaining batch here, journal? to resume on return
            super.onCancelled();
        }

        private void updateSideNavWithPlaylists(final PlaySpec playSpec) {
            Menu sideMenu = weakSideMenu.get();
            final List<Playlist> playlistList = playSpec.playlistList;
            if (sideMenu != null && playlistList != null) {

                sideMenu.removeGroup(MENU_PLAYLIST_GROUP_ID);
                SubMenu playlistSubMenu = sideMenu.addSubMenu(MENU_PLAYLIST_GROUP_ID,
                        Menu.NONE, Menu.NONE,
                        "Playlist");
                for (final Playlist playlist : playlistList) {

                    int uniqueId;
                    boolean matched;
                    do {
                        // TODO(doyle): Revise this size limit
                        uniqueId = new Random().nextInt(1024);
                        matched = false;
                        for (int i = 0; i < playlistSubMenu.size(); i++) {
                            MenuItem item = playlistSubMenu.getItem(i);
                            if (item.getItemId() == uniqueId) {
                                matched = true;
                                break;
                            }
                        }
                    } while (matched);

                    MenuItem playlistMenuItem = playlistSubMenu.add
                            (MENU_PLAYLIST_GROUP_ID, uniqueId, Menu.NONE, playlist.name);
                    playlist.menuId = playlistMenuItem.getItemId();
                    playlistMenuItem.
                            setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {

                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            // TODO(doyle): Improve linear search maybe? Typical use case doesn't have many playlists
                            for (int i = 0; i < playlistList.size(); i++) {
                                Playlist checkPlaylist = playlistList.get(i);
                                if (checkPlaylist.menuId == item.getItemId()) {
                                    playSpec.activePlaylist = checkPlaylist;
                                    updateDisplay();
                                    break;
                                }
                            }
                            return false;
                        }

                    });
                }
            } else {
                Debug.LOG_W(this, "PlaySpec got GCed, menu not init with playlist. " +
                        "Another rescan needed");
            }
        }

        final int MENU_PLAYLIST_GROUP_ID = 1000;
        private void updateDisplay() {
            final PlaySpec playSpec = weakPlaySpec.get();
            AudioFileAdapter audioFileAdapter = weakAudioFileAdapter.get();
            if (audioFileAdapter != null && playSpec != null) {
                audioFileAdapter.audioList = playSpec.activePlaylist.contents;
                audioFileAdapter.notifyDataSetChanged();
                updateSideNavWithPlaylists(playSpec);

            } else {
                Debug.LOG_W(this, "PlaySpec got GCed, " +
                        "OSD may not be accurate");
            }

        }

        @Override
        protected void onProgressUpdate(Void... args) {
            super.onProgressUpdate();
            updateDisplay();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            updateDisplay();

            Context context = weakContext.get();
            if (context == null) {
                Debug.LOG_W(this, "Context got GC'ed, library init key not written. " +
                        "Another rescan needed");
            } else {
                SharedPreferences sharedPref =
                        PreferenceManager.getDefaultSharedPreferences(context);
                String libraryInitKey = context.getResources().
                        getString(R.string.internal_pref_library_init_key);
                sharedPref.edit().putBoolean(libraryInitKey, true).apply();

                if (Debug.DEBUG_MODE) {
                    Toast.makeText(context, "Library Initialisation has been confirmed",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /*
     ***********************************************************************************************
     * AUDIO METADATA MANIPULATION
     ***********************************************************************************************
     */

    public void audioFileClicked(View view) {
        AudioFileAdapter.AudioEntryInView entry = (AudioFileAdapter.AudioEntryInView) view.getTag();
        int index = entry.position;
        enqueueToPlayer(index);
    }

    // NOTE(doyle): When we request a song to be played, the media player has to be prepared first!
    // Playback does not happen until it is complete! We know when playback is complete in the
    // callback
    private void enqueueToPlayer(int index) {
        if (!serviceBound) {
            LOG_D(this, "Rebinding audio service");
            Intent playerIntent = new Intent(this, AudioService.class);
            startService(playerIntent);
            bindService(playerIntent, audioConnection, Context.BIND_AUTO_CREATE);

            // NOTE(doyle): Queue playlist whereby onServiceConnected will detect and begin playing
            queuedPlaylistIndex = index;
        } else {
            Playlist activePlaylist = playSpec.activePlaylist;
            List<AudioFile> playlistFiles = activePlaylist.contents;

            audioService.preparePlaylist(playlistFiles, index);
            audioService.playMedia();
            // TODO(doyle): Broadcast receiver seems useless here? Whats the point.
            // isn't it better to just directly access the function? The only way we can send
            // broadcasts is through the app itself, so it's not like we can rely on it to "wake-up"
            // our app if it's asleep
            /*
            // NOTE(doyle): Service is active, send media with broadcast receiver
            Intent broadcastIntent = new Intent(BROADCAST_PLAY_NEW_AUDIO);
            sendBroadcast(broadcastIntent);
            */
        }
    }

    public void whenAudioHasStarted() {
        playBarItems.playPauseButton.setBackgroundResource(R.drawable.ic_pause);

        int max = audioService.getTrackDuration();
        playBarItems.seekBar.setMax(max);
        int position = audioService.getCurrTrackPosition();
        playBarItems.seekBar.setProgress(position);

        String artist = audioService.activeAudio.artist;
        String title = audioService.activeAudio.title;
        playBarItems.currentSongTextView.setText(artist + " - " + title);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (serviceBound) {
                    if (audioService.playState == AudioService.PlayState.PLAYING) {
                        int position = audioService.getCurrTrackPosition();
                        playBarItems.seekBar.setProgress(position);
                    }
                    handler.postDelayed(this, 1000);
                }
            }
        });
    }

}
