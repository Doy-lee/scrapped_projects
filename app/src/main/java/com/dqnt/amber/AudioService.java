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
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.support.v7.app.NotificationCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.IOException;
import java.util.List;

// TODO(doyle): Audio focus/audio manager API to manage what happens to sound on lose focus
public class AudioService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener,
        MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnInfoListener,
        MediaPlayer.OnBufferingUpdateListener, AudioManager.OnAudioFocusChangeListener {
    private final String TAG = AudioService.class.getName();

    public static final String ACTION_PLAY = "com.dqnt.amber.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.dqnt.amber.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "com.dqnt.amber.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "com.dqnt.amber.ACTION_NEXT";
    public static final String ACTION_STOP = "com.dqnt.amber.ACTION_STOP";

    private MediaSessionManager mediaSessionManager;
    private MediaSessionCompat mediaSessionCompat;
    private MediaControllerCompat.TransportControls transportControls;

    public enum PlaybackStatus {
        PLAYING,
        PAUSED
    }

    private static final int NOTIFICATION_ID = 101;

    private final IBinder binder = new LocalBinder();

    // NOTE(doyle): Interface between client and service. Allows client to get service instance from
    // the binder
    public class LocalBinder extends Binder {
        public AudioService getService() { return AudioService.this; }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private final int NOTIFY_ID = 1;
    private AudioManager audioManager;

    private List<AudioFile> playlist;
    private int playlistIndex;
    private AudioFile activeAudio;

    private MediaPlayer player;
    private int resumePosition;

    // NOTE(doyle): The system calls this method when the service is first created, to perform
    // one-time setup procedures (before it calls either onStartCommand() or onBind()). If the
    // service is already running, this method is not called.
    public void onCreate() {
        super.onCreate();

        // NOTE(doyle): Manage incoming phone calls during playback
        registerCallStateListener();
        registerBecomingNoisyReceiver();
        registerPlayNewAudio();
    }

    // NOTE(doyle): Called when an activity requests the service be started
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!requestAudioFocus()) stopSelf();

        if (mediaSessionManager == null) {
            try {
                initMediaSession();
                // initMediaPlayer();
            } catch (RemoteException e) {
                e.printStackTrace();
                stopSelf();
            }
            // buildNotification(PlaybackStatus.PLAYING);
        }

        handleIncomingActions(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    private void initMediaSession() throws RemoteException {
        if (mediaSessionManager != null) return;

        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        mediaSessionCompat = new MediaSessionCompat(getApplicationContext(), "AudioPlayer");
        transportControls = mediaSessionCompat.getController().getTransportControls();

        /* Indicate media session ready to receive media commands */
        mediaSessionCompat.setActive(true);

        /* Set media session to handle control commands through the call back */
        mediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        // Attach Callback to receive MediaSession updates
        mediaSessionCompat.setCallback(new MediaSessionCompat.Callback() {
            // Implement callbacks
            @Override
            public void onPlay() {
                super.onPlay();
                resumeMedia();
                buildNotification(PlaybackStatus.PLAYING);
            }

            @Override
            public void onPause() {
                super.onPause();
                pauseMedia();
                buildNotification(PlaybackStatus.PAUSED);
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                skipToNext();
                updateMetadata();
                buildNotification(PlaybackStatus.PLAYING);
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                skipToPrevious();
                updateMetadata();
                buildNotification(PlaybackStatus.PLAYING);
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

    private void buildNotification(PlaybackStatus status) {
        int notificationAction = android.R.drawable.ic_media_pause;
        PendingIntent playPauseAction = null;

        /* Build a new notification according to current state of the player */
        if (status == PlaybackStatus.PLAYING) {
            notificationAction = android.R.drawable.ic_media_pause;
            playPauseAction = playbackAction(1);
        } else if (status == PlaybackStatus.PAUSED) {
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

        // NOTE(doyle): Partial wake to allow playback on screen-off
        player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);

        if (Debug.CAREFUL_ASSERT(activeAudio != null, TAG,
                "initMediaPlayer(): Audio file is null")) {
            try {
                player.setDataSource(getApplicationContext(), activeAudio.uri);
            } catch (IOException e) {
                e.printStackTrace();
                stopSelf();
            }
            player.prepareAsync();
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (player != null) {
            player.stop();
            player.release();
        }

        return false;
    }

    @Override
    public void onDestroy() {
        // NOTE(doyle): Stop notification running in foreground
        stopForeground(true);

        if (player != null) {
            stopMedia();
            player.release();
        }

        // TODO(doyle):  Think about focus only requested during playback
        removeAudioFocus();

        if (phoneStateListener != null)
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);

        removeNotification();

        unregisterReceiver(becomingNoisyReceiver);
        unregisterReceiver(playNewAudioReceiver);
    }

    private boolean requestAudioFocus() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return true;
        return false;
    }

    private boolean removeAudioFocus() {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(this);
    }

    void queuePlaylistAndSong(final List<AudioFile> playlist, int index) {
        if (Debug.CAREFUL_ASSERT(playlist != null, TAG,
                "queuePlaylistAndSong(): Playlist is null" )) {
            this.playlist = playlist;

            if (!Debug.CAREFUL_ASSERT(index < playlist.size(), TAG,
                    "queuePlaylistAndSong(): Index out of bounds")) {
                index = 0;
            }

            this.playlistIndex = index;
            activeAudio = playlist.get(index);
        }
    }


    /*
     ***********************************************************************************************
     * MEDIA PLAYER INTERFACE IMPLEMENTATION
     ***********************************************************************************************
     */
    // NOTE(doyle): Triggers when track finished/skipped or new track is selected
    @Override
    public void onCompletion(MediaPlayer mp) {
        stopMedia();
        stopSelf();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        // TODO(doyle): Look up API
        mp.reset();
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Log.e(TAG, "onError(): Media error not valid for progressive playback: " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.e(TAG, "onError(): Media error server died: " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.e(TAG, "onError(): Media error unknown: " + extra);
                break;
            default:
                Debug.CAREFUL_ASSERT(false, TAG, "onError(): Media error not handled: " + extra);
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

        playMedia();
    }


    @Override
    public void onAudioFocusChange(int focusState) {
        //Invoked when the audio focus of the system is updated.
        switch (focusState) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // resume playback
                if (player == null) initMediaPlayer();
                else if (!player.isPlaying()) player.start();
                player.setVolume(1.0f, 1.0f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // Lost focus for an unbounded amount of time: stop playback and release media player
                if (player.isPlaying()) player.stop();
                player.release();
                player = null;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume
                if (player.isPlaying()) player.pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (player.isPlaying()) player.setVolume(0.1f, 0.1f);
                break;
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
    public void onSeekComplete(MediaPlayer mp) {

    }


    /*
     ***********************************************************************************************
     * PLAYBACK CONTROL
     ***********************************************************************************************
     */
    private void playMedia() {
        if (!player.isPlaying()) {
            player.start();
        }
    }

    private void stopMedia() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.stop();
        }
    }

    private void pauseMedia() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
            resumePosition = player.getCurrentPosition();
        }
    }

    private void resumeMedia() {
        if (!player.isPlaying()) {
            player.seekTo(resumePosition);
            player.start();
        }
    }

    private void skipToNext() {
        // TODO(doyle): To be implemented
    }

    private void skipToPrevious() {
        // TODO(doyle): To be implemented
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
            buildNotification(PlaybackStatus.PAUSED);
        }
    };

    private void registerBecomingNoisyReceiver() {
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);
    }

    private BroadcastReceiver playNewAudioReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopMedia();
            initMediaPlayer();
            buildNotification(PlaybackStatus.PLAYING);
        }
    };

    private void registerPlayNewAudio() {
        IntentFilter intentFilter = new IntentFilter(MainActivity.BROADCAST_PLAY_NEW_AUDIO);
        registerReceiver(playNewAudioReceiver, intentFilter);
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
                                resumeMedia();
                        }
                        break;
                }
            }
        };

        // NOTE(doyle): Register the listener for changes to the device call state
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    private void unregisterCallStateListener() {

    }

}
