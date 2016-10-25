package com.dqnt.amber;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import com.dqnt.amber.PlaybackData.AudioFile;

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

    public enum PlayState {
        PLAYING,
        PAUSED,
        STOPPED,
    }

    private static final int NOTIFICATION_ID = 101;
    private final IBinder binder = new LocalBinder();

    // NOTE(doyle): Interface between client and service. Allows client to get service instance from
    // the binder
    public class LocalBinder extends Binder {
        AudioService getService() { return AudioService.this; }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private final int NOTIFY_ID = 1;
    private AudioManager audioManager;
    PlayState playState;
    boolean shuffle;
    boolean repeat;

    private List<AudioFile> playlist;
    private int playlistIndex;
    AudioFile activeAudio;

    private MediaPlayer player;
    int resumePosition;

    // NOTE(doyle): The system calls this method when the service is first created, to perform
    // one-time setup procedures (before it calls either onStartCommand() or onBind()). If the
    // service is already running, this method is not called.
    public void onCreate() {
        super.onCreate();

        // NOTE(doyle): Manage incoming phone calls during playback
        registerCallStateListener();
        // TODO(doyle): Revise broadcast audio
        // registerPlayNewAudio();
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
            // buildNotification(PlayState.PLAYING);
        }

        MediaButtonReceiver.handleIntent(mediaSessionCompat, intent);

        playState = PlayState.STOPPED;
        handleIncomingActions(intent);
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
        // Attach Callback to receive MediaSession updates
        mediaSessionCompat.setCallback(new MediaSessionCompat.Callback() {
            // Implement callbacks
            @Override
            public void onPlay() {
                super.onPlay();
                playMedia();
                buildNotification(PlayState.PLAYING);
            }

            @Override
            public void onPause() {
                super.onPause();
                pauseMedia();
                buildNotification(PlayState.PAUSED);
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                skipToNextOrPrevious(PlaybackSkipDirection.NEXT);
                updateMetadata();
                buildNotification(PlayState.PLAYING);
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                skipToNextOrPrevious(PlaybackSkipDirection.PREVIOUS);
                updateMetadata();
                buildNotification(PlayState.PLAYING);
            }

            @Override
            public void onStop() {
                super.onStop();
                removeNotification();
                //Stop the service
                stopSelf();
            }

            @Override
            public void onSeekTo(long position) {
                super.onSeekTo(position);
            }
        });
    }

    private void buildNotification(PlayState status) {
        int notificationAction = android.R.drawable.ic_media_pause;
        PendingIntent playPauseAction = null;

        /* Build a new notification according to current playState of the player */
        if (status == PlayState.PLAYING) {
            notificationAction = android.R.drawable.ic_media_pause;
            playPauseAction = playbackAction(1);
        } else if (status == PlayState.PAUSED) {
            notificationAction = android.R.drawable.ic_media_play;
            playPauseAction = playbackAction(0);
        }

        // Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.image);

        /* Create a new notification */
        NotificationCompat.Builder builder = (NotificationCompat.Builder)
                new NotificationCompat.Builder(this)
                .setShowWhen(false)
                .setStyle(new NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSessionCompat.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .setColor(getResources().getColor(R.color.colorPrimary))
                // .setLargeIcon(largeIcon)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                .setContentText(activeAudio.artist)
                .setContentTitle(activeAudio.album)
                .setContentInfo(activeAudio.title)
                .addAction(android.R.drawable.ic_media_previous, "previous", playbackAction(3))
                .addAction(notificationAction, "pause", playPauseAction)
                .addAction(android.R.drawable.ic_media_next, "next", playbackAction(2));

        ((NotificationManager)getSystemService
                (Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, builder.build());
    }

    private void removeNotification() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private PendingIntent playbackAction(int actionNumber) {
        Intent playbackAction = new Intent(this, AudioService.class);
        switch (actionNumber) {
            case 0:
                // Play
                playbackAction.setAction(ACTION_PLAY);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 1:
                // Pause
                playbackAction.setAction(ACTION_PAUSE);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 2:
                // Next track
                playbackAction.setAction(ACTION_NEXT);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 3:
                // Previous track
                playbackAction.setAction(ACTION_PREVIOUS);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            default:
                break;
        }
        return null;
    }

    private void handleIncomingActions(Intent playbackAction) {
        if (playbackAction == null || playbackAction.getAction() == null) return;

        String actionString = playbackAction.getAction();
        if (actionString.equalsIgnoreCase(ACTION_PLAY)) {
            transportControls.play();
        } else if (actionString.equalsIgnoreCase(ACTION_PAUSE)) {
            transportControls.pause();
        } else if (actionString.equalsIgnoreCase(ACTION_NEXT)) {
            transportControls.skipToNext();
        } else if (actionString.equalsIgnoreCase(ACTION_PREVIOUS)) {
            transportControls.skipToPrevious();
        } else if (actionString.equalsIgnoreCase(ACTION_STOP)) {
            transportControls.stop();
        }
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
        /* Init media player */
        player = new MediaPlayer();

        /* Register listeners to this class's implementation */
        player.setOnBufferingUpdateListener(this);
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);
        player.setOnInfoListener(this);
        player.setOnPreparedListener(this);
        player.setOnSeekCompleteListener(this);
        player.reset();
        resumePosition = 0;

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
        Debug.LOG_D(this, "Service destroyed");

        // NOTE(doyle): Stop notification running in foreground
        stopForeground(true);
        if (player != null) {
            stopMedia();
            player.release();
        }

        // TODO(doyle):  Think about focus only requested during playback
        removeAudioFocus();

        removeNotification();

        // TODO(doyle): Revise broadcast audio
        // unregisterReceiver(playNewAudioReceiver);

        unregisterReceiver(becomingNoisyReceiver);
        unregisterCallStateListener(phoneStateListener);
    }

    private boolean requestAudioFocus() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);


        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return true;
        }
        return false;
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
    public interface Response { void audioHasStartedPlayback(); }
    Response listener;

    // NOTE(doyle): Triggers when track finished/skipped or new track is selected
    @Override
    public void onCompletion(MediaPlayer mp) {
        // stopMedia();
        // stopSelf();
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
        /*
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // NOTE(doyle): Allow user to be redirected back to main activity on notification click
        PendingIntent pendingIntent = PendingIntent.getActivity
                (this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = new Notification.Builder(this);
        builder.setContentIntent(pendingIntent)
                .setTicker(file.title)
                .setOngoing(true)
                .setContentTitle("Playing")
                .setContentText(file.title);
        Notification notification = builder.build();

        startForeground(NOTIFY_ID, notification);
        */
        startPlayback();
    }


    @Override
    public void onAudioFocusChange(int focusState) {
        if (player == null) return;

        //Invoked when the audio focus of the system is updated.
        switch (focusState) {
            case AudioManager.AUDIOFOCUS_GAIN: {
                /* Indicate media session ready to receive media commands */
                mediaSessionCompat.setActive(true);
                // resume playback
                player.setVolume(1.0f, 1.0f);
                playMedia();
                break;
            }
            case AudioManager.AUDIOFOCUS_LOSS: {
                mediaSessionCompat.setActive(false);
                // Lost focus for an amount of time stop playback and release media player
                stopMedia();
                player.release();
                player = null;
                break;
            }
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT: {
                // Lost focus for a short time, but we have to stop playback. We don't release the
                // media player because playback is likely to resume
                pauseMedia();
                break;
            }

            // Lost focus for a short time, but it's ok to keep playing at an attenuated level
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: {
                if (player.isPlaying()) player.setVolume(0.1f, 0.1f);
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
        if (validatePlaylist(playlist, index)) {
            this.playlist = playlist;
            this.playlistIndex = index;
            activeAudio = playlist.get(playlistIndex);
            queuedNewSong = true;
        }
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
        player.start();

        playState = PlayState.PLAYING;
        listener.audioHasStartedPlayback();
    }

    private void controlPlayback(PlayCommand command) {
        switch (command) {
            case PLAY: {
                if (!requestAudioFocus()) {
                    stopSelf();
                    Debug.LOG_W(this, "Could not request audio focus");
                    return;
                }

                if (playState == PlayState.PAUSED && !queuedNewSong) {
                    player.seekTo(resumePosition);
                    startPlayback();
                } else {
                    if (player == null) {
                        initMediaPlayer();
                        Debug.INCREMENT_COUNTER(this, "Player reinit from null");
                    }

                    try {
                        player.reset();
                        player.setDataSource(getApplicationContext(), activeAudio.uri);
                        player.prepareAsync();
                        buildNotification(PlayState.PLAYING);
                        queuedNewSong = false;
                        resumePosition = 0;
                        return;
                    } catch (IOException e) {
                        e.printStackTrace();
                        Debug.CAREFUL_ASSERT(false, this, "IOException, could not " +
                                "set file as source: " + activeAudio.uri.getPath());
                        Debug.TOAST(this, "Stopping service, file load error", Toast.LENGTH_SHORT);
                        stopSelf();
                        return;
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
                    resumePosition = player.getCurrentPosition();
                    unregisterReceiver(becomingNoisyReceiver);
                    return;
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
                    return;
                }
            } break;
        }
    }



    enum PlaybackSkipDirection {
        NEXT,
        PREVIOUS,
    }

    boolean skipToNextOrPrevious(PlaybackSkipDirection direction) {
        if (!validatePlaylist(this.playlist, this.playlistIndex)) return false;

        // NOTE(doyle): Repeat takes precedence over shuffle
        if (repeat) {
        } else if (shuffle) {
            int newIndex = playlistIndex;
            while (newIndex == playlistIndex) {
                newIndex = new Random().nextInt(playlist.size());
                Debug.INCREMENT_COUNTER(this, "Shuffle random index failed count");
            }

            playlistIndex = newIndex;
        } else {
            if (direction == PlaybackSkipDirection.NEXT) {
                if (++playlistIndex >= playlist.size()) playlistIndex = 0;
            } else {
                if (--playlistIndex < 0) playlistIndex = playlist.size() - 1;
            }
        }

        queuedNewSong = true;
        activeAudio = playlist.get(playlistIndex);
        playMedia();

        return true;
    }

    int getCurrTrackPosition() {
        int result = 0;
        if (Debug.CAREFUL_ASSERT(player != null, this, "Player is null")) {
            result = player.getCurrentPosition();
        }

        return result;
    }

    int getTrackDuration() {
        int result = 0;
        if (Debug.CAREFUL_ASSERT(player != null, this, "Player is null")) {
            result = player.getDuration();
        }

        return result;
    }

    void seekTo(int msec) {
        if (Debug.CAREFUL_ASSERT(player != null, this, "Player is null")) {
            player.seekTo(msec);
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
            buildNotification(PlayState.PAUSED);
        }
    };

    private void registerBecomingNoisyReceiver() {
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);
    }

    // TODO(doyle): Do we need a broadcaster for playing audio? Or just use play function directly
    /*
    private BroadcastReceiver playNewAudioReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(this, "Received play new audio intent");
            prepareSongAndPlay();
        }
    };

    private void registerPlayNewAudio() {
        IntentFilter intentFilter = new IntentFilter(MainActivity.BROADCAST_PLAY_NEW_AUDIO);
        registerReceiver(playNewAudioReceiver, intentFilter);
    }
    */

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

    private void unregisterCallStateListener(PhoneStateListener listener) {
        if (listener != null)
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE);
    }

}
