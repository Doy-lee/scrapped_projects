package dqnt.amber;

import android.content.Context;
import android.widget.MediaController;

/**
 * Created by Doyle on 26/09/2016.
 */

public class AudioController extends MediaController {
    public AudioController(Context context) {
        super(context);
    }

    @Override
    public void hide() {}
}
