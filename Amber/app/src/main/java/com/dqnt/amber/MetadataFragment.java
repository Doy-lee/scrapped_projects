package com.dqnt.amber;


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.TreeSet;

import com.dqnt.amber.Models.AudioFile;
import com.dqnt.amber.Models.Playlist;
import com.dqnt.amber.MainActivity.FragmentType;
import com.google.gson.Gson;

public class MetadataFragment extends Fragment {
    static class DisplaySpec {
        final FragmentType type;
        long playlistKey;
        int listPos;

        DisplaySpec(FragmentType type) {
            this.type = type;
            this.playlistKey = -1;
            listPos = 0;
        }
    }
    private List<DisplaySpec> displaySpecList;
    private int currDisplaySpecIndex;
    private Stack<DisplaySpec> metadataDisplayStack;

    private List<AudioFile> allAudioFiles;
    private MetadataAdapter metadataAdapter;
    ListView listView;
    private Toolbar toolbar;
    private boolean init;

    AudioFileClickListener listener;

    static class PlaylistUiSpec {
        Playlist displayingPlaylist;
        final PlaylistAdapter adapter_;

        PlaylistUiSpec(Context context, Playlist displayingPlaylist) {
            this.displayingPlaylist = displayingPlaylist;

            int accentColor = ContextCompat.getColor(context, R.color.colorAccent);
            adapter_ = new PlaylistAdapter(context, displayingPlaylist, accentColor);
        }
    }
    PlaylistUiSpec playlistUiSpec;

    public MetadataFragment() {}

    public static MetadataFragment newInstance() {
        MetadataFragment result = new MetadataFragment();
        result.init = false;
        return result;
    }

    public static MetadataFragment newInstance(Activity context, List<AudioFile> allAudioFiles,
                                               FragmentType type, Playlist activePlaylist,
                                               Toolbar toolbar) {
        MetadataFragment result = new MetadataFragment();
        result.init_(context, allAudioFiles, type, activePlaylist, toolbar, null);
        return result;
    }

    public boolean isInit() { return init; }

    public void init(Activity context, List<AudioFile> allAudioFiles,
                      FragmentType type, Playlist activePlaylist,
                      Toolbar toolbar) {
        init_(context, allAudioFiles, type, activePlaylist, toolbar, null);
    }

    public void init(Activity context, List<AudioFile> allAudioFiles,
                      FragmentType type, Playlist activePlaylist,
                      Toolbar toolbar, DisplaySpec displaySpec) {
        init_(context, allAudioFiles, type, activePlaylist, toolbar, displaySpec);
    }

    private void init_(Activity context, List<AudioFile> allAudioFiles,
                       FragmentType type, Playlist activePlaylist,
                       Toolbar toolbar, DisplaySpec displaySpec) {

        if (init) {
            Debug.CAREFUL_ASSERT(false, this, "Metadata fragment is already initialised");
            return;
        }

        this.allAudioFiles = allAudioFiles;

        DisplaySpec specToUse = displaySpec;
        if (displaySpec == null) {
            specToUse = new DisplaySpec(type);
            if (type == FragmentType.PLAYLIST) {
                specToUse.playlistKey = activePlaylist.dbKey;
            }
        }

        displaySpecList = new ArrayList<>();
        displaySpecList.add(specToUse);

        currDisplaySpecIndex = displaySpecList.size() - 1;
        metadataDisplayStack = new Stack<>();

        this.toolbar = toolbar;
        metadataAdapter = new MetadataAdapter(new ArrayList<ListItemEntry>(), context);
        playlistUiSpec = new PlaylistUiSpec(context, activePlaylist);

        Handler handler = new Handler();
        Debug.UiUpdateAndRender debugRenderer =
                new Debug.UiUpdateAndRender("METADATA", context, handler, 1, true) {
                    @Override
                    public void renderElements() {
                        for (DisplaySpec spec: displaySpecList) {
                            pushText("==" + spec.type.toString() + "==");
                            pushClass(spec, true, true, false);
                        }
                    }
                };

        init = true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_metadata_view, container, false);

