package dqnt.amber;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.MediaController.MediaPlayerControl;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class MainActivity extends AppCompatActivity implements MediaPlayerControl {
    private static final String TAG = MainActivity.class.getName();
    private static final int AMBER_READ_EXTERNAL_STORAGE_REQUEST = 1;

    private AudioFileAdapter audioFileAdapter;
    private ArrayList<AudioFile> audioList;
    private ListView audioFileListView;

    private AudioService audioService;
    private Intent playIntent;
    private boolean audioBound = false;

    private AudioController controller;
    private boolean paused = false;
    private boolean playbackPaused = false;

    // NOTE(doyle): When the Android system creates the connection between the client and service,
    // (bindService()) it calls onServiceConnected() on the ServiceConnection, to deliver the
    // IBinder that the client can use to communicate with the service. (i.e. callback).
    private ServiceConnection audioConnection = new ServiceConnection() {


        // NOTE(doyle): Use the binder to get access to the audio service, i.e. playback control
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudioService.AudioBinder binder = (AudioService.AudioBinder) service;
            audioService = binder.getService();
            audioService.setAudioList(audioList);

            audioBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            audioBound = false;
        }

    };

    private void getFilesFromDirRecursive(ArrayList<File> list, File root) {
        File[] dir = root.listFiles();
        for (File file : dir) {
            if (file.isDirectory()) {
                getFilesFromDirRecursive(list, file);
            } else {
                list.add(file);
            }
        }
    }

    private void enumerateAndDisplayAudio() {
        ContentResolver musicResolver = getContentResolver();
        Uri musicUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);

        if (musicCursor != null) {
            if (musicCursor.moveToFirst()) {
                int titleCol = musicCursor.getColumnIndex
                        (android.provider.MediaStore.Audio.Media.TITLE);
                int idCol = musicCursor.getColumnIndex
                        (android.provider.MediaStore.Audio.Media._ID);
                int artistCol = musicCursor.getColumnIndex
                        (android.provider.MediaStore.Audio.Media.ARTIST);

                do {
                    int id = musicCursor.getInt(idCol);
                    String title = musicCursor.getString(titleCol);
                    String artist = musicCursor.getString(artistCol);

                    Uri uri = ContentUris.withAppendedId
                            (android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);

                    AudioFile audioFile = new AudioFile(id, uri, artist, title);
                    audioList.add(audioFile);
                } while (musicCursor.moveToNext());
            } else {
                Log.v(TAG, "No music files found by MediaStore");
            }
        } else {
            Log.e(TAG, "Cursor could not be resolved from ContentResolver");
        }

        // TODO(doyle): Add callback handling on preference change
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        /* DEBUG CLEAR preferences */
        // sharedPref.edit().clear().commit();

        String musicPath = sharedPref.getString("pref_music_path_key", "");
        File musicDir = new File(musicPath);
        if (musicDir.exists() && musicDir.canRead()) {
            Log.d(TAG, "Music directory exists and is readable: " + musicDir.getAbsolutePath());

            ArrayList<File> audioFileEnumerator = new ArrayList<>();
            getFilesFromDirRecursive(audioFileEnumerator, musicDir);

            int uniqueId = 999;
            for (File file : audioFileEnumerator) {
                if (file.toString().endsWith(".opus")) {
                    // TODO(doyle): Media parse from media player or external library
                    Uri uri = Uri.fromFile(file);
                    /*
                    musicCursor = musicResolver.query(uri, null, null, null, null);
                    if (musicCursor != null)
                    {
                        musicCursor.moveToFirst();
                        int titleCol = musicCursor.getColumnIndex
                                (android.provider.MediaStore.Audio.Media.TITLE);
                        int idCol = musicCursor.getColumnIndex
                                (android.provider.MediaStore.Audio.Media._ID);
                        int artistCol = musicCursor.getColumnIndex
                                (android.provider.MediaStore.Audio.Media.ARTIST);

                        int id = musicCursor.getInt(idCol);
                        String title = musicCursor.getString(titleCol);
                        String artist = musicCursor.getString(artistCol);

                    }
                    */

                    AudioFile audio = new AudioFile(uniqueId++, uri, "", file.toString());
                    audioList.add(audio);
                }
            }
        }

        /* Sort list */
        Collections.sort(audioList, new Comparator<AudioFile>() {
            public int compare(AudioFile a, AudioFile b) {
                return a.title.compareTo(b.title);
            }
        });

        /* Display audio */
        this.audioFileListView = (ListView) findViewById(R.id.main_list_view);
        this.audioFileAdapter = new AudioFileAdapter(this, audioList);
        this.audioFileListView.setAdapter(this.audioFileAdapter);
    }

    private void amberUpdate() {
        this.audioList = new ArrayList<AudioFile>();

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
            enumerateAndDisplayAudio();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case AMBER_READ_EXTERNAL_STORAGE_REQUEST: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length == 0) {
                    Log.i(TAG, "Read external storage permission request cancelled");
                    return;
                }

                if (grantResults.length > 1)
                    Log.e(TAG, "Read external storage permission request has unexpected argument results expected length 1, got " + grantResults.length + ". Ignoring additional arguments");

                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    enumerateAndDisplayAudio();
                else
                    Log.i(TAG, "Read external storage permission request denied");

                return;
            }
            default: {
                Log.e(TAG, "Permission request code not handled: " + requestCode);
                break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        setController();
        amberUpdate();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (playIntent == null) {
            // TODO(doyle): Start a new thread for MP3 playback, recommended by Android
            playIntent = new Intent(this, AudioService.class);
            bindService(playIntent, audioConnection, Context.BIND_AUTO_CREATE);
            startService(playIntent);
        }
    }

    public void audioFileClicked(View view) {
        int audioFileIndex = Integer.parseInt(view.getTag().toString());

        // TODO(doyle): Look into collapsing into one call ..
        audioService.setAudio(audioFileIndex);
        audioService.play();

        if (playbackPaused) {
            setController();
            playbackPaused = false;
        }

        controller.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    protected void onDestroy() {
        stopService(playIntent);
        audioService = null;
        super.onDestroy();
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
                Intent intent = new Intent();
                intent.setClassName(this, "dqnt.amber.SettingsActivity");
                startActivity(intent);
                return true;
            }
            case R.id.action_stop_playback: {
                if (audioBound) {
                    audioService.end();
                }
                break;
            }
            case R.id.action_exit: {
                stopService(playIntent);
                audioService = null;
                System.exit(0);
                break;
            }
            case R.id.action_shuffle: {
                audioService.setShuffle();
                break;
            }
            default: {
                Log.e(TAG, "Unrecognised item id selected in options menu: " + id);
                break;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void start() {
        audioService.go();
    }

    @Override
    public void pause() {
        audioService.pause();
        playbackPaused = false;
    }

    @Override
    public void onPause() {
        super.onPause();
        paused = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (paused) {
            setController();
            paused = false;
        }
    }

    @Override
    public void onStop() {
        controller.hide();
        super.onStop();
    }

    @Override
    public int getDuration() {
        if (audioService != null && audioBound && audioService.isPlaying())
            return audioService.getDuration();

        return 0;
    }

    @Override
    public int getCurrentPosition() {
        if (audioService != null && audioBound && audioService.isPlaying())
            return audioService.getPosition();

        return 0;
    }

    @Override
    public void seekTo(int pos) {
        audioService.seek(pos);
    }

    @Override
    public boolean isPlaying() {
        if (audioService != null && audioBound)
            return audioService.isPlaying();

        return false;
    }

    @Override
    public int getBufferPercentage() {
        return 0;
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return true;
    }

    @Override
    public boolean canSeekForward() {
        return true;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    private void playNext() {
        audioService.playNext();
        if (playbackPaused) {
            setController();
            playbackPaused = false;
        }
        controller.show(0);
    }

    private void playPrev() {
        audioService.playPrev();
        if (playbackPaused) {
            setController();
            playbackPaused = false;
        }
        controller.show(0);
    }

    private void setController() {
        controller = new AudioController(this);
        controller.setPrevNextListeners(new View.OnClickListener() {
            public void onClick(View v) {
                playNext();
                controller.show();
            }
        }, new View.OnClickListener() {
            public void onClick(View v) {
                playPrev();
                controller.show();
            }
        });

        controller.setMediaPlayer(this);
        controller.setAnchorView(findViewById(R.id.main_list_view));
        controller.setEnabled(true);
    }

    public class AudioFile {
        int id;
        Uri uri;
        String artist;
        String title;

        public AudioFile(int id, Uri uri, String artist, String title) {
            this.id = id;
            this.uri = uri;
            this.artist = artist;
            this.title = title;
        }
    }
}
