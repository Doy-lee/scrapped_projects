package com.dqnt.amber;


import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import com.dqnt.amber.PlaybackData.AudioFile;


public class ArtistFragment extends Fragment {
    private List<AudioFile> allAudioFiles;

    public ArtistFragment() {}

    public static ArtistFragment newInstance(List<AudioFile> allAudioFiles) {
        ArtistFragment fragment = new ArtistFragment();
        fragment.allAudioFiles = allAudioFiles;

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
        ListView artistListView = (ListView) rootView.findViewById(R.id.fragment_artist_list_view);

        artistListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            }
        });

        SortedSet<String> artistSet = new TreeSet<>();
        for (AudioFile file: allAudioFiles) {
            artistSet.add(file.artist);
        }
        List<String> artistList = new ArrayList<>();
        artistList.addAll(artistSet);

        StringAdapter adapter = new StringAdapter(artistList, getContext());
        artistListView.setAdapter(adapter);
        return rootView;
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
