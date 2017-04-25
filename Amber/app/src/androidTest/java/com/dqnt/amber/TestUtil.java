package com.dqnt.amber;

import android.support.test.espresso.PerformException;
import android.support.test.espresso.ViewAction;
import android.support.test.espresso.action.CoordinatesProvider;
import android.support.test.espresso.action.GeneralSwipeAction;
import android.support.test.espresso.action.Press;
import android.support.test.espresso.action.Swipe;
import android.support.test.espresso.util.HumanReadables;
import android.view.View;
import android.widget.SeekBar;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static android.support.test.espresso.action.ViewActions.actionWithAssertions;
import static junit.framework.Assert.assertEquals;

/**
 * Created by Doyle on 5/11/2016.
 */

public class TestUtil {

    static final int WAIT_INTERVAL = 500;
    static final int ONE_SECOND = 1000;

    public class BoolRef {
        boolean val;
    }

    public static void waitUntil(AtomicReference<Boolean> condition, int timeoutInMs,
                                 int checkGranularityInMs) throws InterruptedException {
        int timeoutCounter = 0;
        while (!condition.get()) {
            Thread.sleep(checkGranularityInMs);
            timeoutCounter += checkGranularityInMs;

            if (timeoutCounter >= timeoutInMs) assertEquals(false, true);
        }
    }

    public static ViewAction scrubSeekBarAction(int progress) {
        return actionWithAssertions(new GeneralSwipeAction(
                Swipe.FAST,
                new SeekBarThumbCoordinatesProvider(0),
                new SeekBarThumbCoordinatesProvider(progress),
                Press.PINPOINT));
    }

    private static class SeekBarThumbCoordinatesProvider implements CoordinatesProvider {
        int percent;

        public SeekBarThumbCoordinatesProvider(int progress) {
            this.percent = progress;
        }

        private static float[] getVisibleLeftTop(View view) {
            final int[] xy = new int[2];
            view.getLocationOnScreen(xy);
            return new float[]{ (float) xy[0], (float) xy[1] };
        }

        @Override
        public float[] calculateCoordinates(View view) {
            if (!(view instanceof SeekBar)) {
                throw new PerformException.Builder()
                        .withViewDescription(HumanReadables.describe(view))
                        .withCause(new RuntimeException(String.format("SeekBar expected"))).build();
            }
            SeekBar seekBar = (SeekBar) view;
            int width = seekBar.getWidth() - seekBar.getPaddingLeft() - seekBar.getPaddingRight();
            double progress = ((float)percent / 100.0f) * seekBar.getMax();
            int xPosition = (int) (seekBar.getPaddingLeft() + width * progress / seekBar.getMax());
            float[] xy = getVisibleLeftTop(seekBar);
            return new float[]{ xy[0] + xPosition, xy[1] + 10 };
        }
    }
}
