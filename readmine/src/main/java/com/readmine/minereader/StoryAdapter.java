package com.readmine.minereader;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.json.JSONObject;

public class StoryAdapter extends ArrayAdapter<JSONObject> {

    private final int rowResourceId;
    protected Context c;

    public StoryAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
        this.rowResourceId = textViewResourceId;
        this.c = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        TextView rowView;
        if (convertView != null) {
            rowView = (TextView) convertView;
        } else {
            rowView = (TextView) inflater.inflate(rowResourceId, parent, false);
        }

        JSONObject story = getItem(position);
        String title = null;
        try {
            title = story.getString("Title");
            if (title.length() == 0) title = getContext().getString(R.string.title_unknown);
            //title += " - " + MineRead.get(c).url2Opml.get(story.getString("Feed")).getString("Title");
        } catch (Exception e) {
            Log.e(MineRead.TAG, e.getMessage(), e);
        }
        rowView.setText(title);
        rowView.setTypeface(null, story.has("Read") ? Typeface.NORMAL : Typeface.BOLD);
        return rowView;
    }
}