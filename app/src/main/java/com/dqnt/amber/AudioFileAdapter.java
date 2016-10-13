package com.dqnt.amber;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by Doyle on 25/09/2016.
 */
class AudioFileAdapter extends BaseAdapter {
    private final String TAG = AudioFileAdapter.class.getName();

    private ArrayList<AudioFile> audioList;
    private LayoutInflater audioInflater;

    AudioFileAdapter(Context context, ArrayList<AudioFile> audioList) {
        this.audioList = audioList;
        audioInflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return audioList.size();
    }

    @Override
    public Object getItem(int position) {
        return audioList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return audioList.get(position).id;
    }

    // NOTE(doyle): Cache the inflated layout elements in a audio entry into the tag of a list item
    public class AudioEntryInView {
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
