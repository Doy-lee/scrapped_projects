package com.dqnt.amber;

import android.content.Context;
import android.widget.MediaController;

/**
 * Created by Doyle on 26/09/2016.
 */


// NOTE(doyle): A view containing controls for a MediaPlayer. Typically contains the buttons like "Play/Pause",
// "Rewind", "Fast Forward" and a progress slider. It takes care of synchronizing the controls with the
// state of the MediaPlayer.
public class AudioController extends MediaController {
    public AudioController(Context context) {
        super(context);
    }

    @Override
    public void hide() {}
}
