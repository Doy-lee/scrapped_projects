package dqnt.amber;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by Doyle on 25/09/2016.
 */
public class AudioFileAdapter extends BaseAdapter
{
    private ArrayList<MainActivity.AudioFile> audioList;
    private LayoutInflater audioInflater;

    public AudioFileAdapter(Context context, ArrayList<MainActivity.AudioFile> audioList)
    {
        this.audioList = audioList;
        audioInflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() { return audioList.size(); }

    @Override
    public Object getItem(int position) { return audioList.get(position); }

    @Override
    public long getItemId(int position) { return audioList.get(position).id ; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        /* Get data */
        LinearLayout layout = (LinearLayout)audioInflater.inflate(R.layout.file_audio, parent, false);
        MainActivity.AudioFile audio = audioList.get(position);

        /* Set view data */
        TextView title = (TextView)layout.findViewById(R.id.audio_title);
        TextView artistView = (TextView)layout.findViewById(R.id.audio_artist);
        title.setText(audio.title);
        artistView.setText(audio.artist);

        /* Set position as tag */
        layout.setTag(position);

        return layout;
    }
}
