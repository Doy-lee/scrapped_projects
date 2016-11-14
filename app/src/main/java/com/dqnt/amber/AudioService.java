package com.dqnt.amber;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v7.app.NotificationCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import com.dqnt.amber.Models.AudioFile;

// TODO(doyle): Audio focus/audio manager API to manage what happens to sound on lose focus
public class AudioService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener,
        MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnInfoListener,
        MediaPlayer.OnBufferingUpdateListener, AudioManager.OnAudioFocusChangeListener {

    public static final String ACTION_PLAY = "com.dqnt.amber.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.dqnt.amber.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "com.dqnt.amber.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "com.dqnt.amber.ACTION_NEXT";
    public static final String ACTION_STOP = "com.dqnt.amber.ACTION_STOP";

    private MediaSessionManager mediaSessionManager;
    private MediaSessionCompat mediaSessionCompat;
    private MediaControllerCompat.TransportControls transportControls;
    private AudioManager audioManager;

    enum PlayState {
        PLAYING,
        ADVANCE_TO_NEW_AUDIO,
        PAUSED,
        STOPPED,
    }

    private static final int NOTIFICATION_ID = 101;
    private final IBinder binder = new LocalBinder();

    private MediaPlayer player;
    private List<AudioFile> playlist;
    private int playlistIndex;

    // NOTE(doyle): Interface between client and service. Allows client to get service instance from
    // the binder
    public class LocalBinder extends Binder {
        AudioService getService() { return AudioService.this; }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    PlayState playState;
    boolean shuffle;
    boolean repeat;
    boolean wasPlayingBeforeAudioDuck;
    AudioFile activeAudio;
    int resumePosInMs;

    // NOTE(doyle): The system calls this method when the service is first created, to perform
    // one-time setup procedures (before it calls either onStartCommand() or onBind()). If the
    // service is already running, this method is not called.
    public void onCreate() {
        super.onCreate();
        activeAudio = null;
        // NOTE(doyle): Manage incoming phone calls during playback
        registerCallStateListener();
    }

    // NOTE(doyle): Called when an activity requests the service be started
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mediaSessionManager == null) {
            try {
                initMediaSession();
            } catch (RemoteException e) {
                e.printStackTrace();
                stopSelf();
            }
        }
        MediaButtonReceiver.handleIntent(mediaSessionCompat, intent);

        playState = PlayState.STOPPED;
        { // Handle Incoming Intent Actions
            if (intent != null && intent.getAction() != null) {
                String actionString = intent.getAction();

                if (actionString.equalsIgnoreCase(ACTION_PLAY))
                    transportControls.play();

                else if (actionString.equalsIgnoreCase(ACTION_PAUSE))
                    transportControls.pause();

                else if (actionString.equalsIgnoreCase(ACTION_NEXT))
                    transportControls.skipToNext();

                else if (actionString.equalsIgnoreCase(ACTION_PREVIOUS))
                    transportControls.skipToPrevious();

                else if (actionString.equalsIgnoreCase(ACTION_STOP))
                    transportControls.stop();
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    private void initMediaSession() throws RemoteException {
        if (mediaSessionManager != null) return;

        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        mediaSessionCompat = new MediaSessionCompat(getApplicationContext(), "AudioPlayer");
        transportControls = mediaSessionCompat.getController().getTransportControls();

        /* Set media session to handle control commands through the call back */
        mediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS |
                                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS);
    }

    void setNotificationMediaCallbacks(MediaSessionCompat.Callback callback) {
        // Allow connecting client to attach callback

        // NOTE(doyle): We call this externally so that all play services can only be executed
        // externally from a connecting client. This removes any notion of audio service controlling
        // itself.
        mediaSessionCompat.setCallback(callback);
    }


    private enum NotificationAction {
        PLAY,
        PAUSE,
        SKIP_NEXT,
        SKIP_PREV,
    }

    private void buildNotification(PlayState status) {
        PendingIntent playPauseAction = null;
        /* Build a new notification according to current playState of the player */
        int playPauseNotificationResource = -1;
        if (status == PlayState.PLAYING) {
            playPauseNotificationResource = R.drawable.ic_pause;
            playPauseAction = playbackAction(NotificationAction.PAUSE);
        } else if (status == PlayState.PAUSED || status == PlayState.STOPPED) {
            playPauseNotificationResource = R.drawable.ic_play;
            playPauseAction = playbackAction(NotificationAction.PLAY);
        }

        // Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.image);

        // TODO(doyle): Abstract intent return to whichever class started to make completely modular
        Intent notificationIntent = new Intent(this, MainActivity.class);
        // NOTE(doyle): Allow user to be redirected back to main activity on notification click
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent intentToSendToApp = PendingIntent.getActivity
                (this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Bitmap bitmap = null;
        if (activeAudio != null && activeAudio.bitmapUri != null) {
            bitmap = BitmapFactory.decodeFile(activeAudio.bitmapUri.getPath());
        }

        NotificationCompat.Builder builder = (NotificationCompat.Builder)
                new NotificationCompat.Builder(this)
                .setShowWhen(false)
                .setStyle(new NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSessionCompat.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .setLargeIcon(bitmap)
                .setSmallIcon(R.drawable.ic_play)
                .setContentIntent(intentToSendToApp)
                .setContentText(activeAudio.artist)
                .setContentTitle(activeAudio.title)
                .setContentInfo(activeAudio.album)
                .addAction(R.drawable.ic_skip_previous, "Skip Previous",
                        playbackAction(NotificationAction.SKIP_PREV))
                .addAction(playPauseNotificationResource, "Play/Pause", playPauseAction)
                .addAction(R.drawable.ic_skip_next, "Skip Next",
                        playbackAction(NotificationAction.SKIP_NEXT));

        Notification notification = builder.build();

        // ((NotificationManager)getSystemService
        //         (Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, notification);
        this.startForeground(NOTIFICATION_ID, notification);
    }

    private PendingIntent playbackAction(NotificationAction action) {
        Intent notificationIntent = new Intent(this, AudioService.class);
        switch (action) {
            case PLAY:
                notificationIntent.setAction(ACTION_PLAY);
                break;
            case PAUSE:
                notificationIntent.setAction(ACTION_PAUSE);
                break;
            case SKIP_NEXT:
                notificationIntent.setAction(ACTION_NEXT);
                break;
            case SKIP_PREV:
                notificationIntent.setAction(ACTION_PREVIOUS);
                break;
            default:
                Debug.LOG_D(this, "Unhandled notification action enum value: " + action.toString());
                break;
        }

        return PendingIntent.getService(this, action.ordinal(), notificationIntent, 0);
    }

    private void updateMetadata() {
        // Bitmap albumArt = BitmapFactory.decodeResource(getResources(), R.drawable.image)
        mediaSessionCompat.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, activeAudio.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, activeAudio.album)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, activeAudio.title)
                .build());
    }

    private void initMediaPlayer() {
        if (player != null) {
            Debug.LOG_E(this, "Player being re-init when already initialised");
            return;
        }

        /* Init media player */
        player = new MediaPlayer();

        /* Register listeners to this class's implementation */
        player.setOnBufferingUpdateListener(this);
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);
        player.setOnInfoListener(this);
        player.setOnPreparedListener(this);
        player.setOnSeekCompleteListener(this);
        resumePosInMs = 0;

        // NOTE(doyle): Partial wake to allow playback on screen-off
        player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Debug.LOG_D(this, "Service unbound from activity");
        return false;
    }

    @Override
    public void onDestroy() {
        endService();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        endService();
    }

    private void endService() {
        Debug.LOG_D(this, "Service destroyed");
        stopForeground(true);
        if (player != null) {
            stopMedia();
            player.release();
            player = null;
        }

        /*
        { // removeNotification(): Remove notifications that are not started in foreground
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(NOTIFICATION_ID);
        }
        */

        // unregisterCallStateListener()
        if (listener != null)
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);

    }

    private boolean requestAudioFocus() {
        if (audioManager == null) {
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }

        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);

        return (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    }

    private boolean removeAudioFocus() {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(this);
    }

    /*
     ***********************************************************************************************
     * MEDIA PLAYER INTERFACE IMPLEMENTATION
     ***********************************************************************************************
     */

    // NOTE(doyle): Callback so we know when playback has actually started, because playback is not
    // immediate. Does not start until player is "prepared()"
    public interface Response {
        void audioHasStartedPlayback(int songIndex, boolean skippedToNewSong);
    }
    Response listener;

    // NOTE(doyle): Triggers when track finished/skipped or new track is selected
    @Override
    public void onCompletion(MediaPlayer mp) {
        skipToNextOrPrevious(PlaybackSkipDirection.NEXT);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        // TODO(doyle): Look up API
        mp.reset();
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Debug.LOG_D(this, "Media error not valid for progressive playback: " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Debug.LOG_D(this, "Media error server died: " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Debug.LOG_D(this, "Media error unknown: " + extra);
                break;
            default:
                Debug.CAREFUL_ASSERT(false, this, "Media error not handled: " + extra);
                break;
        }

        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        startPlayback();
    }

    @Override
    public void onAudioFocusChange(int focusState) {
        if (player == null) return;

        //Invoked when the audio focus of the system is updated.
        switch (focusState) {
            case AudioManager.AUDIOFOCUS_GAIN: {
                /* Indicate media session ready to receive media commands */

                if (wasPlayingBeforeAudioDuck) {
                    mediaSessionCompat.setActive(true);
                    // resume playback
                    player.setVolume(1.0f, 1.f);
                    wasPlayingBeforeAudioDuck = false;
                }

                Debug.TOAST(this, "Audio focus gain, play media", Toast.LENGTH_SHORT);
                Debug.INCREMENT_COUNTER(this, "Focus gained");
                break;
            }
            case AudioManager.AUDIOFOCUS_LOSS: {
                mediaSessionCompat.setActive(false);
                // Lost focus for an amount of time stop playback and release media player
                resumePosInMs = getCurrTrackPosition();
                stopMedia();
                player.release();
                player = null;

                Debug.TOAST(this, "Audio focus lost, stop media & release player", Toast.LENGTH_SHORT);
                Debug.INCREMENT_COUNTER(this, "Focus lost");
                break;
            }
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT: {
                // Lost focus for a short time, but we have to stop playback. We don't release the
                // media player because playback is likely to resume
                pauseMedia();
                Debug.TOAST(this, "Audio focus lost transient", Toast.LENGTH_SHORT);
                Debug.INCREMENT_COUNTER(this, "Focus lost transient");
                break;
            }

            // Lost focus for a short time, but it's ok to keep playing at an attenuated level
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: {
                if (playState == PlayState.PLAYING) {
                    player.setVolume(0.1f, 0.1f);
                    wasPlayingBeforeAudioDuck = true;
                }

                Debug.TOAST(this, "Audio focus lost transient can duck", Toast.LENGTH_SHORT);
                Debug.INCREMENT_COUNTER(this, "Focus lost transient can duck");
                break;
            }
        }
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {

    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        return false;
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {}

    /*
     ***********************************************************************************************
     * PLAYBACK CONTROL
     ***********************************************************************************************
     */

    private boolean validatePlaylist(List<AudioFile> playlist, int index) {
        if (playlist == null) {
            Debug.LOG_W(this, "Playlist was null");
            return false;
        }

        if (index >= playlist.size() || index < 0) {
            Debug.LOG_W(this, "Index was out of playlist bounds");
            return false;
        }

        return true;
    }

    boolean queuedNewSong = false;
    void preparePlaylist(List<AudioFile> playlist, int index) {
        /* Queue playlist and song */
        this.playlist = playlist;
        this.playlistIndex = index;
        queuedNewSong = true;
    }

    void playMedia() { controlPlayback(PlayCommand.PLAY); }
    void stopMedia() { controlPlayback(PlayCommand.STOP); }
    void pauseMedia() { controlPlayback(PlayCommand.PAUSE); }

    private enum PlayCommand {
        PLAY,
        PAUSE,
        STOP,
    }

    private void startPlayback() {
        registerBecomingNoisyReceiver();
        player.seekTo(resumePosInMs);
        player.start();

        boolean skippedToNewSong =
                (playState == PlayState.ADVANCE_TO_NEW_AUDIO);
        playState = PlayState.PLAYING;

        listener.audioHasStartedPlayback(playlistIndex, skippedToNewSong);

        buildNotification(playState);
    }

    private void controlPlayback(PlayCommand command) {
        if (!validatePlaylist(playlist, playlistIndex)) return;
        activeAudio = playlist.get(playlistIndex);

        switch (command) {
            case PLAY: {
                if (!requestAudioFocus()) {
                    stopSelf();
                    Debug.LOG_W(this, "Could not request audio focus");
                    return;
                }

                if (playState == PlayState.PAUSED && !queuedNewSong) {
                    startPlayback();
                } else {
                    int newResumePosition = 0;

                    // NOTE(doyle): If stopped, then player is null and we need to reinit,
                    // but reinit will reset the resume position, so store it
                    if (playState == PlayState.STOPPED) {
                        newResumePosition = resumePosInMs;
                    }

                    if (player == null) {
                        initMediaPlayer();
                        Debug.INCREMENT_COUNTER(this, "Player reinit from null");
                    }

                    try {
                        player.reset();
                        player.setDataSource(getApplicationContext(), activeAudio.uri);
                        queuedNewSong = false;
                        resumePosInMs = newResumePosition;
                        player.prepareAsync();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Debug.CAREFUL_ASSERT(false, this, "IOException, could not " +
                                "set file as source: " + activeAudio.uri.getPath());
                        Debug.TOAST(this, "Stopping service, file load error", Toast.LENGTH_SHORT);
                        stopSelf();
                    }

                }
            } break;

            case PAUSE: {

                if (player == null) {
                    Debug.CAREFUL_ASSERT(false, this, "Player is null");
                    return;
                }

                if (player.isPlaying()) {
                    player.pause();
                    playState = PlayState.PAUSED;
                    resumePosInMs = player.getCurrentPosition();
                    buildNotification(playState);
                }
            } break;

            case STOP: {
                if (player == null) {
                    Debug.CAREFUL_ASSERT(false, this, "Player is null");
                    return;
                }

                if (player.isPlaying()) {
                    removeAudioFocus();
                    player.stop();
                    playState = PlayState.STOPPED;
                    unregisterReceiver(becomingNoisyReceiver);

                    buildNotification(PlayState.STOPPED);
                }
            } break;
        }
    }

    enum PlaybackSkipDirection {
        NEXT,
        PREVIOUS,
    }

    int skipToNextOrPrevious(PlaybackSkipDirection direction) {
        // NOTE(doyle): We allow playlist index to be any value, because we can normalise it
        // if the playlist is valid. In particular -1 index means a new playlist that doesn't have
        // a particular song selection yet
        if (!(playlist != null && playlist.size() > 0)) return -1;

        if (playlist.size() == 1) {
            playlistIndex = 0;
        } else if (repeat) {
            // NOTE(doyle): Repeat takes precedence over shuffle if both are set
        } else if (shuffle) {
            int newIndex = new Random().nextInt(playlist.size());
            while (newIndex == playlistIndex) {
                newIndex = new Random().nextInt(playlist.size());
                Debug.INCREMENT_COUNTER(this, "Shuffle random index failed count");
            }

            playlistIndex = newIndex;
        } else {

            if (playlistIndex == -1) {
                playlistIndex = 0;
            } else {
                if (direction == PlaybackSkipDirection.NEXT) {
                    if (++playlistIndex >= playlist.size()) playlistIndex = 0;
                } else {
                    if (--playlistIndex < 0) playlistIndex = playlist.size() - 1;
                }
            }
        }

        playState = PlayState.ADVANCE_TO_NEW_AUDIO;
        queuedNewSong = true;
        // TODO(doyle): In the event that we stop the player (i.e. audio focus loss) and then
        // skip to next song, in playMedia, the playstate is at "STOPPED", which will preserve
        // the resume position from before we stopped. Here we reset it to 0 so the new song will be
        // correct. Another way would be having another function for resuming so that we can
        // differentiate between the two. Consider it in the future.
        resumePosInMs = 0;
        playMedia();
        return playlistIndex;
    }

    int getCurrTrackPosition() {
        int result;

        if (player != null) {
            result = player.getCurrentPosition();
        } else {
            // NOTE(doyle): In this case, the player may have been released, so we just resume from
            // the last recorded resume position
            result = resumePosInMs;
        }

        return result;
    }

    int getTrackDuration() {
        int result = 0;

        if (activeAudio != null) {
            result = activeAudio.duration;
        }

        return result;
    }

    void seekTo(int msec) {
        if (player != null && playState == PlayState.PLAYING) {
            player.seekTo(msec);
        } else {
            resumePosInMs = msec;
        }
    }

    /*
     ***********************************************************************************************
     * BROADCAST LISTENER IMPLEMENTATION
     ***********************************************************************************************
     */
    private BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            pauseMedia();
        }
    };

    private void registerBecomingNoisyReceiver() {
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);
    }

    private boolean ongoingCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    private void registerCallStateListener() {
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        phoneStateListener = new PhoneStateListener() {
        @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                switch(state) {
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (player != null) {
                            pauseMedia();
                            ongoingCall = true;
                        }
                        break;

                    case TelephonyManager.CALL_STATE_IDLE:
                        if (player != null && ongoingCall) {
                            ongoingCall = false;
                            playMedia();
                        }
                        break;
                }
            }
        };

        // NOTE(doyle): Register the listener for changes to the device call playState
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }
}
