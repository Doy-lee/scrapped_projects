package com.dqnt.amber;


import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.dqnt.amber.PlaybackData.Playlist;

/**
 * A simple {@link Fragment} subclass.
 */
public class PlaylistFragment extends Fragment {
    static class PlaylistUiSpec {
        Playlist displayingPlaylist;
        AudioFileAdapter audioFileAdapter;
        private ListView audioListView;
    }
    PlaylistUiSpec playlistUiSpec;

    AudioFileClickListener listener;

    public PlaylistFragment() {}

    public static PlaylistFragment newInstance(Playlist activePlaylist) {
        PlaylistFragment fragment = new PlaylistFragment();
        fragment.playlistUiSpec = new PlaylistUiSpec();
        fragment.playlistUiSpec.displayingPlaylist = activePlaylist;

        // Bundle args = new Bundle();
        // args.putString(ARG_PARAM1, param1);
        // args.putString(ARG_PARAM2, param2);
        // fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_playlist_view, container, false);

        // TODO(doyle): Send in args from main
        int accentColor = ContextCompat.getColor(getContext(), R.color.colorAccent);

        playlistUiSpec.audioListView =
                (ListView) rootView.findViewById(R.id.fragment_playlist_view);
        playlistUiSpec.audioFileAdapter = new AudioFileAdapter(getContext(),
                playlistUiSpec.audioListView, playlistUiSpec.displayingPlaylist, accentColor);
        playlistUiSpec.audioListView.setOnItemClickListener(new AudioFileInAdapterClickListener());

        return rootView;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof AudioFileClickListener) {
            listener = (AudioFileClickListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }

        Debug.TOAST(context, "Playlist view fragment + listener attached", Toast.LENGTH_SHORT);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;

        Debug.TOAST(getContext(), "Playlist view fragment + listener detached", Toast.LENGTH_SHORT);
    }

    void updateUiData() {
        Debug.INCREMENT_COUNTER(this);

        AudioFileAdapter adapter = playlistUiSpec.audioFileAdapter;
        /* Update the playlist currently displayed */
        if (adapter.playlist != playlistUiSpec.displayingPlaylist) {
            playlistUiSpec.audioFileAdapter.playlist = playlistUiSpec.displayingPlaylist;
        }
        playlistUiSpec.audioFileAdapter.notifyDataSetChanged();
    }

    private class AudioFileInAdapterClickListener implements AdapterView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            AudioFileAdapter.AudioEntryInView entry =
                    (AudioFileAdapter.AudioEntryInView) view.getTag();
            int index = entry.position;

            /* Update playlist view after song click */
            Playlist newPlaylist = playlistUiSpec.displayingPlaylist;
            AudioFileAdapter adapter = playlistUiSpec.audioFileAdapter;
            adapter.playlist = newPlaylist;

            if (newPlaylist.index != index) {
                newPlaylist.index = index;
                adapter.listView.invalidateViews();
            }

            listener.audioFileClicked(playlistUiSpec.displayingPlaylist);
        }
    }


}
