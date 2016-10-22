package com.dqnt.amber;


import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
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
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.dqnt.amber.PlaybackData.AudioFile;

public class MainActivity extends AppCompatActivity {
    private static final Class DEBUG_TAG = MainActivity.class;
    private static final int AMBER_READ_EXTERNAL_STORAGE_REQUEST = 1;
    private AudioFileAdapter audioFileAdapter;

    private List<AudioFile> allAudioFiles;

    private AudioService audioService;
    private boolean serviceBound = false;

    private List<AudioFile> queuedPlaylist = null;
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
                if (Debug.CAREFUL_ASSERT(queuedPlaylistIndex != -1, DEBUG_TAG,
                        "Playlist queued, but no index specified")) {
                    enqueueToPlayer(queuedPlaylist, queuedPlaylistIndex);
                    queuedPlaylist = null;
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

    private Handler seekbarHandler;
    PlayBarItems playBarItems;
    private void amberCreate() {
        /* Assign UI references */
        allAudioFiles = new ArrayList<>();
        audioFileAdapter = new AudioFileAdapter(getBaseContext(), allAudioFiles);

        ListView audioFileListView = (ListView) findViewById(R.id.main_list_view);
        audioFileListView.setAdapter(audioFileAdapter);

        /* DEBUG CLEAR preferences */
        SharedPreferences sharedPref = PreferenceManager.
                getDefaultSharedPreferences(getApplicationContext());

        if (Debug.RESET_DB) this.deleteDatabase(AudioDatabase.DB_NAME);
        if (Debug.RESET_CONFIG) {
            sharedPref.edit().clear().apply();
            sharedPref.edit().putString(getString(R.string.pref_music_path_key),
                    "/storage/emulated/0/Music").apply();
        }

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

        seekbarHandler = new Handler();

        audioServiceResponse = new AudioService.Response() {
            @Override
            public void audioHasStartedPlayback() {
                // NOTE: Fall through to another function for organisation
                whenAudioHasStarted();
            }
        };
    }

    private void queryDeviceForAudioData() {
        Context appContext = getApplicationContext();
        SharedPreferences sharedPref = PreferenceManager.
                getDefaultSharedPreferences(appContext);
        boolean libraryInit = sharedPref.getBoolean
                (getResources().getString(R.string.internal_pref_library_init_key), false);

        AudioDatabase dbHandle = AudioDatabase.getHandle(appContext);
        if (Debug.CAREFUL_ASSERT(dbHandle != null, DEBUG_TAG,
                "amberCreate(): Could not get a db handle")) {
            if (libraryInit) {
                new GetAudioMetadataFromDb(dbHandle).execute();
            } else {
                GetAudioFromDevice task = new GetAudioFromDevice(appContext, dbHandle);
                task.execute();
            }
        }
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

        final Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDefaultDisplayHomeAsUpEnabled(true);

        amberCreate();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!serviceBound) {
            Debug.LOG_D(DEBUG_TAG, "Starting audio service");
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
                break;
            }

            default: {
                Debug.CAREFUL_ASSERT
                        (false, DEBUG_TAG, "Unrecognised item id selected in options menu: " + id);
                break;
            }
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
                    Debug.LOG_I(DEBUG_TAG, "Read external storage permission request cancelled");
                    return;
                }

