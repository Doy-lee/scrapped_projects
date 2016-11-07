package com.dqnt.amber;


import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;

import com.dqnt.amber.PlaybackData.AudioFile;
import com.dqnt.amber.PlaybackData.Playlist;

public class MetadataFragment extends Fragment {
    private List<AudioFile> allAudioFiles;
    private ListView listView;
    private StringAdapter metadataAdapter;
    MainActivity.FragmentType type;

    static class PlaylistUiSpec {
        Playlist displayingPlaylist;
        AudioFileAdapter adapter;
    }
    PlaylistUiSpec playlistUiSpec;

    private Stack<MainActivity.FragmentType> metadataViewStack;
    AudioFileClickListener listener;

    public MetadataFragment() {}

    public static MetadataFragment newInstance(Context context, List<AudioFile> allAudioFiles,
                                               MainActivity.FragmentType type,
                                               Playlist activePlaylist) {
        MetadataFragment fragment = new MetadataFragment();
        fragment.allAudioFiles = allAudioFiles;
        fragment.type = type;
        fragment.metadataAdapter = new StringAdapter(new ArrayList<String>(), context);
        fragment.metadataViewStack = new Stack<>();

        fragment.playlistUiSpec = new PlaylistUiSpec();
        fragment.playlistUiSpec.displayingPlaylist = activePlaylist;

        /*
            Bundle args = new Bundle();
            args.putString(ARG_PARAM1, param1);
            args.putString(ARG_PARAM2, param2);
            fragment.setArguments(args);
        */

        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_metadata_view, container, false);

        { // Setup Metadata view
            listView = (ListView) rootView.findViewById(R.id.fragment_metadata_list_view);
            listView.setAdapter(metadataAdapter);
            switchMetadataView(type, null);

            rootView.setFocusable(true);
            rootView.requestFocus();
            rootView.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    boolean result = false;
                    if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                        if (!metadataViewStack.isEmpty()) {
                            MainActivity.FragmentType type = metadataViewStack.pop();
                            switchMetadataView(type, null);
                            result = true;
                        }
                    }

