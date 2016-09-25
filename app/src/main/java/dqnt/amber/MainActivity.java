package dqnt.amber;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getName();
    private static final int AMBER_READ_EXTERNAL_STORAGE_REQUEST = 1;

    private AudioFileAdapter audioFileAdapter;
    private ArrayList<AudioFile> audioList;
    private ListView audioFileListView;

    public class AudioFile
    {
        int id;
        String artist;
        String title;

        public AudioFile(int id, String artist, String title)
        {
            this.id = id;
            this.artist = artist;
            this.title = title;
        }
    }

    private void getFilesFromDirRecursive(ArrayList<File> list, File root)
    {
        File[] dir = root.listFiles();
        for (File file: dir)
        {
            if (file.isDirectory())
            {
                getFilesFromDirRecursive(list, file);
            }
            else
            {
                list.add(file);
            }
        }
    }

    private void enumerateAndDisplayAudio()
    {
        ContentResolver musicResolver = getContentResolver();
        Uri musicUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);

        if (musicCursor != null)
        {
            if (musicCursor.moveToFirst())
            {
                int titleCol = musicCursor.getColumnIndex
                        (android.provider.MediaStore.Audio.Media.TITLE);
                int idCol = musicCursor.getColumnIndex
                        (android.provider.MediaStore.Audio.Media._ID);
                int artistCol = musicCursor.getColumnIndex
                        (android.provider.MediaStore.Audio.Media.ARTIST);

                do
                {
                    int id = musicCursor.getInt(idCol);
                    String title = musicCursor.getString(titleCol);
                    String artist = musicCursor.getString(artistCol);

                    AudioFile audioFile = new AudioFile(id, artist, title);
                    audioList.add(audioFile);
                } while (musicCursor.moveToNext());
            }
            else
            {
                Log.v(TAG, "No music files found by MediaStore");
            }
        }
        else
        {
            Log.e(TAG, "Cursor could not be resolved from ContentResolver");
        }

        // TODO(doyle): Add callback handling on preference change
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        /* DEBUG CLEAR preferences */
        // sharedPref.edit().clear().commit();

        String musicPath = sharedPref.getString("pref_music_path_key", "");
        File musicDir = new File(musicPath);
        if (musicDir.exists() && musicDir.canRead())
        {
            Log.d(TAG, "Music directory exists and is readable: " + musicDir.getAbsolutePath());

            ArrayList<File> audioFileEnumerator = new ArrayList<>();
            getFilesFromDirRecursive(audioFileEnumerator, musicDir);

            int uniqueId = 999;
            for (File file: audioFileEnumerator)
            {
                if (file.toString().endsWith(".opus"))
                {
                    // TODO(doyle): Media parse from media player or external library
                    AudioFile audio = new AudioFile(uniqueId++, "", file.toString());
                    audioList.add(audio);
                }
            }
            /*
            list = musicDir.listFiles(new FilenameFilter(){
                @Override
                public boolean accept(File dir, String name) {
                    boolean result = false;
                    if (name.endsWith(".opus")) result = true;
                    return result;
                }
            });
            */

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

    private void amberUpdate()
    {
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

        if (readPermissionCheck == PackageManager.PERMISSION_GRANTED)
        {
            enumerateAndDisplayAudio();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case AMBER_READ_EXTERNAL_STORAGE_REQUEST:
            {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length == 0)
                {
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
            default:
            {
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

        amberUpdate();
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
        switch(id)
        {
            case R.id.action_settings:
            {
                Intent intent = new Intent();
                intent.setClassName(this, "dqnt.amber.SettingsActivity");
                startActivity(intent);
                return true;
            }
            default:
            {
                Log.e(TAG, "Unrecognised item id selected in options menu: " + id);
            }
        }

        return super.onOptionsItemSelected(item);
    }
}
