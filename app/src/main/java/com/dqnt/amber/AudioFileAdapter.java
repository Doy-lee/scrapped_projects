package com.dqnt.amber;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.dqnt.amber.PlaybackData.AudioFile;

/**
 * Created by Doyle on 25/09/2016.
 */
class AudioFileAdapter extends BaseAdapter {
    ListView listView;

    PlaybackData.Playlist playlist;
    private LayoutInflater audioInflater;
    private int highlightColor;

    AudioFileAdapter(Context context, ListView listView, PlaybackData.Playlist playlist,
                     int highlightColor) {
        audioInflater = LayoutInflater.from(context);
        this.playlist = playlist;

        this.highlightColor = highlightColor;
        this.listView = listView;
        listView.setAdapter(this);
    }

    public int getCount() {
        int result = 0;
        if (playlist != null) {
            result = playlist.contents.size();
        }

        return result;
    }

    @Override
    public AudioFile getItem(int position) {
        if (playlist != null) {
            return playlist.contents.get(position);
        }

        return null;
    }

    @Override
    public long getItemId(int position) {
        if (playlist != null) {
            return playlist.contents.get(position).dbKey;
        }

        return -1;
    }

    // NOTE(doyle): Cache the inflated layout elements in a audio entry into the tag of a list item
    class AudioEntryInView {
        int position;
        TextView artistAndAlbum;
        TextView title;
    }

    int artistInactiveColor = -1;
    int titleInactiveColor = -1;
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        AudioEntryInView audioEntry;

        // NOTE(doyle): convertView is a recycled entry going offscreen to be
        // reused for another element
        if (convertView == null) {
            convertView = audioInflater.inflate(R.layout.file_audio, parent, false);

            audioEntry = new AudioEntryInView();
            audioEntry.artistAndAlbum = (TextView) convertView.findViewById(R.id.audio_artist);
            audioEntry.title = (TextView) convertView.findViewById(R.id.audio_title);

            convertView.setTag(audioEntry);

            if (artistInactiveColor == -1) {
                artistInactiveColor = audioEntry.artistAndAlbum.getCurrentTextColor();
                titleInactiveColor = audioEntry.title.getCurrentTextColor();
            }
        } else {
            audioEntry = (AudioEntryInView) convertView.getTag();
        }

        AudioFile audio = playlist.contents.get(position);
        if (Debug.CAREFUL_ASSERT(audio != null, this, "Audio file is null")) {
            /* Set view data */

            String artistAndAlbumString = audio.artist + " | " + audio.album;
            audioEntry.artistAndAlbum.setText(artistAndAlbumString);
            audioEntry.title.setText(audio.title);
            audioEntry.position = position;

            if (playlist.index == position) {
                audioEntry.artistAndAlbum.setTextColor(highlightColor);
                audioEntry.title.setTextColor(highlightColor);
            } else {
                audioEntry.artistAndAlbum.setTextColor(artistInactiveColor);
                audioEntry.title.setTextColor(titleInactiveColor);
            }
        }

        return convertView;
    }
}
