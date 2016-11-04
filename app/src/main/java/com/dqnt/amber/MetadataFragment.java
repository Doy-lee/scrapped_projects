package com.dqnt.amber;


import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
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

public class MetadataFragment extends Fragment {
    private List<AudioFile> allAudioFiles;
    private ListView listView;
    private StringAdapter adapter;
    MainActivity.FragmentType type;

    private Stack<MainActivity.FragmentType> metadataViewStack;

    AudioFileClickListener listener;

    public MetadataFragment() {}

    public static MetadataFragment newInstance(Context context, List<AudioFile> allAudioFiles,
                                               MainActivity.FragmentType type) {
        MetadataFragment fragment = new MetadataFragment();
        fragment.allAudioFiles = allAudioFiles;
        fragment.type = type;
        fragment.adapter = new StringAdapter(new ArrayList<String>(), context);
        fragment.metadataViewStack = new Stack<>();

        /*
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        */

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
        */
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_artist, container, false);
        listView = (ListView) rootView.findViewById(R.id.fragment_artist_list_view);
        listView.setAdapter(adapter);
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

                            // TODO(doyle): Store file data into adapter, instead of search list
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

                            // TODO(doyle): Store file data into adapter, instead of search list
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

            default: {
                Debug.CAREFUL_ASSERT(false, this,
                        "Fragment type not handled in metadata fragment: " + type.toString());
            } break;
        }

        List<String> dataList = new ArrayList<>();
        dataList.addAll(dataForFragment);
        adapter.stringList = dataList;

        adapter.notifyDataSetChanged();
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

    private abstract class OnItemClick implements AdapterView.OnItemClickListener {
        @Override
        public abstract void onItemClick(AdapterView<?> parent, View view, int position, long id);
    }

}
