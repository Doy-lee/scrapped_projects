package com.dqnt.amber;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import com.dqnt.amber.PlaybackData.AudioFile;

/**
 * Created by Doyle on 25/09/2016.
 */
class AudioFileAdapter extends BaseAdapter {
    private final String TAG = AudioFileAdapter.class.getName();

    List<AudioFile> audioList;
    private LayoutInflater audioInflater;

    AudioFileAdapter(Context context, List<AudioFile> audioList) {
        this.audioList = audioList;
        audioInflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        int result = 0;
        if (audioList != null) {
            result = audioList.size();
        }

        return result;
    }

    @Override
    public Object getItem(int position) {
        if (audioList != null) {
            return audioList.get(position);
        }

        return null;
    }

    @Override
    public long getItemId(int position) {
        // TODO(doyle): Fix
        if (audioList != null) {
            return audioList.get(position).id;
        }

         return 0;
    }

    // NOTE(doyle): Cache the inflated layout elements in a audio entry into the tag of a list item
    class AudioEntryInView {
        int position;
        TextView artist;
        TextView title;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        AudioEntryInView audioEntry;

        // NOTE(doyle): convertView is a recycled entry going offscreen to be
        // reused for another element
        if (convertView == null) {
            convertView = audioInflater.inflate(R.layout.file_audio, parent, false);

            audioEntry = new AudioEntryInView();
            audioEntry.artist = (TextView) convertView.findViewById(R.id.audio_artist);
            audioEntry.title = (TextView) convertView.findViewById(R.id.audio_title);

            convertView.setTag(audioEntry);
        } else {
            audioEntry = (AudioEntryInView) convertView.getTag();
        }

        AudioFile audio = audioList.get(position);
        if (Debug.CAREFUL_ASSERT(audio != null, TAG, "getView(): Audio file is null")) {
            /* Set view data */
            audioEntry.artist.setText(audio.title);
            audioEntry.title.setText(audio.artist);
            audioEntry.position = position;
        }

        return convertView;
    }
}
