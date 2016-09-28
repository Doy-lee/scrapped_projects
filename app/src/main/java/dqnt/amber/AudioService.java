package dqnt.amber;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Random;

// TODO(doyle): Audio focus/audio manager API to manage what happens to sound on lose focus
public class AudioService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {

    private static final String TAG = AudioService.class.getName();
    private static final int NOTIFY_ID = 1;
    private final IBinder binder = new AudioBinder();
    private MediaPlayer player;

    private ArrayList<AudioFile> audioList;
    private int audioFileIndex;

    private boolean shuffle = false;
    private Random rand;

    // NOTE(doyle): The system calls this method when the service is first created, to perform
    // one-time setup procedures (before it calls either onStartCommand() or onBind()). If the
    // service is already running, this method is not called.
    public void onCreate() {
        super.onCreate();
        audioFileIndex = 0;
        player = new MediaPlayer();

        /* Init media player */
        // NOTE(doyle): Partial wake to allow playback on screen-off
        player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);

        player.setAudioStreamType(AudioManager.STREAM_MUSIC);

        /* Register listeners to this class's implementation */
        player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);

        // TODO(doyle): Revise for random shuffle
        rand = new Random();
    }

    public void setAudioList(ArrayList<AudioFile> audioList) {
        this.audioList = audioList;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        player.stop();
        player.release();
        return false;
    }

    @Override
    public void onDestroy() {
        // NOTE(doyle): Stop notification running in foreground
        stopForeground(true);
    }

    // NOTE(doyle): Triggers when track finished/skipped or new track is selected
    @Override
    public void onCompletion(MediaPlayer mp) {
        // TODO(doyle): Look at this again
        if (player.getCurrentPosition() > 0) {
            mp.reset();
            playNext();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        // TODO(doyle): Look up API
        mp.reset();
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mp.start();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // NOTE(doyle): Allow user to be redirected back to main activity on notification click
        PendingIntent pendingIntent = PendingIntent.getActivity
                (this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        AudioFile file = audioList.get(audioFileIndex);

        Notification.Builder builder = new Notification.Builder(this);
        builder.setContentIntent(pendingIntent)
                .setTicker(file.title)
                .setOngoing(true)
                .setContentTitle("Playing")
                .setContentText(file.title);
        Notification notification = builder.build();

        startForeground(NOTIFY_ID, notification);
    }

    // NOTE(doyle): Interface between client and service. Allows client to get service instance from
    // the binder
    public class AudioBinder extends Binder {
        AudioService getService() {
            return AudioService.this;
        }
    }

    /*
     *******************
     * PLAYBACK CONTROL
     *******************
     */
    public void end() { player.stop(); }
    public int getPosition() { return player.getCurrentPosition(); }
    public int getDuration() { return player.getDuration(); }
    public boolean isPlaying() { return player.isPlaying(); }
    public void pause() { player.pause(); }
    public void seek(int pos) { player.seekTo(pos); }
    public void go() { player.start(); }

    public void setShuffle() {
        if (shuffle) shuffle = false;
        else shuffle = true;
    }

    public void playPrev() {
        if (--audioFileIndex < 0) audioFileIndex = audioList.size()-1;
        play();
    }

    public void playNext() {
        if (shuffle)
        {
            int newIndex = audioFileIndex;
            while (newIndex == audioFileIndex)
            {
                newIndex = rand.nextInt(audioList.size());
            }

            audioFileIndex = newIndex;
        }
        else
        {
            if (++audioFileIndex >= audioList.size()) audioFileIndex = 0;
        }

        play();
    }

    public void play() {
        player.reset();
        AudioFile audio = audioList.get(audioFileIndex);
        /*
        int audioId = audio.id;
        Uri trackUri = ContentUris.withAppendedId
                (android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId);
        */

        try {
            player.setDataSource(getApplicationContext(), audio.uri);
            player.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "Error setting audio data source", e);
        }

    }

    public void setAudio(int audioIndex) { this.audioFileIndex = audioIndex; }

}