                if (grantResults.length > 1) {
                    Debug.LOG_W(DEBUG_TAG, "Read external storage permission request has unexpected argument " +
                            "results expected length 1, got " + grantResults.length +
                            ". Ignoring additional arguments");
                }

                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) queryDeviceForAudioData();
                else Debug.LOG_I(DEBUG_TAG, "Read external storage permission request denied");
                return;
            }
            default: {
                Debug.CAREFUL_ASSERT(false, DEBUG_TAG, "Request code not handle: " + requestCode);
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

    private class GetAudioMetadataFromDb extends AsyncTask<Void, Void, Void> {
        private final Class ASSERT_TAG = GetAudioMetadataFromDb.class;

        AudioDatabase dbHandle;
        GetAudioMetadataFromDb(AudioDatabase dbHandle) {
            if (Debug.CAREFUL_ASSERT(dbHandle != null, ASSERT_TAG,
                    "dbHandle is null in constructor")) {
                this.dbHandle = dbHandle;
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            // TODO(doyle): Look at again, we copy since other activities use a reference to our master list
            List<AudioFile> list = dbHandle.getAllAudioFiles();
            allAudioFiles.addAll(list);

            Collections.sort(allAudioFiles, new Comparator<AudioFile>() {
                public int compare(AudioFile a, AudioFile b) {
                    return a.title.compareTo(b.title);
                }
            });
            return null;
        }

        @Override
        protected void onCancelled(Void aVoid) {
            super.onCancelled(aVoid);
            dbHandle.close();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            dbHandle.close();
            audioFileAdapter.notifyDataSetChanged();
        }
    }

    private class GetAudioFromDevice extends AsyncTask<Void, Void, Void> {
        private final Class DEBUG_TAG = GetAudioFromDevice.class;

        private Context appContext;
        AudioDatabase dbHandle;
        GetAudioFromDevice(Context appContext, AudioDatabase dbHandle) {
            if (Debug.CAREFUL_ASSERT(dbHandle != null, DEBUG_TAG,
                    "dbHandle is null in constructor")) {
                this.dbHandle = dbHandle;
                Debug.INCREMENT_COUNTER(this.getClass(), "GetAudio");
            }

            this.appContext = appContext;
        }

        private List<AudioFile> updateBatch = new ArrayList<>();
        private int updateCounter = 0;
        private final int UPDATE_THRESHOLD = 20;
        // TODO(doyle): Profile UPDATE_THRESHOLD
        private void checkAndAddFileToDb(final AudioFile file, final AudioDatabase dbHandle) {
            if (file == null) return;

            updateBatch.add(file);

            if (updateCounter++ >= UPDATE_THRESHOLD) {
                updateCounter = 0;

                dbHandle.insertMultiAudioFileToDb(updateBatch);
                allAudioFiles.addAll(updateBatch);
                updateBatch.clear();

                publishProgress();
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            /*
             ******************************
             * READ AUDIO FROM MEDIA STORE
             ******************************
             */
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            ContentResolver musicResolver = getContentResolver();
            Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);

            int audioId = 0;
            if (Debug.CAREFUL_ASSERT(musicCursor != null, DEBUG_TAG,
                    "Cursor could not be resolved from ContentResolver")) {
                if (musicCursor.moveToFirst()) {
                    int idCol = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID);
                    do {
                        // NOTE(doyle): For media store files, we need an absolute path NOT a
                        // content URI. Since serialising a content uri does not preserve the
                        // content flags, content cannot be played back in MediaPlayer
                        int absPathIndex = musicCursor.
                                getColumnIndex(MediaStore.Audio.Media.DATA);
                        if (Debug.CAREFUL_ASSERT(absPathIndex != -1, DEBUG_TAG,
                                "Mediastore file does not have an absolute path field")) {
                            String absPath = musicCursor.getString(absPathIndex);
                            Uri uri = Uri.fromFile(new File(absPath));

                            if (Debug.CAREFUL_ASSERT(uri != null, DEBUG_TAG,
                                    "could not be resolved from absolute path")) {
                                AudioFile audio = extractAudioMetadata(appContext, retriever,
                                        audioId++, uri);
                                checkAndAddFileToDb(audio, dbHandle);
                            }
                        }
                    } while (musicCursor.moveToNext());
                } else {
                    Debug.LOG_V(DEBUG_TAG, "No music files found by MediaStore");
                }
                musicCursor.close();
            }

            /*
             ************************************
             * READ AUDIO FROM USER DEFINED PATH
             ************************************
             */
            /* Get music from the path set in preferences */
            // TODO(doyle): Add callback handling on preference change
            SharedPreferences sharedPref = PreferenceManager.
                    getDefaultSharedPreferences(appContext);
            String musicPath = sharedPref.getString("pref_music_path_key", "");
            File musicDir = new File(musicPath);

            if (musicDir.exists() && musicDir.canRead()) {
                Debug.LOG_D(DEBUG_TAG, "Music directory exists and is readable: " + musicDir.getAbsolutePath());

                ArrayList<File> fileList = new ArrayList<>();
                Util.getFilesFromDirRecursive(fileList, musicDir);

                for (File file : fileList) {
                    // TODO(doyle): Better file support
                    if (file.toString().endsWith(".opus")) {
                        Uri uri = Uri.fromFile(file);

                        AudioFile audio = extractAudioMetadata(appContext, retriever, audioId++,
                                uri);
                        checkAndAddFileToDb(audio, dbHandle);
                    }
                }
            } else {
                Debug.LOG_I(DEBUG_TAG, "Could not find/read music directory: " + musicDir);
            }

            // NOTE(doyle): Add remaining files sitting in batch
            if (updateBatch.size() > 0) {
                allAudioFiles.addAll(updateBatch);
                dbHandle.insertMultiAudioFileToDb(updateBatch);
            }

            retriever.release();
                /* Sort list */
            Collections.sort(allAudioFiles, new Comparator<AudioFile>() {
                public int compare(AudioFile a, AudioFile b) {
                    return a.title.compareTo(b.title);
                }
            });

            return null;
        }

        @Override
        protected void onCancelled() {
            // TODO(doyle): We don't push the remaining batch here, journal? to resume on return
            super.onCancelled();
            dbHandle.close();
        }

        @Override
        protected void onProgressUpdate(Void... args) {
            super.onProgressUpdate();
            dbHandle.close();
            audioFileAdapter.notifyDataSetChanged();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            audioFileAdapter.notifyDataSetChanged();

            SharedPreferences sharedPref =
                    PreferenceManager.getDefaultSharedPreferences(appContext);
            String libraryInitKey = getResources().
                    getString(R.string.internal_pref_library_init_key);
            sharedPref.edit().putBoolean(libraryInitKey, true).apply();
        }
    }

    /*
     ***********************************************************************************************
     * AUDIO METADATA MANIPULATION
     ***********************************************************************************************
     */
    private String extractAudioKeyData(MediaMetadataRetriever retriever, int key) {
        String field = retriever.extractMetadata(key);
        if (field == null) field = "Unknown";
        return field;
    }

    private AudioFile extractAudioMetadata(Context context, MediaMetadataRetriever mmr,
                                                        int id, Uri uri) {

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
        Debug.LOG_V(DEBUG_TAG, "File: " + file.getName() + ", Parsed audio: " + artist +  " - " + title);

        AudioFile result = new AudioFile(id,
                                         uri,
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
                                         year);
        return result;
    }

    public void audioFileClicked(View view) {
        AudioFileAdapter.AudioEntryInView entry = (AudioFileAdapter.AudioEntryInView) view.getTag();
        int index = entry.position;

        // TODO(doyle): Proper playlist creation
        enqueueToPlayer(allAudioFiles, index);
    }

    // NOTE(doyle): When we request a song to be played, the media player has to be prepared first!
    // Playback does not happen until it is complete! We know when playback is complete in the
    // callback
    private void enqueueToPlayer(List<AudioFile> playlist, int index) {
        if (!serviceBound) {
            Debug.LOG_D(DEBUG_TAG, "Rebinding audio service");
            Intent playerIntent = new Intent(this, AudioService.class);
            startService(playerIntent);
            bindService(playerIntent, audioConnection, Context.BIND_AUTO_CREATE);

            // NOTE(doyle): Queue playlist whereby onServiceConnected will detect and begin playing
            queuedPlaylist = playlist;
        } else {
            audioService.preparePlaylist(playlist, index);
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
                    seekbarHandler.postDelayed(this, 1000);
                }
            }
        });
    }

}
