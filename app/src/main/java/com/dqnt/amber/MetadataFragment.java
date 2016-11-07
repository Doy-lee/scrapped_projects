package com.dqnt.amber;


import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
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
import com.dqnt.amber.MainActivity.FragmentType;

public class MetadataFragment extends Fragment {
    private List<AudioFile> allAudioFiles;
    private ListView listView;
    FragmentType type;
    AudioFileClickListener listener;
    private Toolbar toolbar;

    private MetadataAdapter metadataAdapter;
    private Stack<FragmentType> metadataViewStack;

    static class PlaylistUiSpec {
        Playlist displayingPlaylist;
        PlaylistAdapter adapter_;

        PlaylistUiSpec(Context context, Playlist displayingPlaylist) {
            this.displayingPlaylist = displayingPlaylist;

            int accentColor = ContextCompat.getColor(context, R.color.colorAccent);
            adapter_ = new PlaylistAdapter(context, displayingPlaylist, accentColor);
        }
    }
    PlaylistUiSpec playlistUiSpec;

    public MetadataFragment() {}

    public static MetadataFragment newInstance(Context context, List<AudioFile> allAudioFiles,
                                               FragmentType type, Playlist activePlaylist,
                                               Toolbar toolbar) {
        MetadataFragment fragment = new MetadataFragment();
        fragment.allAudioFiles = allAudioFiles;
        fragment.type = type;
        fragment.toolbar = toolbar;

        fragment.metadataAdapter = new MetadataAdapter(new ArrayList<String>(), context);
        fragment.metadataViewStack = new Stack<>();

        fragment.playlistUiSpec = new PlaylistUiSpec(context, activePlaylist);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_metadata_view, container, false);

        listView = (ListView) rootView.findViewById(R.id.fragment_metadata_list_view);
        updateMetadataView(type);

        rootView.setFocusable(true);
        rootView.requestFocus();
        rootView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                boolean result = false;
                if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                    if (!metadataViewStack.isEmpty()) {
                        FragmentType type = metadataViewStack.pop();
                        updateMetadataView(type);
                        result = true;
                    }
                }

                    return result;
                }
            });

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

        updateMetadataView(FragmentType.PLAYLIST);
    }

    void updateMetadataView(FragmentType newType) {
        updateMetadataView_(newType, null);
    }


    private void updateMetadataView_(FragmentType newType, String filterBy) {
        // NOTE(doyle): All metadata views are top level unless it's a file view, which means we
        // need to start tracking the view stack, i.e. artist files should return to artist; artist
        // view itself should return to nothing
        if (newType == FragmentType.ARTIST_FILES ||
                newType == FragmentType.ALBUM_FILES) {
            // NOTE(doyle): Push old type
            metadataViewStack.push(type);
        } else {
            metadataViewStack.clear();
        }

        // TODO: Cache the lists instead of rebuilding each time
        SortedSet<String> dataForFragment = new TreeSet<>();
        switch(newType) {
            case ALBUM:
            case ARTIST: {

                final FragmentType detailedType;
                if (newType == FragmentType.ALBUM) {
                    for (AudioFile file: allAudioFiles) dataForFragment.add(file.album);
                    detailedType = FragmentType.ALBUM_FILES;
                    toolbar.setTitle("Album");
                } else {
                    for (AudioFile file: allAudioFiles) dataForFragment.add(file.artist);
                    detailedType = FragmentType.ARTIST_FILES;
                    toolbar.setTitle("Artist");
                }

                listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position,
                                            long id) {

                        TextView textView = (TextView) view.getTag();
                        updateMetadataView_(detailedType, textView.getText().toString());
                    }
                });
            } break;

            case ALBUM_FILES:
            case ARTIST_FILES: {
                if (Debug.CAREFUL_ASSERT(filterBy != null, this,
                        "Cannot call metadata file view without a filter")) {

                    if (newType == FragmentType.ARTIST_FILES) {
                        toolbar.setTitle("Artist: " + filterBy);
                        for (AudioFile file: allAudioFiles)
                            if (filterBy.equals(file.artist))
                                dataForFragment.add(file.title);
                    } else {
                        toolbar.setTitle("Album: " + filterBy);
                        for (AudioFile file: allAudioFiles)
                            if (filterBy.equals(file.album))
                                dataForFragment.add(file.title);
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
                toolbar.setTitle("Playlist: " + playlistUiSpec.displayingPlaylist.name);

                { // Update Playlist UI
                    Debug.INCREMENT_COUNTER(this, "updatePlaylistUi");

                    PlaylistUiSpec uiSpec = this.playlistUiSpec;
                    PlaylistAdapter adapter = uiSpec.adapter_;
                    if (uiSpec.displayingPlaylist == null || adapter == null) {
                        // NOTE(doyle): Update called before view is created, which is fine.
                        Debug.CAREFUL_ASSERT(false, this, "Displaying playlist or adapter is null");
                        return;
                    }

                    if (adapter.playlist != uiSpec.displayingPlaylist) {
                        adapter.playlist = uiSpec.displayingPlaylist;
                        adapter.notifyDataSetChanged();
                    }
                }

                listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                        PlaylistAdapter.AudioEntryInView entry =
                                (PlaylistAdapter.AudioEntryInView) view.getTag();
                        int index = entry.position;

                        /* Update playlist view after song click */
                        Playlist newPlaylist = playlistUiSpec.displayingPlaylist;
                        PlaylistAdapter adapter = playlistUiSpec.adapter_;
                        adapter.playlist = newPlaylist;

                        if (newPlaylist.index != index) {
                            newPlaylist.index = index;
                            listView.invalidateViews();
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

        this.type = newType;
        if (newType != FragmentType.PLAYLIST) {
            List<String> dataList = new ArrayList<>();
            dataList.addAll(dataForFragment);
            listView.setAdapter(metadataAdapter);
            metadataAdapter.stringList = dataList;
            metadataAdapter.notifyDataSetChanged();
        } else {
            listView.setAdapter(playlistUiSpec.adapter_);
        }
    }

    private static class MetadataAdapter extends BaseAdapter {
        List<String> stringList;
        private LayoutInflater inflater;

        MetadataAdapter(List<String> stringList, Context context) {
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