        listView = (ListView) rootView.findViewById(R.id.fragment_metadata_list_view);
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {

            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

            }
        });

        listView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {

            }
        });

        rootView.setFocusable(true);
        rootView.requestFocus();
        rootView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                boolean result = false;
                if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                    if (init) {
                        if (!metadataDisplayStack.isEmpty()) {
                            DisplaySpec spec = metadataDisplayStack.pop();
                            updateMetadataView(spec.type);
                            result = true;
                        }
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

        // TODO(doyle): We store display spec but only use it to restore the last view playlist, not index
        Gson gson = new Gson();
        DisplaySpec currDisplaySpec = displaySpecList.get(currDisplaySpecIndex);
        currDisplaySpec.listPos = listView.getFirstVisiblePosition();
        String displaySpecInJson = gson.toJson(currDisplaySpec);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());

        sharedPref.edit().putString(getString(R.string.internal_pref_display_spec_key),
                displaySpecInJson).apply();
    }

    // NOTE(doyle): Need current playing playlist, because if it is'nt the current playing playlist,
    // we don't want to highlight an entry, that may be left active since it last played
    void changeDisplayingPlaylist(Playlist playingPlaylist, Playlist newPlaylist) {
        playlistUiSpec.displayingPlaylist = newPlaylist;

        if (playlistUiSpec.displayingPlaylist != playingPlaylist) {
            playlistUiSpec.displayingPlaylist.index = -1;
        }
    }

    void updateMetadataView(FragmentType newType) {
        updateMetadataView_(newType, playlistUiSpec, displaySpecList, metadataAdapter,
                listView, null);
    }

    FragmentType getFragmentType() {
        return displaySpecList.get(currDisplaySpecIndex).type;
    }

    private void updateMetadataView_(final FragmentType newType,
                                     final PlaylistUiSpec playlistUiSpec,
                                     final List<DisplaySpec> displaySpecList,
                                     final MetadataAdapter metadataAdapter,
                                     final ListView listView,
                                     final String filterBy) {

        /* Store the list position of the current fragment display */
        DisplaySpec oldSpec = displaySpecList.get(currDisplaySpecIndex);
        oldSpec.listPos = listView.getFirstVisiblePosition();

        { // Generate the data for the new fragment
            TreeSet<ListItemEntry> dataForFragment = new TreeSet<>();
            // TODO: Cache the lists instead of rebuilding each time
            switch (newType) {
                case ALBUM:
                case ARTIST:
                case ALBUM_ARTIST:
                case GENRE: {

                    final FragmentType detailedType;
                    if (newType == FragmentType.ALBUM) {
                        for (AudioFile file : allAudioFiles) {
                            String subtitle;
                            if (file.albumArtist.equals("Unknown")) subtitle = file.artist;
                            else subtitle = file.albumArtist;

                            ListItemEntry entry = new ListItemEntry(file.album, subtitle);
                            dataForFragment.add(entry);
                        }
                        detailedType = FragmentType.ALBUM_FILES;
                        toolbar.setTitle("Album");
                    } else if (newType == FragmentType.ARTIST) {
                        for (AudioFile file : allAudioFiles) {
                            ListItemEntry entry = new ListItemEntry(file.artist, null);
                            dataForFragment.add(entry);
                        }
                        detailedType = FragmentType.ARTIST_FILES;
                        toolbar.setTitle("Artist");
                    } else if (newType == FragmentType.ALBUM_ARTIST) {
                        for (AudioFile file : allAudioFiles) {
                            ListItemEntry entry = new ListItemEntry(file.albumArtist, null);
                            dataForFragment.add(entry);
                        }
                        detailedType = FragmentType.ALBUM_ARTIST_FILES;
                        toolbar.setTitle("Album Artist");
                    } else {
                        for (AudioFile file : allAudioFiles) {
                            ListItemEntry entry = new ListItemEntry(file.genre, null);
                            dataForFragment.add(entry);
                        }
                        detailedType = FragmentType.GENRE_FILES;
                        toolbar.setTitle("Genre");
                    }

                    listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position,
                                                long id) {

                            ListItemEntryView entry = (ListItemEntryView) view.getTag();
                            updateMetadataView_(detailedType, playlistUiSpec, displaySpecList,
                                    metadataAdapter, listView, entry.title.getText().toString());
                        }
                    });
                }
                break;

                case ALBUM_FILES:
                case ARTIST_FILES:
                case ALBUM_ARTIST_FILES:
                case GENRE_FILES: {
                    if (Debug.CAREFUL_ASSERT(filterBy != null, this,
                            "Cannot call metadata file view without a filter")) {

                        if (newType == FragmentType.ARTIST_FILES) {
                            for (AudioFile file : allAudioFiles) {
                                if (filterBy.equals(file.artist)) {
                                    String artistAndAlbumString = file.artist + " | " + file.album;
                                    ListItemEntry entry = new ListItemEntry(file.title, artistAndAlbumString);
                                    dataForFragment.add(entry);
                                }
                            }

                        } else if (newType == FragmentType.ALBUM_FILES) {
                            for (AudioFile file : allAudioFiles) {
                                if (filterBy.equals(file.album)) {
                                    String artistAndAlbumString = file.artist + " | " + file.album;
                                    ListItemEntry entry = new ListItemEntry(file.title, artistAndAlbumString);
                                    dataForFragment.add(entry);
                                }
                            }

                        } else if (newType == FragmentType.ALBUM_ARTIST_FILES) {
                            for (AudioFile file : allAudioFiles) {
                                if (filterBy.equals(file.albumArtist)) {
                                    String artistAndAlbumString = file.artist + " | " + file.album;
                                    ListItemEntry entry = new ListItemEntry(file.title, artistAndAlbumString);
                                    dataForFragment.add(entry);
                                }
                            }

                        } else if (newType == FragmentType.GENRE_FILES) {
                            for (AudioFile file : allAudioFiles) {
                                if (filterBy.equals(file.genre)) {
                                    String artistAndAlbumString = file.artist + " | " + file.album;
                                    ListItemEntry entry = new ListItemEntry(file.title, artistAndAlbumString);
                                    dataForFragment.add(entry);
                                }
                            }
                        }

                        toolbar.setTitle(filterBy);

                        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick
                                    (AdapterView<?> parent, View view, int position, long id) {
                                Models.Playlist playlist =
                                        new Models.Playlist("Now playing");

                                // TODO(doyle): Store file data into metadataAdapter, instead of search list
                                // again to find the file to play
                                ListItemEntryView entry = (ListItemEntryView) view.getTag();
                                String audioTitleToPlay = entry.title.getText().toString();
                                for (AudioFile file : allAudioFiles) {
                                    if (file.title.equals(audioTitleToPlay)) {
                                        playlist.contents.add(file);
                                        break;
                                    }
                                }

                                listener.audioFileClicked(playlist);
                            }
                        });
                    }
                }
                break;

                case PLAYLIST: {
                    toolbar.setTitle(playlistUiSpec.displayingPlaylist.name);

                    { // Update Playlist UI
                        Debug.INCREMENT_COUNTER(this, "updatePlaylistUi");

                        PlaylistAdapter adapter = playlistUiSpec.adapter_;
                        if (playlistUiSpec.displayingPlaylist == null || adapter == null) {
                            // NOTE(doyle): Update called before view is created, which is fine.
                            Debug.CAREFUL_ASSERT(false, this, "Displaying playlist or adapter is null");
                            return;
                        }

                        if (adapter.playlist != playlistUiSpec.displayingPlaylist) {
                            adapter.playlist = playlistUiSpec.displayingPlaylist;
                        }
                        // NOTE(doyle): Playlist could be the same but new files in list
                        adapter.notifyDataSetChanged();
                    }

                    listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            /* Update playlist view after song click */
                            Playlist newPlaylist = playlistUiSpec.displayingPlaylist;
                            PlaylistAdapter adapter = playlistUiSpec.adapter_;
                            adapter.playlist = newPlaylist;

                            if (newPlaylist.index != position) {
                                newPlaylist.index = position;
                                listView.invalidateViews();
                            }

                            listener.audioFileClicked(playlistUiSpec.displayingPlaylist);
                        }
                    });
                } break;

                default: {
                    Debug.CAREFUL_ASSERT(false, this,
                            "Fragment type not handled in metadata fragment: " +
                                    newType.toString());
                } break;
            }

            /* Switch to new fragment data display */
            if (newType != FragmentType.PLAYLIST) {
                List<ListItemEntry> dataList = new ArrayList<>();
                dataList.addAll(dataForFragment);
                listView.setAdapter(metadataAdapter);
                metadataAdapter.listEntries = dataList;
                metadataAdapter.notifyDataSetChanged();
            } else {
                listView.setAdapter(playlistUiSpec.adapter_);
            }
        }

        { // Special display spec for file views, dummy spec to ignore list pos

            // NOTE(doyle): All metadata views are top level unless it's a file view, which means we
            // need to start tracking the view stack, i.e. artist files should return to artist; artist
            // view itself should return to nothing
            if (newType == FragmentType.ARTIST_FILES ||
                    newType == FragmentType.ALBUM_FILES ||
                    newType == FragmentType.ALBUM_ARTIST_FILES ||
                    newType == FragmentType.GENRE_FILES) {

                metadataDisplayStack.push(oldSpec);

                boolean matched = false;
                for (int i = 0; i < displaySpecList.size(); i++) {
                    DisplaySpec spec = displaySpecList.get(i);
                    if (spec.type == FragmentType.INVALID) {
                        currDisplaySpecIndex = i;
                        matched = true;
                        break;
                    }
                }

                if (!matched) {
                    DisplaySpec dummySpec = new DisplaySpec(FragmentType.INVALID);
                    displaySpecList.add(dummySpec);
                    currDisplaySpecIndex = displaySpecList.size() - 1;
                }

                return;
            }
        }

        // NOTE(doyle): In special display spec, we return early if valid, so always valid to clear
        metadataDisplayStack.clear();

        { // Get the display spec for the new fragment (create or grab pre-existing if it exists)
            DisplaySpec newSpec = null;
            /* Get the display spec of the new fragment, if it exists, else create*/
            // NOTE(doyle): For playlists, we need a way to store list positions for different
            // playlists, so we also need to store some way of identifiying them
            boolean matched = false;
            for (int i = 0; i < displaySpecList.size(); i++) {
                DisplaySpec spec = displaySpecList.get(i);
                if (spec.type == newType) {

                    boolean valid = false;
                    if (spec.type != FragmentType.PLAYLIST)
                        valid = true;
                    else if (spec.playlistKey == playlistUiSpec.displayingPlaylist.dbKey)
                        valid = true;

                    if (valid) {
                        newSpec = spec;
                        matched = true;
                        currDisplaySpecIndex = i;
                        break;
                    }
                }
            }

            // NOTE(doyle): Doesn't exists yet, so we create, display spec is lazily created
            if (!matched) {
                newSpec = new DisplaySpec(newType);

                if (newType == FragmentType.PLAYLIST)
                    newSpec.playlistKey = playlistUiSpec.displayingPlaylist.dbKey;

                displaySpecList.add(newSpec);
                currDisplaySpecIndex = displaySpecList.size() - 1;

                Debug.CAREFUL_ASSERT(
                        displaySpecList.get(displaySpecList.size() - 1) == newSpec,
                        this, "Implementation behaviour changed. " +
                                "Expected add to array list to append to end");
            }

            listView.setSelection(newSpec.listPos);
        }
    }

    private class ListItemEntry implements Comparable<ListItemEntry> {
        final String title;
        final String subtitle;

        public ListItemEntry(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }

        @Override
        public int compareTo(ListItemEntry o) {
            return this.title.compareTo(o.title);
        }
    }

    private static class ListItemEntryView {
        final TextView title;
        final TextView subtitle;

        ListItemEntryView(TextView title, TextView subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private static class MetadataAdapter extends BaseAdapter {
        List<ListItemEntry> listEntries;
        private LayoutInflater inflater;

        MetadataAdapter(List<ListItemEntry> listEntries, Context context) {
            this.listEntries = listEntries;
            inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            int result = listEntries.size();
            return result;
        }

        @Override
        public ListItemEntry getItem(int position) {
            if (listEntries != null) {
                return listEntries.get(position);
            }
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ListItemEntryView entry;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.list_item, parent, false);

                TextView title = (TextView) convertView.findViewById(R.id.list_item_title_text_view);
                TextView subtitle = (TextView) convertView.findViewById(R.id.list_item_subtitle_text_view);
                entry = new ListItemEntryView(title, subtitle);

                convertView.setTag(entry);
            } else {
                entry = (ListItemEntryView) convertView.getTag();
            }

            ListItemEntry entryData = listEntries.get(position);
            entry.title.setText(entryData.title);
            entry.subtitle.setText(entryData.subtitle);

            return convertView;
        }
    }

}
