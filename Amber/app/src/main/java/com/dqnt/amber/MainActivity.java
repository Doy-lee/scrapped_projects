package com.dqnt.amber;


import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import com.dqnt.amber.Models.AudioFile;
import com.dqnt.amber.Models.Playlist;
import com.google.gson.Gson;

import static com.dqnt.amber.Debug.ASSERT;
import static com.dqnt.amber.Debug.CAREFUL_ASSERT;
import static com.dqnt.amber.Debug.LOG_D;
import static com.dqnt.amber.Util.flagIsSet;

interface AudioFileClickListener {
    void audioFileClicked (Playlist newPlaylist);
}

public class MainActivity extends AppCompatActivity implements AudioFileClickListener {
    public static final String BROADCAST_UPDATE_UI = "com.dqnt.amber.BroadcastUpdateUi";
    private static final int AMBER_READ_EXTERNAL_STORAGE_REQUEST = 1;
    private static final int AMBER_VERSION = 1;

    static class PlaySpecFlags {
        static final int SHUFFLE = 1 << 1;
        static final int REPEAT  = 1 << 2;
    }

    enum PlayState {
        PLAYING,
        ADVANCE_TO_NEW_AUDIO,
        PAUSED,
        STOPPED,
    }

    class PlaySpec {
        final List<AudioFile> allAudioFiles;
        // NOTE(doyle): Permanent list that has all the files the app has scanned, separate from the
        // list index because we clear the playlist list on load from DB, so save having to recreate
        // a library list every time we init from DB.
        final Playlist libraryList;

        List<Playlist> playlistList;

        Playlist playingPlaylist;
        Playlist searchResultPlaylist;
        Playlist returnFromSearchPlaylist;

        boolean searchMode;
        int flags;
        int resumePosInMs;
        AudioFile activeAudio;
        PlayState state;

        PlaySpec() {
            allAudioFiles = new ArrayList<>();
            playlistList = new ArrayList<>();

            libraryList = new Playlist("Library");
            libraryList.contents = allAudioFiles;
            libraryList.index = -1;
        }
    }

    class UiSpec {
        Toolbar toolbar;
        NavigationView navigationView;

        PlayBarItems playBarItems;

        Handler handler;

        int primaryColor;
        int accentColor;
        int primaryTextColor;

        Debug.UiUpdateAndRender debugRenderer;
    }

    private class PlayBarItems {
        Button skipNextButton;
        Button playPauseButton;
        Button skipPreviousButton;
        Button shuffleButton;
        Button repeatButton;
        Button searchButton;
        EditText searchEditText;

        TextView currentSongTextView;

        SeekBar seekBar;
        Runnable seekBarUpdater;
        boolean seekBarIsRunning;
    }


    PlaySpec playSpec_;
    UiSpec uiSpec_;

    private boolean playlistQueued;
    private boolean serviceBound;
    private AudioService.Response audioServiceResponse;
    private GetAudioFromDevice getAudioFromDevice;

    AudioService audioService;

    enum FragmentType {
        ARTIST,
        ARTIST_FILES,
        ALBUM,
        ALBUM_FILES,
        ALBUM_ARTIST,
        ALBUM_ARTIST_FILES,
        GENRE,
        GENRE_FILES,
        PLAYLIST,
        INVALID;
    }
    MetadataFragment metadataFragment;

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
            audioService.playSpec = playSpec_;
            serviceBound = true;

            if (Debug.DEBUG_MODE) {
                Toast.makeText(getApplicationContext(), "Service Bound", Toast.LENGTH_SHORT).show();
            }

            if (playlistQueued) {
                enqueueToPlayer(true);
                playlistQueued = false;
            }

            SharedPreferences sharedPref =
                    PreferenceManager.getDefaultSharedPreferences(audioService);

            boolean onRepeat =
                    sharedPref.getBoolean(getString(R.string.internal_pref_repeat_mode), false);
            if (onRepeat) {
                playSpec_.flags |= PlaySpecFlags.REPEAT;
                int activeColor = uiSpec_.accentColor;
                uiSpec_.playBarItems.repeatButton.getBackground()
                        .setColorFilter(activeColor, PorterDuff.Mode.SRC_IN);
            }

            boolean onShuffle =
                    sharedPref.getBoolean(getString(R.string.internal_pref_shuffle_mode), false);
            if (onShuffle) {
                playSpec_.flags |= PlaySpecFlags.SHUFFLE;
                int activeColor = uiSpec_.accentColor;
                uiSpec_.playBarItems.shuffleButton.getBackground()
                        .setColorFilter(activeColor, PorterDuff.Mode.SRC_IN);
            }

