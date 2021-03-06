package com.dqnt.amber;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.dqnt.amber.Models.AudioFile;

/**
 * Created by Doyle on 25/09/2016.
 */
class PlaylistAdapter extends BaseAdapter {
    Models.Playlist playlist;
    private LayoutInflater inflater;
    private int highlightColor;

    PlaylistAdapter(Context context, Models.Playlist playlist, int highlightColor) {
        this.inflater = LayoutInflater.from(context);
        this.playlist = playlist;
        this.highlightColor = highlightColor;
    }

    public int getCount() {
        int result = 0;
        if (playlist != null) {
            result = playlist.contents.size();
        }

        return result;
    }

    @Override
    public Object getItem(int position) {
        if (playlist != null) {
            return playlist.contents.get(position);
        }

        return null;
    }

    @Override
    public long getItemId(int position) {
        long result = -1;
        if (playlist != null) {
            result = playlist.contents.get(position).dbKey;
        }

        return result;
    }

    // NOTE(doyle): Cache the inflated layout elements in a audio entry into the tag of a list item
    class PlaylistItemEntry {
        ImageView coverArt;
        TextView artistAndAlbum;
        TextView title;
    }

    int artistInactiveColor = -1;
    int titleInactiveColor = -1;
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        PlaylistItemEntry audioEntry;
        // NOTE(doyle): convertView is a recycled entry going offscreen to be
        // reused for another element
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.playlist_item, parent, false);

            audioEntry = new PlaylistItemEntry();
            audioEntry.artistAndAlbum =
                    (TextView) convertView.findViewById(R.id.playlist_item_subtitle_text_view);
            audioEntry.title =
                    (TextView) convertView.findViewById(R.id.playlist_item_title_text_view);
            audioEntry.coverArt =
                    (ImageView) convertView.findViewById(R.id.playlist_item_image_view);

            convertView.setTag(audioEntry);

            if (artistInactiveColor == -1) {
                artistInactiveColor = audioEntry.artistAndAlbum.getCurrentTextColor();
                titleInactiveColor = audioEntry.title.getCurrentTextColor();
            }
        } else {
            audioEntry = (PlaylistItemEntry) convertView.getTag();
        }

        AudioFile audio = playlist.contents.get(position);
        if (Debug.CAREFUL_ASSERT(audio != null, this, "Audio file is null")) {
            /* Set view data */
            String artistAndAlbumString = audio.artist + " | " + audio.album;
            audioEntry.artistAndAlbum.setText(artistAndAlbumString);
            audioEntry.title.setText(audio.title);

            if (audio.bitmapUri != null) {
                Bitmap bitmap = BitmapFactory.decodeFile(audio.bitmapUri.getPath());
                audioEntry.coverArt.setImageBitmap(bitmap);
            } else {
                audioEntry.coverArt.setImageBitmap(null);
            }

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