                    return result;
                }
            });
        }

        { // Setup Playlist Spec
            // TODO(doyle): Send in args from main
            int accentColor = ContextCompat.getColor(getContext(), R.color.colorAccent);
            playlistUiSpec.adapter = new AudioFileAdapter(getContext(),
                    listView, playlistUiSpec.displayingPlaylist, accentColor);
        }

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

        Debug.TOAST(context, "Metadata view fragment + listener attached", Toast.LENGTH_SHORT);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;

        Debug.TOAST(getContext(), "Metadata view fragment + listener detached", Toast.LENGTH_SHORT);
    }

    // NOTE(doyle): Need current playing playlist, because if it is'nt the current playing playlist,
    // we don't want to highlight an entry, that may be left active since it last played
    void updateDisplayingPlaylist(Playlist playingPlaylist, Playlist newPlaylist) {
        playlistUiSpec.displayingPlaylist = newPlaylist;

        if (playlistUiSpec.displayingPlaylist != playingPlaylist) {
            playlistUiSpec.displayingPlaylist.index = -1;
        }

        updateUiData();
    }

    void updateUiData() {
        Debug.INCREMENT_COUNTER(this);

        if (playlistUiSpec.displayingPlaylist == null) {
            return;
        }

        /* Update the playlist currently displayed */
        AudioFileAdapter adapter = playlistUiSpec.adapter;
        // NOTE(doyle): Update called before view is created, which is fine.
        if (adapter == null) return;

        if (adapter.playlist != playlistUiSpec.displayingPlaylist) {
            playlistUiSpec.adapter.playlist = playlistUiSpec.displayingPlaylist;
        }
        playlistUiSpec.adapter.notifyDataSetChanged();
    }

    void switchMetadataView(MainActivity.FragmentType newType, String filterBy) {
        // NOTE(doyle): All metadata views are top level unless it's a file view, which means we
        // need to start tracking the view stack, i.e. artist files should return to artist; artist
        // view itself should return to nothing
        if (newType == MainActivity.FragmentType.ARTIST_FILES ||
                newType == MainActivity.FragmentType.ALBUM_FILES) {
            // NOTE(doyle): Push old type
            metadataViewStack.push(type);

        } else {
            metadataViewStack.clear();
        }

        this.type = newType;
        listView.setOnItemClickListener(null);

        // TODO: Cache the lists instead of rebuilding each time
        SortedSet<String> dataForFragment = new TreeSet<>();
        switch(type) {
            case ARTIST: {
                for (AudioFile file: allAudioFiles) {
                    dataForFragment.add(file.artist);
                }

                listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        TextView textView = (TextView) view.getTag();
                        switchMetadataView(MainActivity.FragmentType.ARTIST_FILES,
                                textView.getText().toString());
                    }
                });
            } break;

            case ARTIST_FILES: {
                if (Debug.CAREFUL_ASSERT(filterBy != null, this,
                        "Cannot call artist file view without a filter")) {

                    for (AudioFile file: allAudioFiles) {

                        if (filterBy.equals(file.artist)) {
                            dataForFragment.add(file.title);
                        }
                    }

                    listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            PlaybackData.Playlist playlist =
                                    new PlaybackData.Playlist("Now playing");

                            // TODO(doyle): Store file data into metadataAdapter, instead of search list
                            // again to find the file to play
                            TextView textView = (TextView) view.getTag();
                            String audioTitleToPlay = textView.getText().toString();
                            for (AudioFile file: allAudioFiles) {
                                if (file.title.equals(audioTitleToPlay)) {
                                    playlist.contents.add(file);
                                    break;
                                }
                            }

                            listener.audioFileClicked(playlist);
                        }
                    });
                }
            } break;

            case ALBUM: {
                for (AudioFile file: allAudioFiles) {
                    dataForFragment.add(file.album);
                }

                listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        TextView textView = (TextView) view.getTag();
                        switchMetadataView(MainActivity.FragmentType.ALBUM_FILES,
                                textView.getText().toString());
                    }
                });
            } break;

            // TODO(doyle): Reorganise file views after metadata drill-down since very similar code
            case ALBUM_FILES: {
                if (Debug.CAREFUL_ASSERT(filterBy != null, this,
                        "Cannot call artist file view without a filter")) {

                    for (AudioFile file: allAudioFiles) {
                        if (filterBy.equals(file.album)) {
                            dataForFragment.add(file.title);
                        }
                    }

                    listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            PlaybackData.Playlist playlist =
                                    new PlaybackData.Playlist("Now playing");

                            // TODO(doyle): Store file data into metadataAdapter, instead of search list
                            // again to find the file to play
                            TextView textView = (TextView) view.getTag();
                            String audioTitleToPlay = textView.getText().toString();
                            for (AudioFile file: allAudioFiles) {
                                if (file.title.equals(audioTitleToPlay)) {
                                    playlist.contents.add(file);
                                    break;
                                }
                            }

                            listener.audioFileClicked(playlist);
                        }
                    });
                }
            } break;

            case PLAYLIST: {
                listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                        AudioFileAdapter.AudioEntryInView entry =
                                (AudioFileAdapter.AudioEntryInView) view.getTag();
                        int index = entry.position;

                        /* Update playlist view after song click */
                        Playlist newPlaylist = playlistUiSpec.displayingPlaylist;
                        AudioFileAdapter adapter = playlistUiSpec.adapter;
                        adapter.playlist = newPlaylist;

                        if (newPlaylist.index != index) {
                            newPlaylist.index = index;
                            adapter.listView.invalidateViews();
                        }

                        listener.audioFileClicked(playlistUiSpec.displayingPlaylist);
                    }
                });
            } break;

            default: {
                Debug.CAREFUL_ASSERT(false, this,
                        "Fragment type not handled in metadata fragment: " + type.toString());
            } break;
        }

        if (newType == MainActivity.FragmentType.PLAYLIST) {
            listView.setAdapter(playlistUiSpec.adapter);
        } else {
            listView.setAdapter(metadataAdapter);
            List<String> dataList = new ArrayList<>();
            dataList.addAll(dataForFragment);
            metadataAdapter.stringList = dataList;

            metadataAdapter.notifyDataSetChanged();
        }
    }

    private static class StringAdapter extends BaseAdapter {
        List<String> stringList;
        private LayoutInflater inflater;

        StringAdapter(List<String> stringList, Context context) {
            this.stringList = stringList;
            inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            int result = stringList.size();
            return result;
        }

        @Override
        public Object getItem(int position) {
            if (stringList != null) {
                return stringList.get(position);
            }
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView textView;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.list_item, parent, false);
                textView = (TextView) convertView.findViewById(R.id.list_item_text_view);

                convertView.setTag(textView);
            } else {
                textView = (TextView) convertView.getTag();
            }

            textView.setText(stringList.get(position));
            return convertView;
        }
    }

}