            audioService.setNotificationMediaCallbacks(new MediaSessionCompat.Callback() {
                @Override
                public void onPlay() {
                    handleMediaInput(Input.PLAY, null);
                }

                @Override
                public void onPause() {
                    handleMediaInput(Input.PAUSE, null);
                }

                @Override
                public void onSkipToNext() {
                    handleMediaInput(Input.SKIP_TO_NEXT, null);
                }

                @Override
                public void onSkipToPrevious() {
                    handleMediaInput(Input.SKIP_TO_PREV, null);
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    private void amberCreate() {
        { // Intialise debug state
            SharedPreferences sharedPref = PreferenceManager.
                    getDefaultSharedPreferences(getApplicationContext());

            if (Debug.RESET_DB) this.deleteDatabase(AudioStorage.DB_NAME);
            if (Debug.RESET_CONFIG) {
                sharedPref.edit().clear().apply();
                sharedPref.edit().putString(getString(R.string.pref_music_path_key),
                        "/storage/emulated/0/Music").apply();
            }

            AudioStorage dbHandle = AudioStorage.getHandle(this);
            if (Debug.RESET_PLAYLIST) {
                dbHandle.deleteAllEntries(AudioStorage.TableType.PLAYLIST);
                dbHandle.deleteAllEntries(AudioStorage.TableType.PLAYLIST_CONTENTS);
            }
        }

        initAppData();
        registerUpdateUiReceiver(updateUiReceiver);

        // Initialise UI
        uiSpec_ = new UiSpec();

        audioServiceResponse = new AudioServiceResponse(this, uiSpec_, playSpec_);
        uiSpec_.toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(uiSpec_.toolbar);
        getSupportActionBar().setDefaultDisplayHomeAsUpEnabled(true);

        metadataFragment = MetadataFragment.newInstance();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.main_content_fragment, metadataFragment)
                // .addToBackStack(null)
                .commit();

        uiSpec_.primaryColor = ContextCompat.getColor(this, R.color.colorPrimary);
        uiSpec_.accentColor = ContextCompat.getColor(this, R
                .color.colorAccent);
        uiSpec_.primaryTextColor
                = ContextCompat.getColor(this, R.color.primary_text_on_light_background);

        final DrawerLayout drawerLayout = (DrawerLayout)
                findViewById(R.id.activity_main_drawer_layout);
        uiSpec_.navigationView = (NavigationView) findViewById(R.id.activity_main_navigation_view);
        uiSpec_.navigationView.setNavigationItemSelectedListener
                (new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {

                        // NOTE(doyle): Disable search first, since this will return the playlist
                        // view to the original view first, then apply the new view
                        if (playSpec_.searchMode) {
                            uiSpec_.playBarItems.searchButton.performClick();
                            Debug.CAREFUL_ASSERT(!playSpec_.searchMode, this, "Search " +
                                    "mode did not toggle on menu item click");
                        }

                        switch (item.getItemId()) {
                            case R.id.menu_main_drawer_library: {
                                metadataFragment.changeDisplayingPlaylist
                                        (playSpec_.playingPlaylist, playSpec_.libraryList);
                                metadataFragment.updateMetadataView(FragmentType.PLAYLIST);
                            } break;

                            case R.id.menu_main_drawer_album: {
                                metadataFragment.updateMetadataView(FragmentType.ALBUM);
                            } break;

                            case R.id.menu_main_drawer_album_artist: {
                                metadataFragment.updateMetadataView(FragmentType.ALBUM_ARTIST);
                            } break;

                            case R.id.menu_main_drawer_artist: {
                                metadataFragment.updateMetadataView(FragmentType.ARTIST);
                            } break;


                            case R.id.menu_main_drawer_genre: {
                                metadataFragment.updateMetadataView(FragmentType.GENRE);
                            } break;

                            default: {
                            } break;
                        }

                        drawerLayout.closeDrawers();
                        return true;
                    }
                });

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showDebug =
                sharedPref.getBoolean(getString(R.string.internal_pref_show_debug), false);
        Debug.showDebugRenderers = showDebug;

        uiSpec_.handler = new Handler();
        uiSpec_.debugRenderer = new Debug.UiUpdateAndRender
                ("MAIN", this, uiSpec_.handler, 1, true) {
            @Override
            public void renderElements() {

                pushVariable("Amber Version", AMBER_VERSION);
                pushClass(audioService, true, false, true);
                pushClass(this, true, false, true);
                pushVariable("Service Bound", serviceBound);

                if (metadataFragment == null) {
                    pushText("metadataFragment: NULL");
                } else {
                    if (metadataFragment.isInit()) {
                        pushText("metadataFragment: INIT");
                    } else {
                        pushText("metadataFragment: NOT-INIT");
                    }
                }

                Playlist returnFromSearchPlaylist = playSpec_.returnFromSearchPlaylist;
                if (returnFromSearchPlaylist != null) {
                    pushText("=======================================================================");
                    pushVariable("Return Search Playlist", returnFromSearchPlaylist.name);
                    pushVariable("Return Search Playlist Size", returnFromSearchPlaylist.contents.size());
                    pushVariable("Return Search Index", returnFromSearchPlaylist.index);
                }

                // TODO(doyle): Revise. Is messy but works
                Playlist playingPlaylist = playSpec_.playingPlaylist;
                if (metadataFragment != null && metadataFragment.isInit()) {
                    Playlist displayingPlaylist = metadataFragment.playlistUiSpec.displayingPlaylist;
                    if (displayingPlaylist != null) {

                        if (playingPlaylist != null) {
                            if (playingPlaylist == displayingPlaylist) {
                                pushText("=======================================================================");
                                pushText("Active + Displaying");
                                pushVariable("Playlist", playingPlaylist.name);
                                pushVariable("Playlist Size", playingPlaylist.contents.size());
                                pushVariable("Index", playingPlaylist.index);

                            } else {
                                pushText("=======================================================================");
                                pushVariable("Active Playlist", playingPlaylist.name);
                                pushVariable("Active Playlist Size", playingPlaylist.contents.size());
                                pushVariable("Active Index", playingPlaylist.index);

                                pushText("=======================================================================");
                                pushVariable("Displaying Playlist", displayingPlaylist.name);
                                pushVariable("Displaying Playlist Size", displayingPlaylist.contents.size());
                                pushVariable("Displaying Index", displayingPlaylist.index);
                            }
                        } else {
                            pushText("=======================================================================");
                            pushVariable("Displaying Playlist", displayingPlaylist.name);
                            pushVariable("Displaying Playlist Size", displayingPlaylist.contents.size());
                            pushVariable("Displaying Index", displayingPlaylist.index);
                        }
                    } else {
                        pushText("=======================================================================");
                        pushVariable("Active Playlist", playingPlaylist.name);
                        pushVariable("Active Playlist Size", playingPlaylist.contents.size());
                        pushVariable("Active Index", playingPlaylist.index);
                    }
                }

                pushText("=======================================================================");
                pushVariable("Debug FPS", (1000 / updateRateInMilliseconds));
                pushText(Debug.GENERATE_COUNTER_STRING());

                Debug.INCREMENT_COUNTER(this, "Debug Renderer");
            }
        };

        uiSpec_.playBarItems = new PlayBarItems();
        final PlayBarItems playBarItems = uiSpec_.playBarItems;
        playBarItems.playPauseButton = (Button) findViewById(R.id.play_bar_play_button);
        playBarItems.skipNextButton = (Button) findViewById(R.id.play_bar_skip_next_button);
        playBarItems.skipPreviousButton = (Button) findViewById(R.id.play_bar_skip_previous_button);

        playBarItems.seekBar = (SeekBar) findViewById(R.id.play_bar_seek_bar);
        playBarItems.seekBarUpdater = new Runnable() {
            @Override
            public void run() {
                if (playSpec_.state == PlayState.PLAYING) {
                    int position = audioService.getCurrTrackPosition();
                    uiSpec_.playBarItems.seekBar.setProgress(position);
                }
                uiSpec_.handler.postDelayed(this, 1000);
                Debug.INCREMENT_COUNTER(this, "Seek bar updater");
            }
        };

        playBarItems.shuffleButton = (Button) findViewById(R.id.play_bar_shuffle_button);
        playBarItems.repeatButton = (Button) findViewById(R.id.play_bar_repeat_button);
        playBarItems.searchButton = (Button) findViewById(R.id.play_bar_search_button);
        playBarItems.searchEditText = (EditText)
                findViewById(R.id.play_bar_search_edit_text);

        playBarItems.currentSongTextView = (TextView)
                findViewById(R.id.play_bar_current_song_text_view);

        playBarItems.currentSongTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (metadataFragment != null) {
                    metadataFragment.changeDisplayingPlaylist
                            (playSpec_.playingPlaylist, playSpec_.playingPlaylist);
                    metadataFragment.updateMetadataView(FragmentType.PLAYLIST);
                    metadataFragment.listView.setSelection(playSpec_.playingPlaylist.index);
                }
            }
        });

        if (playSpec_.searchMode) {
            if (playBarItems.searchEditText.getVisibility() != View.VISIBLE) {
                playBarItems.searchEditText.setVisibility(View.VISIBLE);
            }

            if (playBarItems.currentSongTextView.getVisibility() == View.VISIBLE) {
                playBarItems.currentSongTextView.setVisibility(View.GONE);
            }
        } else {
            if (playBarItems.searchEditText.getVisibility() == View.VISIBLE) {
                playBarItems.searchEditText.setVisibility(View.GONE);
            }

            if (playBarItems.currentSongTextView.getVisibility() != View.VISIBLE) {
                playBarItems.currentSongTextView.setVisibility(View.VISIBLE);
            }
        }

        playBarItems.playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { handleMediaInput(Input.PLAY, v); }
        });

        playBarItems.skipNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { handleMediaInput(Input.SKIP_TO_NEXT, v); }
        });

        playBarItems.skipPreviousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { handleMediaInput(Input.SKIP_TO_PREV, v); }
        });

        playBarItems.shuffleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { handleMediaInput(Input.SHUFFLE, v); }
        });

        playBarItems.repeatButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { handleMediaInput(Input.REPEAT, v); }
        });

        playBarItems.searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { handleMediaInput(Input.SEARCH, v); }
        });

        playBarItems.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            boolean splitStringAndCheckIfPrefixed(String checkString, String checkPrefix) {
                String fileTitleLowercased = checkString.toLowerCase();
                String checkPrefixLowercased = checkPrefix.toLowerCase();

                String[] parts = fileTitleLowercased.split(" ");
                for (String part : parts) {
                    if (part.startsWith(checkPrefixLowercased)) {
                        return true;
                    }
                }

                return false;
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String sequenceToString = s.toString();

                if (playSpec_.searchResultPlaylist == null) {
                    playSpec_.searchResultPlaylist = new Playlist("Search Result");
                } else {
                    playSpec_.searchResultPlaylist.contents.clear();
                    playSpec_.searchResultPlaylist.index = -1;
                }

                for (AudioFile file: playSpec_.returnFromSearchPlaylist.contents) {
                    if (splitStringAndCheckIfPrefixed(file.title, sequenceToString) ||
                            splitStringAndCheckIfPrefixed(file.artist, sequenceToString) ||
                            splitStringAndCheckIfPrefixed(file.album, sequenceToString) ||
                            splitStringAndCheckIfPrefixed(file.year, sequenceToString)) {
                        playSpec_.searchResultPlaylist.contents.add(file);
                    }
                }

                metadataFragment.changeDisplayingPlaylist(playSpec_.playingPlaylist,
                         playSpec_.searchResultPlaylist);
                metadataFragment.updateMetadataView(FragmentType.PLAYLIST);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        playBarItems.searchEditText.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                boolean result = false;

                if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                    v.clearFocus();

                    if (Debug.CAREFUL_ASSERT(playSpec_.returnFromSearchPlaylist != null, this,
                            "Exit search, return playlist is not defined")) {
                        playBarItems.searchButton.performClick();
                    }

                    result = true;
                }

                return result;
            }
        });

        playBarItems.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (serviceBound && fromUser && playSpec_.activeAudio != null) {
                    audioService.seekTo(progress);
                    playSpec_.resumePosInMs = progress;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    enum Input {
        PLAY,
        PAUSE,
        SKIP_TO_NEXT,
        SKIP_TO_PREV,
        SHUFFLE,
        REPEAT,
        SEARCH,
    }

    private void handleMediaInput (Input input, View v) {
        switch (input) {
            case PLAY: {
                if (serviceBound) {
                    if (playSpec_.state == PlayState.PLAYING) {
                        // TODO(doyle): I don't like this. playMedia() will eventually call updatePlayBarUi on callback .. should we make pause media callback as well?
                        audioService.pauseMedia();
                        updatePlayBarUi(uiSpec_);
                    } else if (playSpec_.state == PlayState.PAUSED
                            || playSpec_.state == PlayState.STOPPED) {
                        audioService.playMedia();
                    }
                }
            } break;

            case PAUSE: {
                audioService.pauseMedia();
                updatePlayBarUi(uiSpec_);
            } break;

            case SKIP_TO_PREV:
            case SKIP_TO_NEXT: {
                AudioService.PlaybackSkipDirection direction;
                if (input == Input.SKIP_TO_NEXT)
                    direction = AudioService.PlaybackSkipDirection.NEXT;
                else
                    direction = AudioService.PlaybackSkipDirection.PREVIOUS;

                if (serviceBound) {
                    audioService.skipToNextOrPrevious(direction);
                    if (metadataFragment.playlistUiSpec.displayingPlaylist == playSpec_.playingPlaylist) {
                        metadataFragment.playlistUiSpec.adapter_.notifyDataSetChanged();
                    }
                }
            } break;

            case SHUFFLE: {
                playSpec_.flags ^= PlaySpecFlags.SHUFFLE;
                if (v != null) {
                    Drawable background = ContextCompat.getDrawable
                            (v.getContext(), R.drawable.ic_shuffle);

                    boolean isShuffle = flagIsSet(playSpec_.flags, PlaySpecFlags.SHUFFLE);
                    if (isShuffle) {
                        int color = ContextCompat.getColor(v.getContext(), R.color.colorAccent);
                        background.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                    } else {
                        background.setColorFilter(null);
                    }

                    v.setBackground(background);

                    SharedPreferences sharedPref =
                            PreferenceManager.getDefaultSharedPreferences(v.getContext());
                    sharedPref.edit().putBoolean(getString(R.string.internal_pref_shuffle_mode),
                            isShuffle).apply();
                }
            } break;

            case REPEAT: {
                playSpec_.flags ^= PlaySpecFlags.REPEAT;

                if (v != null) {
                    Drawable background = ContextCompat.getDrawable
                            (v.getContext(), R.drawable.ic_repeat);

                    boolean isRepeat = flagIsSet(playSpec_.flags, PlaySpecFlags.REPEAT);
                    if (isRepeat) {
                        int color = ContextCompat.getColor(v.getContext(), R.color.colorAccent);
                        background.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                    } else {
                        background.setColorFilter(null);
                    }

                    v.setBackground(background);

                    SharedPreferences sharedPref =
                            PreferenceManager.getDefaultSharedPreferences(v.getContext());
                    sharedPref.edit().putBoolean(getString(R.string.internal_pref_repeat_mode),
                            isRepeat).apply();
                }
            } break;

            case SEARCH: {
                playSpec_.searchMode = !playSpec_.searchMode;

                PlayBarItems playBarItems = uiSpec_.playBarItems;
                EditText searchEditText = playBarItems.searchEditText;
                TextView currentSongTextView = playBarItems.currentSongTextView;

                Drawable background = ContextCompat.getDrawable
                        (v.getContext(), R.drawable.ic_magnify);

                if (playSpec_.searchMode) {
                    playSpec_.returnFromSearchPlaylist =
                            metadataFragment.playlistUiSpec.displayingPlaylist;

                    searchEditText.setVisibility(View.VISIBLE);
                    currentSongTextView.setVisibility(View.GONE);

                    searchEditText.setText("");
                    searchEditText.requestFocus();

                    int color = ContextCompat.getColor(v.getContext(), R.color.colorAccent);
                    background.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                } else {
                    searchEditText.setVisibility(View.GONE);
                    currentSongTextView.setVisibility(View.VISIBLE);

                    background.setColorFilter(null);
                    metadataFragment.changeDisplayingPlaylist(playSpec_.playingPlaylist,
                            playSpec_.returnFromSearchPlaylist);
                    metadataFragment.updateMetadataView(FragmentType.PLAYLIST);

                    playSpec_.returnFromSearchPlaylist = null;
                }

                v.setBackground(background);
            } break;

            default: {
            } break;
        }

    }

    /***********************************************************************************************
     * FRAGMENT LIFECYCLE BEHAVIOUR
     **********************************************************************************************/
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

        updatePlayBarUi(uiSpec_);
        Debug.TOAST(this, "Activity started", Toast.LENGTH_SHORT);
    }

    @Override
    public void onPause() {
        super.onPause();
        Debug.TOAST(this, "Activity paused", Toast.LENGTH_SHORT);
    }

    @Override
    public void onResume() {
        super.onResume();
        Debug.TOAST(this, "Activity resumed", Toast.LENGTH_SHORT);
    }

    @Override
    public void onStop() {
        super.onStop();
        Debug.TOAST(this, "Activity stopped", Toast.LENGTH_SHORT);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Debug.LOG_D(this, Debug.GENERATE_COUNTER_STRING());

        if (serviceBound) { unbindService(audioConnection); }
        unregisterReceiver(updateUiReceiver);
        metadataFragment = null;

        if (getAudioFromDevice != null) {
            getAudioFromDevice.cancel(true);
            getAudioFromDevice = null;
        }

        Debug.TOAST(this, "Activity destroyed", Toast.LENGTH_SHORT);
    }

    @Override
    public void onBackPressed() {
        if (getFragmentManager().getBackStackEntryCount() == 0) {
            // TODO(doyle): Add notion of home screen and return to home view
            if (metadataFragment.getFragmentType() != FragmentType.PLAYLIST) {
                metadataFragment.updateMetadataView(FragmentType.PLAYLIST);

                Menu sideMenu = uiSpec_.navigationView.getMenu();
                for (int i = 0; i < sideMenu.size(); i++) {
                    MenuItem item = sideMenu.getItem(i);
                    if (item.getTitle().equals("Library")) {
                        item.setChecked(true);
                    }
                }

            } else {
                super.onBackPressed();
            }
        } else {
            getFragmentManager().popBackStack();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        if (!Debug.DEBUG_MODE) {
            menu.removeItem(R.id.action_debug_overlay);
        } else {
            boolean debugOverlayEnabled
                    = sharedPref.getBoolean(getString(R.string.internal_pref_show_debug)
                    , false);

            MenuItem debugOverlayCheckbox = menu.findItem(R.id.action_debug_overlay);
            if (Debug.CAREFUL_ASSERT(debugOverlayCheckbox != null, this,
                    "Could not find ui follows playback menu item")) {
                debugOverlayCheckbox.setChecked(debugOverlayEnabled);
            }

        }

        boolean followPlaybackEnabled
                = sharedPref.getBoolean(getString(R.string.internal_pref_ui_follows_playback)
                , false);

        MenuItem followPlaybackCheckbox = menu.findItem(R.id.action_ui_follow_playback);
        if (Debug.CAREFUL_ASSERT(followPlaybackCheckbox != null, this,
                "Could not find ui follows playback menu item")) {
            followPlaybackCheckbox.setChecked(followPlaybackEnabled);
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
                    Debug.showDebugRenderers= !Debug.showDebugRenderers;

                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
                sharedPref.edit().putBoolean(
                        getString(R.string.internal_pref_show_debug),
                        Debug.showDebugRenderers).apply();
            } break;

            case R.id.action_rescan_music: {
                // TODO(doyle): For now just drop all the data in the db
                AudioStorage dbHandle = AudioStorage.getHandle(this);
                playSpec_.allAudioFiles.clear();

                metadataFragment.playlistUiSpec.adapter_.notifyDataSetChanged();
                if (getAudioFromDevice != null) {
                    getAudioFromDevice.cancel(true);
                }

                getAudioFromDevice = new GetAudioFromDevice(this, playSpec_, dbHandle, true);
                getAudioFromDevice.execute();
            } break;

            case R.id.action_ui_follow_playback: {
                item.setChecked(!item.isChecked());

                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
                sharedPref.edit().putBoolean(getString(R.string.internal_pref_ui_follows_playback),
                        item.isChecked()).apply();
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

                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    queryDeviceForAudioData(playSpec_);
                else
                    Debug.LOG_I(this, "Read external storage permission request denied");
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


    /***********************************************************************************************
     * DATA FUNCTIONS
     **********************************************************************************************/
    void initAppData() {
        playSpec_ = new PlaySpec();
        serviceBound = false;
        playlistQueued = false;
        playSpec_.state = MainActivity.PlayState.STOPPED;

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
            queryDeviceForAudioData(playSpec_);
        }
    }

    private void queryDeviceForAudioData(PlaySpec playSpec) {
        AudioStorage dbHandle = AudioStorage.getHandle(this);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        boolean validateDatabase
                = sharedPref.getBoolean(getString(R.string.pref_validate_database_on_startup_key),
                false);

        boolean libraryInit
                = sharedPref.getBoolean(getString(R.string.internal_pref_library_init_key),
                false);

        if (!libraryInit) validateDatabase = true;

        if (getAudioFromDevice != null) {
            getAudioFromDevice.cancel(true);
            getAudioFromDevice = null;
        }

        getAudioFromDevice = new GetAudioFromDevice(this, playSpec, dbHandle, validateDatabase);
        getAudioFromDevice.execute();
    }

    private static class GetAudioFromDevice extends AsyncTask<Void, Void, Void> {
        final private WeakReference<MainActivity> weakActivity;
        final private WeakReference<PlaySpec> weakPlaySpec;
        final private AudioStorage dbHandle;
        final private boolean validateDatabase;

        final private MetadataFragment.DisplaySpec displaySpecToUse;

        GetAudioFromDevice(MainActivity activity, PlaySpec playSpec, AudioStorage dbHandle,
                           boolean validateDatabase) {
            this.dbHandle = dbHandle;

            this.weakActivity = new WeakReference<>(activity);
            this.weakPlaySpec = new WeakReference<>(playSpec);

            this.validateDatabase = validateDatabase;

            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activity);
            String displaySpecInJson =
                    sharedPref.getString(activity.getString(R.string.internal_pref_display_spec_key),
                            null);

            if (displaySpecInJson != null) {
                Gson gson = new Gson();
                displaySpecToUse =
                        gson.fromJson(displaySpecInJson, MetadataFragment.DisplaySpec.class);
            } else {
                displaySpecToUse = null;
            }

        }

        private AudioFile updateAndCheckAgainstDbList(Context context,
                                                      MediaMetadataRetriever retriever,
                                                      File newFile) {

            AudioFile result = null;
            if (newFile == null) {
                Debug.LOG_W(this, "File arg is null , cannot check/add against db");
                return null;
            } else if (!newFile.exists()) {
                Debug.LOG_W(this, "File does not exist, cannot check/add against db: "
                        + newFile.getAbsolutePath());
                return null;
            }

            String checkFields = AudioStorage.AudioFileEntry.KEY_PATH + " =  ? ";
            String[] checkArgs = new String[] {
                newFile.getPath(),
            };

            Uri newFileUri = Uri.fromFile(newFile);
            long newFileSizeInKb = newFile.length() / 1024;
            AudioStorage.EntryCheckResult dbCheck =
                    dbHandle.checkIfExistFromFieldWithValue(context, checkFields, checkArgs);

            if (dbCheck.result == AudioStorage.CheckResult.EXISTS) {

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

                        dbHandle.updateAudioFileInDbWithKey(context, newAudio);
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
                    dbHandle.insertAudioFileToDb(context, result);
                } else {
                    Debug.CAREFUL_ASSERT(false, this, "Error! An empty db check " +
                            "result should not occur when marked existing!");
                }

            } else if (dbCheck.result == AudioStorage.CheckResult.NOT_EXIST) {
                result = Util.extractAudioMetadata(context, retriever, newFileUri);
                dbHandle.insertAudioFileToDb(context, result);
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

        private class AudioSorter implements Comparator<AudioFile> {
            @Override
            public int compare(AudioFile a, AudioFile b) {
                try {
                    return a.title.compareTo(b.title);
                } catch (IllegalArgumentException e) {
                    Debug.LOG_D(this, a.uri.toString() + " : " + b.uri.toString());
                    ASSERT(false);
                }
                return 0;
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            PlaySpec playSpec = weakPlaySpec.get();
            if (playSpec == null) {
                Debug.LOG_W(this, "playSpec got GC'ed early, scan ending early");
                return null;
            }

            MainActivity activity = weakActivity.get();
            if (activity == null) {
                Debug.LOG_W(this, "Context got GC'ed. MediaStore not scanned");
                return null;
            }

            { // NOTE(doyle): Load whatever is in the DB first and let the user interact with that,
              // then recheck information is valid afterwards
                // TODO(doyle): Use the sort param in query from db
                List<AudioFile> fileListFromDb = dbHandle.getAllAudioFiles(activity);
                List<Playlist> playlistListFromDb = dbHandle.getAllPlaylists(activity);

                if (fileListFromDb != null) {
                    if (fileListFromDb.size() > 0) {
                        playSpec.allAudioFiles.clear();
                        playSpec.allAudioFiles.addAll(fileListFromDb);

                        Collections.sort(playSpec.allAudioFiles, new AudioSorter());
                    }
                }

                if (playlistListFromDb != null) {
                    if (playlistListFromDb.size() > 0) {
                        playSpec.playlistList.clear();
                        playSpec.playlistList.addAll(playlistListFromDb);

                        Collections.sort(playSpec.playlistList, new Comparator<Playlist>() {
                            @Override
                            public int compare(Playlist a, Playlist b) {
                                return a.name.compareTo(b.name);
                            }
                        });
                    }
                }
            }

            if (!validateDatabase) return null;
            else publishProgress();

            {
                // After load, delete any invalid entries first- so that scanning new files has a
                // smaller list to compare to
                for (int i = 0; i < playSpec.allAudioFiles.size(); i++) {
                    AudioFile audioFile = playSpec.allAudioFiles.get(i);
                    File file = new File(audioFile.uri.getPath());
                    if (!file.exists()) {
                        dbHandle.deleteAudioFileFromDbWithKey(audioFile.dbKey);
                        playSpec.allAudioFiles.remove(audioFile);
                    }
                }

                for (int i = 0; i < playSpec.playlistList.size(); i++) {
                    Playlist playlist = playSpec.playlistList.get(i);
                    File file = new File(playlist.uri.getPath());
                    if (!file.exists()) {
                        dbHandle.deletePlaylistFromDbWithKey(playlist.dbKey);
                        playSpec.playlistList.remove(playlist);
                    }
                }
            }

            if (isCancelled()) return null;
            boolean updateLibraryWithEachBatch = false;
            if (playSpec.allAudioFiles.size() == 0) updateLibraryWithEachBatch = true;

            /* Start scanning from device */
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            ContentResolver musicResolver = activity.getContentResolver();
            Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);

            final int updateThreshold = 20;
            int updateCounter = 0;
            List<AudioFile> fileBatch = new ArrayList<>();
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

                            AudioFile result = updateAndCheckAgainstDbList(activity, retriever, file);

                            if (updateLibraryWithEachBatch) {
                                fileBatch.add(result);
                                if (updateCounter++ > updateThreshold) {
                                    playSpec.allAudioFiles.addAll(fileBatch);
                                    fileBatch.clear();

                                    publishProgress();
                                    updateCounter = 0;
                                    if (isCancelled()) return null;
                                }
                            }

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
                    getDefaultSharedPreferences(activity);
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
                        AudioFile result = updateAndCheckAgainstDbList(activity, retriever, file);

                        if (updateLibraryWithEachBatch) {
                            fileBatch.add(result);
                            if (updateCounter++ > updateThreshold) {
                                playSpec.allAudioFiles.addAll(fileBatch);
                                fileBatch.clear();

                                publishProgress();
                                updateCounter = 0;
                                if (isCancelled()) return null;
                            }
                        }

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

            // TODO(doyle): We ignore the rest because we will replace all entries with the DB entries. This handles invalid file entries
            // By completely nuking the in memory representation since the DB is guaranteed to be correct
            fileBatch.clear();

            List<AudioFile> filesFromDb = dbHandle.getAllAudioFiles(activity);
            playSpec.allAudioFiles.clear();
            playSpec.allAudioFiles.addAll(filesFromDb);
            List<AudioFile> allAudioFiles = playSpec.allAudioFiles;

            Collections.sort(playSpec.allAudioFiles, new AudioSorter());

            publishProgress();
            if (isCancelled()) return null;

            // TODO(doyle): On resort the playlist index becomes invalid.
            /* Sort list */
            Collections.sort(allAudioFiles, new AudioSorter());

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

                        AudioFile file = updateAndCheckAgainstDbList(activity, retriever,
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

                        if (index == -1) {
                            Debug.LOG_W(this, "Could not find file, excluded from playlist: "
                                            + findFile.uri.getPath());
                        } else {
                            newPlaylist.contents.add(allAudioFiles.get(index));
                        }

                    }
                    if (isCancelled()) return null;

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
            playSpec.playlistList = dbHandle.getAllPlaylists(activity);
            return null;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }

        private void updateMetadataFragment(MainActivity activity, PlaySpec playSpec) {
            MetadataFragment fragment = activity.metadataFragment;
            if (!fragment.isInit()) {

                Playlist playlistToUse = playSpec.libraryList;
                boolean displaySpecIsStillValid = false;

                if (displaySpecToUse != null) {

                    // TODO(doyle): Unify notion of library as a playlist included in the playlist list
                    // i.e. a virtual playlist "concept" which does not get stored into the db or validated against
                    // like playlists built at runtime or something

                    // NOTE(doyle): A key of 0 is invalid in DBs, as they start from 1. We use it to
                    // store out universal playlist, the library list
                    if (displaySpecToUse.playlistKey == 0) {
                        displaySpecIsStillValid = true;
                    } else {
                        for (Playlist playlist : playSpec.playlistList) {
                            if (playlist.dbKey == displaySpecToUse.playlistKey
                                    && displaySpecToUse.type == FragmentType.PLAYLIST) {
                                displaySpecIsStillValid = true;
                                playlistToUse = playlist;
                                break;
                            }
                        }
                    }
                }
                playSpec.playingPlaylist = playlistToUse;
                activity.enqueueToPlayer(false);

                if (displaySpecIsStillValid) {
                    fragment.init(activity, activity.playSpec_.allAudioFiles,
                            FragmentType.PLAYLIST, playlistToUse,
                            activity.uiSpec_.toolbar, displaySpecToUse);
                } else {
                    fragment.init(activity, activity.playSpec_.allAudioFiles,
                            FragmentType.PLAYLIST, playlistToUse,
                            activity.uiSpec_.toolbar);
                }
            }
            fragment.updateMetadataView(FragmentType.PLAYLIST);

            Intent broadcast = new Intent(MainActivity.BROADCAST_UPDATE_UI);
            activity.sendBroadcast(broadcast);
        }

        @Override
        protected void onProgressUpdate(Void... args) {
            super.onProgressUpdate();
            MainActivity activity = weakActivity.get();
            PlaySpec playSpec = weakPlaySpec.get();
            if (activity != null && playSpec != null) {
                // TODO(doyle): Duplicated on exit. Since if we also validate against db, then allow playlist to be updated during update
                // instead of waiting until entire db load is validated (which may take awhile)
                updateMetadataFragment(activity, playSpec);
            }
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            MainActivity activity = weakActivity.get();
            PlaySpec playSpec = weakPlaySpec.get();
            if (activity == null || playSpec == null) {
                Debug.LOG_W(this, "Acitivty or playSpec got GC'ed, library init key not written. " +
                        "Another rescan needed");
            } else {
                updateMetadataFragment(activity, playSpec);

                SharedPreferences sharedPref =
                        PreferenceManager.getDefaultSharedPreferences(activity);
                String libraryInitKey = activity.getResources().
                        getString(R.string.internal_pref_library_init_key);
                sharedPref.edit().putBoolean(libraryInitKey, true).apply();

                if (Debug.DEBUG_MODE) {
                    Toast.makeText(activity, "Library Initialisation has been confirmed",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /***********************************************************************************************
     * UI FUNCTIONS
     **********************************************************************************************/
    private BroadcastReceiver updateUiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Debug.LOG_D(this, "Ui update request received");
            updateUiData_(uiSpec_, playSpec_);
        }
    };

    private void registerUpdateUiReceiver(BroadcastReceiver receiver) {
        IntentFilter intentFilter = new IntentFilter(MainActivity.BROADCAST_UPDATE_UI);
        registerReceiver(receiver, intentFilter);
    }

    void updateUiData_(UiSpec uiSpec, PlaySpec playSpec) {
        Debug.INCREMENT_COUNTER(this);
        updateSideNavWithPlaylists(playSpec, uiSpec);
        if (metadataFragment != null) {
            metadataFragment.updateMetadataView(FragmentType.PLAYLIST);
        }
    }

    void updatePlayBarUi(final UiSpec uiSpec) {
        Debug.INCREMENT_COUNTER(this);

        if (uiSpec == null) {
            CAREFUL_ASSERT(false, this, "uiSpec is null");
            return;
        }

        final PlayBarItems playBarItems = uiSpec.playBarItems;
        if (serviceBound && playSpec_.activeAudio != null) {
            int backgroundRes;
            switch(playSpec_.state) {
                case PLAYING: {
                    if (!playBarItems.seekBarIsRunning) {
                        runOnUiThread(playBarItems.seekBarUpdater);
                        playBarItems.seekBarIsRunning = true;
                    }

                    int max = audioService.getTrackDuration();
                    int position = audioService.getCurrTrackPosition();

                    playBarItems.seekBar.setMax(max);
                    playBarItems.seekBar.setProgress(position);

                    String artist = playSpec_.activeAudio.artist;
                    String title = playSpec_.activeAudio.title;
                    playBarItems.currentSongTextView.setText(artist + " - " + title);

                    backgroundRes = R.drawable.ic_pause;
                } break;

                case PAUSED:
                case STOPPED: {
                    if (playBarItems.seekBarIsRunning) {
                        uiSpec.handler.removeCallbacks(playBarItems.seekBarUpdater);
                        playBarItems.seekBarIsRunning = false;
                    }

                    backgroundRes = R.drawable.ic_play;
                } break;

                default: {
                    Debug.CAREFUL_ASSERT(false, this, "Audio service state not handled, " +
                            "playPauseButton forcible set to play icon: "
                            + playSpec_.state.toString());

                    // NOTE(doyle): On careful assert fall-through set icon to something sensible
                    backgroundRes = R.drawable.ic_play;
                } break;
            }
            playBarItems.playPauseButton.setBackgroundResource(backgroundRes);
        } else {
            playBarItems.playPauseButton.setBackgroundResource(R.drawable.ic_play);
        }

    }

    final int MENU_PLAYLIST_GROUP_ID = 1000;
    private void updateSideNavWithPlaylists(final PlaySpec playSpec, UiSpec uiSpec) {
        final List<Playlist> playlistList = playSpec.playlistList;
        Menu sideMenu = uiSpec.navigationView.getMenu();

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

            // TODO(doyle): Recycle old menu items
            MenuItem playlistMenuItem = playlistSubMenu.add
                    (MENU_PLAYLIST_GROUP_ID, uniqueId, Menu.NONE, playlist.name);
            playlist.menuId = playlistMenuItem.getItemId();
            playlistMenuItem.setOnMenuItemClickListener
                    (new PlaylistMenuItemClick(uiSpec, playSpec, playlistList));
        }
    }

    public class PlaylistMenuItemClick implements MenuItem.OnMenuItemClickListener {
        private UiSpec uiSpec;
        private PlaySpec playSpec;
        private List<Playlist> playlistList;

        PlaylistMenuItemClick(UiSpec uiSpec, PlaySpec playSpec, List<Playlist> playlistList) {
            this.playSpec = playSpec;
            this.uiSpec = uiSpec;
            this.playlistList = playlistList;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            // TODO(doyle): Improve linear search maybe? Typical use case doesn't have many playlists
            for (int i = 0; i < playlistList.size(); i++) {
                Playlist newPlaylist = playlistList.get(i);
                if (newPlaylist.menuId == item.getItemId()) {
                    metadataFragment.changeDisplayingPlaylist(playSpec.playingPlaylist,
                            newPlaylist);
                    metadataFragment.updateMetadataView(FragmentType.PLAYLIST);
                    break;
                }
            }

            item.setChecked(true);
            return false;
        }
    }

    /***********************************************************************************************
     * AUDIO METADATA MANIPULATION
     **********************************************************************************************/
    // NOTE(doyle): When we request a song to be played, the media player has to be prepared first!
    // Playback does not happen until it is complete! We know when playback is complete in the
    // callback
    public void audioFileClicked (Playlist newPlaylist) {

        if (!playSpec_.searchMode) {
            if (playSpec_.playingPlaylist != newPlaylist) {
                playSpec_.playingPlaylist.index = -1;
                playSpec_.playingPlaylist = newPlaylist;
            }
        } else {
            // NOTE(doyle): In search mode, we don't want to replace the active playlist with the
            // search result. Let it play in place from the playlist we're searching in. So we need
            // to mark the file to be highlighted on the original playlist we're searching in
            long songToPlayKey = newPlaylist.contents.get(newPlaylist.index).dbKey;

            // TODO(doyle): Is linear search here, profile
            for (int i = 0; i < playSpec_.returnFromSearchPlaylist.contents.size(); i++) {
                AudioFile file = playSpec_.returnFromSearchPlaylist.contents.get(i);
                if (file.dbKey == songToPlayKey) {
                    playSpec_.returnFromSearchPlaylist.index = i;
                    break;
                }
            }
        }

        enqueueToPlayer(true);
    }

    void enqueueToPlayer(boolean playImmediately) {
        if (!serviceBound) {
            LOG_D(this, "Rebinding audio service");
            Intent playerIntent = new Intent(this, AudioService.class);
            startService(playerIntent);
            bindService(playerIntent, audioConnection, Context.BIND_AUTO_CREATE);

            // NOTE(doyle): Queue playlist whereby onServiceConnected will detect and begin playing
            playlistQueued = true;
        } else {
            audioService.queuedNewSong = true;
            if (playImmediately) audioService.playMedia();
        }
    }

    public class AudioServiceResponse implements AudioService.Response {
        UiSpec uiSpec;
        PlaySpec playSpec;
        Context context;

        AudioServiceResponse(Context context, UiSpec uiSpec, PlaySpec playSpec) {
            Debug.CAREFUL_ASSERT(uiSpec != null && playSpec != null, this, "Arguments are null");

            this.context = context;
            this.uiSpec = uiSpec;
            this.playSpec = playSpec;
        }

        @Override
        public void audioHasStartedPlayback(boolean skippedToNewSong) {
            updatePlayBarUi(uiSpec);
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
            boolean followPlaybackEnabled
                    = sharedPref.getBoolean(getString(R.string.internal_pref_ui_follows_playback)
                    , false);

            if (metadataFragment != null) {
                if (metadataFragment.getFragmentType() == FragmentType.PLAYLIST &&
                        metadataFragment.playlistUiSpec.displayingPlaylist
                                == playSpec_.playingPlaylist) {

                    metadataFragment.updateMetadataView(FragmentType.PLAYLIST);
                    if (followPlaybackEnabled && skippedToNewSong) {
                        metadataFragment.listView.setSelection(playSpec_.playingPlaylist.index);
                    }
                }
            }

        }
    }
}
