package dqnt.amber;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.util.ArrayList;

public class AudioService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {

    private final static String TAG = AudioService.class.getName();
    private final IBinder binder = new AudioBinder();
    private MediaPlayer player;

    private ArrayList<MainActivity.AudioFile> audioList;
    private int audioFileIndex;

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
    }

    public void setAudioList(ArrayList<MainActivity.AudioFile> audioList) {
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
    public void onCompletion(MediaPlayer mp) {

    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) { mp.start(); }

    public void endPlayback() { player.stop(); }
    public void startPlayback() {
        player.reset();
        MainActivity.AudioFile audio = audioList.get(audioFileIndex);
        int audioId = audio.id;

        /*
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

    // NOTE(doyle): Interface between client and service. Allows client to get service instance from
    // the binder
    public class AudioBinder extends Binder {
        AudioService getService() {
            return AudioService.this;
        }
    }
}
