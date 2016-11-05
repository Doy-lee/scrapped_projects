package com.dqnt.amber;

import android.app.Notification;
import android.app.NotificationManager;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.v4.app.NotificationCompat;
import android.view.View;
import android.widget.Button;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.dqnt.amber.PlaybackData.AudioFile;
import com.dqnt.amber.AudioFileAdapter.AudioEntryInView;

import java.util.concurrent.atomic.AtomicReference;

import static android.content.Context.NOTIFICATION_SERVICE;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static com.dqnt.amber.TestUtil.ONE_SECOND;
import static com.dqnt.amber.TestUtil.waitUntil;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Created by Doyle on 5/11/2016.
 */

@RunWith(AndroidJUnit4.class)
@LargeTest
public class MainActivityTest {

    private MainActivity activity;
    private AudioService service;
    private MainActivity.PlaySpec playSpec;
    private MainActivity.UiSpec uiSpec;

    private boolean oldShuffleState;
    private boolean oldRepeatState;

    @Rule
    public ActivityTestRule activityRule = new ActivityTestRule(MainActivity.class);

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Before
    public void start() throws InterruptedException {
        activity = (MainActivity) activityRule.getActivity();
        playSpec = activity.playSpec_;
        uiSpec = activity.uiSpec_;

        while (activity.audioService == null) Thread.sleep(TestUtil.WAIT_INTERVAL);
        while (activity.playSpec_.playingPlaylist == null) Thread.sleep(TestUtil.WAIT_INTERVAL);
        while (activity.playSpec_.playingPlaylist.contents.size() == 0) Thread.sleep(TestUtil.WAIT_INTERVAL);
        service = activity.audioService;

        oldShuffleState = service.shuffle;
        oldRepeatState = service.repeat;

        setShuffleState(false);
        setRepeatState(false);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @After
    public void end() {
        setShuffleState(oldShuffleState);
        setRepeatState(oldRepeatState);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Test
    public void playBarElements() throws InterruptedException {
        AudioFileAdapter adapter = activity.playlistFragment.playlistUiSpec.audioFileAdapter;
        PlaybackData.Playlist playlist = playSpec.playingPlaylist;

        PlaybackData.AudioFile firstFile = playlist.contents.get(0);
        PlaybackData.AudioFile lastFile = playlist.contents.get(playlist.contents.size() - 1);

        /* Check next button starts playing at initialisation */
        onView(withId(R.id.play_bar_skip_next_button)).perform(click());
        assertEquals(service.playState, AudioService.PlayState.PLAYING);
        assertEquals(firstFile, service.activeAudio);
        assertEquals(service.resumePosInMsec, 0);

        onView(withId(R.id.play_bar_seek_bar)).perform(TestUtil.scrubSeekBarAction(50));
        checkAudioFileEntryExistsAndHighlighted(adapter, firstFile);

        /* Check prev button skips, around to the back of the list */
        onView(withId(R.id.play_bar_skip_previous_button)).perform(click());
        assertEquals(service.playState, AudioService.PlayState.PLAYING);
        assertEquals(lastFile, service.activeAudio);
        assertEquals(service.resumePosInMsec, 0);
        checkAudioFileEntryExistsAndHighlighted(adapter, lastFile);

        /* Test that if song is paused, the track actually pauses, i.e. resume position is static */
        onView(withId(R.id.play_bar_play_button)).perform(click());
        assertEquals(service.playState, AudioService.PlayState.PAUSED);
        int pausedResumePosition = service.getCurrTrackPosition();
        Thread.sleep(100);
        assertEquals(pausedResumePosition, service.getCurrTrackPosition());
        checkAudioFileEntryExistsAndHighlighted(adapter, playlist.contents.get(playlist.index));

        /* Test shuffle button click enables flag and visually */
        checkShuffleStateIs(false);
        setShuffleState(true);

        /* Test shuffle shuffles */
        int oldSongIndex = playSpec.playingPlaylist.index;
        onView(withId(R.id.play_bar_skip_next_button)).perform(click());
        int newSongIndex = playSpec.playingPlaylist.index;

        assertEquals(service.playState, AudioService.PlayState.PLAYING);
        assertNotEquals(oldSongIndex, newSongIndex);
        checkAudioFileEntryExistsAndHighlighted(adapter, playlist.contents.get(playlist.index));

        /* Shuffle backwards */
        oldSongIndex = playSpec.playingPlaylist.index;
        onView(withId(R.id.play_bar_skip_previous_button)).perform(click());
        assertEquals(service.playState, AudioService.PlayState.PLAYING);
        newSongIndex = playSpec.playingPlaylist.index;
        assertNotEquals(oldSongIndex, newSongIndex);
        checkAudioFileEntryExistsAndHighlighted(adapter, playlist.contents.get(playlist.index));

        /* Turn on repeat mode */
        checkRepeatStateIs(false);
        setShuffleState(false);
        setRepeatState(true);
        checkShuffleStateIs(false);

        /* Test repeat next/prev stays on same song, but restarts it */
        oldSongIndex = playSpec.playingPlaylist.index;
        onView(withId(R.id.play_bar_skip_next_button)).perform(click());
        newSongIndex = playSpec.playingPlaylist.index;
        assertEquals(oldSongIndex, newSongIndex);
        assertEquals(service.resumePosInMsec, 0);
        assertEquals(service.playState, AudioService.PlayState.PLAYING);
        onView(withId(R.id.play_bar_seek_bar)).perform(TestUtil.scrubSeekBarAction(25));
        checkAudioFileEntryExistsAndHighlighted(adapter, playlist.contents.get(playlist.index));

        oldSongIndex = playSpec.playingPlaylist.index;
        onView(withId(R.id.play_bar_skip_previous_button)).perform(click());
        newSongIndex = playSpec.playingPlaylist.index;
        assertEquals(oldSongIndex, newSongIndex);
        assertEquals(service.resumePosInMsec, 0);
        assertEquals(service.playState, AudioService.PlayState.PLAYING);
        checkAudioFileEntryExistsAndHighlighted(adapter, playlist.contents.get(playlist.index));

        /* Set both repeat and shuffle on, ensure repeat behaviour persists */
        checkShuffleStateIs(false);
        setShuffleState(true);
        checkRepeatStateIs(true);

        oldSongIndex = playSpec.playingPlaylist.index;
        onView(withId(R.id.play_bar_skip_next_button)).perform(click());
        newSongIndex = playSpec.playingPlaylist.index;
        assertEquals(oldSongIndex, newSongIndex);
        assertEquals(service.resumePosInMsec, 0);
        assertEquals(service.playState, AudioService.PlayState.PLAYING);
        checkAudioFileEntryExistsAndHighlighted(adapter, playlist.contents.get(playlist.index));

        oldSongIndex = playSpec.playingPlaylist.index;
        onView(withId(R.id.play_bar_skip_previous_button)).perform(click());
        newSongIndex = playSpec.playingPlaylist.index;
        assertEquals(oldSongIndex, newSongIndex);
        assertEquals(service.resumePosInMsec, 0);
        assertEquals(service.playState, AudioService.PlayState.PLAYING);
        checkAudioFileEntryExistsAndHighlighted(adapter, playlist.contents.get(playlist.index));

        /* Test that notifications duck audio and on gain, don't resume if we've already paused */
        onView(withId(R.id.play_bar_seek_bar)).perform(TestUtil.scrubSeekBarAction(50));
        onView(withId(R.id.play_bar_play_button)).perform(click());
        assertEquals(service.playState, AudioService.PlayState.PAUSED);
        int oldResumePosition = service.getCurrTrackPosition();

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(activity)
                        .setSmallIcon(R.drawable.ic_play)
                        .setDefaults(Notification.DEFAULT_SOUND);

        NotificationManager notificationManager =
                (NotificationManager) activity.getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(0xDEADBEEF, builder.build());

        Thread.sleep(ONE_SECOND);
        assertEquals(service.playState, AudioService.PlayState.PAUSED);
        assertEquals(oldResumePosition, service.getCurrTrackPosition());

        /* Test that notifications duck audio and on gain, resume as we were playing music */
        onView(withId(R.id.play_bar_play_button)).perform(click());
        assertEquals(service.playState, AudioService.PlayState.PLAYING);

        notificationManager.notify(0xDEADBEEF, builder.build());
        Thread.sleep(ONE_SECOND);
        assertEquals(service.playState, AudioService.PlayState.PLAYING);

        /* _WAIT UNTIL_ USAGE STUB, doesn't work yet
        int waitGranularityInMs = (int)(ONE_SECOND * 0.25f);
        int waitTimeout = ONE_SECOND * 10;
        AtomicReference<Boolean> serviceWasPlayingBeforeDuck =
                new AtomicReference<>(service.wasPlayingBeforeAudioDuck);
        waitUntil(serviceWasPlayingBeforeDuck, waitTimeout, waitGranularityInMs);
        */

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setRepeatState(boolean state) {
        Button repeatButton = (Button) activity.findViewById(R.id.play_bar_repeat_button);
        if (state == service.repeat) return;
        onView(withId(R.id.play_bar_repeat_button)).perform(click());
        checkPlayBarHighlightableButtonIs(repeatButton, state);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void checkRepeatStateIs(boolean state) {
        Button repeatButton = (Button) activity.findViewById(R.id.play_bar_repeat_button);
        assertEquals(state, service.repeat);
        checkPlayBarHighlightableButtonIs(repeatButton, state);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setShuffleState(boolean state) {
        Button shuffleButton = (Button) activity.findViewById(R.id.play_bar_shuffle_button);
        if (state == service.shuffle) return;
        onView(withId(R.id.play_bar_shuffle_button)).perform(click());
        checkPlayBarHighlightableButtonIs(shuffleButton, state);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void checkShuffleStateIs(boolean state) {
        Button shuffleButton = (Button) activity.findViewById(R.id.play_bar_shuffle_button);
        assertEquals(state, service.shuffle);
        checkPlayBarHighlightableButtonIs(shuffleButton, state);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void checkPlayBarHighlightableButtonIs(Button button, boolean state) {
        ColorFilter checkFilter = null;
        if (state) {
            checkFilter = new PorterDuffColorFilter(uiSpec.accentColor, PorterDuff.Mode.SRC_IN);
        }

        assertEquals(button.getBackground().getColorFilter(), checkFilter);
    }

    private void checkAudioFileEntryExistsAndHighlighted(AudioFileAdapter adapter,
                                                         AudioFile checkFile) {
        int numItems = adapter.getCount();
        boolean matchedEntryWithHighlight = false;
        for (int i = 0; i < numItems; i++) {
            if (adapter.getItem(i).title.equals(checkFile.title)) {
                View view = adapter.getView(i, null, null);
                AudioEntryInView entry = (AudioEntryInView) view.getTag();
                assertEquals(entry.title.getCurrentTextColor(), uiSpec.accentColor);
                matchedEntryWithHighlight = true;
                break;
            }
        }
        assertEquals(matchedEntryWithHighlight, true);
    }

    /*
    public static Matcher<Object> checkAudioFileEntryExistsAndHighlighted(final String audioTitle) {
        Checks.checkNotNull(audioTitle);
        return new BoundedMatcher<Object, AudioFile>(AudioFile.class) {
            @Override
            public void describeTo(Description description) {

            }

            @Override
            public boolean matchesSafely(AudioFile item) {
                return (item.title.equals(audioTitle));
            }
        };
    }
    */

}
