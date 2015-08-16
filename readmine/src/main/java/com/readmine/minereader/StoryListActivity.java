package com.readmine.minereader;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import com.android.volley.Request;
import com.android.volley.Response;
import com.jakewharton.disklrucache.DiskLruCache;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

public class StoryListActivity extends ListActivity {

    private StoryAdapter storyAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_storylist);
        storyAdapter = new StoryAdapter(this, android.R.layout.simple_list_item_1);
        setListAdapter(storyAdapter);
        try {
            JSONObject feed2Stories = MineRead.get(this).feed2Stories;
            ArrayList<JSONObject> storiesList = new ArrayList<JSONObject>();

            Intent it = getIntent();
            int p = it.getIntExtra(MainActivity.K_FOLDER_POS, -1);
            if (it.hasExtra(MainActivity.K_FEED_URL)) {
                String feedUrl = it.getStringExtra(MainActivity.K_FEED_URL);
                setTitle(MineRead.get(this).url2Opml.get(feedUrl).getString("Title"));
                addFeed(storiesList, feed2Stories, feedUrl);

                final Context c = this;
                AsyncTask<String, Void, Void> task = new AsyncTask<String, Void, Void>() {
                    @Override
                    protected Void doInBackground(String... params) {
                        try {
                            String iconURL = MineRead.getIcon(c, params[0]);
                            //skip favicon for the moment
//                            if (iconURL != null) {
//                                Bitmap bi = Picasso.with(c).load(iconURL).resize(128, 128).get();
//                                BitmapDrawable bd = new BitmapDrawable(getResources(), bi);
//                                getActionBar().setIcon(bd);
//                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return null;
                    }
                };
                //task.execute(feedUrl);
            } else if (p >= 0) {
                JSONArray a = MineRead.get(this).listFeeds.getJSONArray("Opml");
                JSONObject folder = a.getJSONObject(p);
                setTitle(folder.getString("Title"));
                a = folder.getJSONArray("Outline");
                for (int i = 0; i < a.length(); i++) {
                    JSONObject f = a.getJSONObject(i);
                    addFeed(storiesList, feed2Stories, f.getString("XmlUrl"));
                }
            } else {
                setTitle(R.string.all_items);
                if (feed2Stories != null) {
                    Iterator<String> keys = feed2Stories.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        addFeed(storiesList, feed2Stories, key);
                    }
                }
            }

            Collections.sort(storiesList, new StoryComparator());
            Collections.reverse(storiesList);
            for (JSONObject story : storiesList) {
                storyAdapter.add(story);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /*
     * apply feedUrl onto story, return the stories list
     */
    private void addFeed(ArrayList<JSONObject> sl, JSONObject feed2Stories, String feed) {
        if (feed2Stories == null) {
            return;
        }
        try {
            JSONArray sa = feed2Stories.getJSONArray(feed);
            for (int i = 0; i < sa.length(); i++) {
                JSONObject s = sa.getJSONObject(i);
                s.put("Feed", feed);
                sl.add(s);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        final JSONObject storyJson = storyAdapter.getItem(position);
        final String feed;
        final String storyId;
        final Intent i = new Intent(this, StoryActivity.class);
        i.putExtra(StoryActivity.K_STORY, storyJson.toString());
        try {
            feed = storyJson.getString("Feed");
            storyId = storyJson.getString("Id");
            String key = MineRead.hashStory(feed, storyId);
            DiskLruCache.Snapshot s = MineRead.get(this).storyCache.get(key);
            if (s != null) {
                String c = s.getString(0);
                i.putExtra(StoryActivity.K_CONTENT, c);
            } else {
                // if we didn't fetch from cache, just download it
                // todo: populate the cache

                JSONArray a = new JSONArray();
                JSONObject o = new JSONObject();
                o.put("Feed", feed);
                o.put("Story", storyId);
                a.put(o);

                MineRead.addReq(this, new JsonArrayRequest(Request.Method.POST, MineRead.get(this).MINE_URL + "/user/get-contents", a, new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray jsonArray) {
                        try {
                            String r = jsonArray.getString(0);
                            i.putExtra(StoryActivity.K_CONTENT, r);
                            Log.i("mine read", "download story");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, null));
            }
            startActivity(i);
            markRead(position);
        } catch (JSONException e) {
            return;
        } catch (IOException e) {
            // todo: perhaps not return, since it's just the cache not being available
            return;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.storylist, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        try {
            switch (item.getItemId()) {
                case R.id.action_mark_read:
                    markRead();
                    return true;
            }
        } catch (Exception e) {
            Log.e(MineRead.TAG, "oois", e);
        }
        return super.onOptionsItemSelected(item);
    }

    protected void markRead() throws JSONException {
        Log.e(MineRead.TAG, "mark read");
        markRead(-1);
    }

    private void markRead(int position) throws JSONException {
        JSONArray read = new JSONArray();

        if (position >= 0) {
            markReadStory(read, position);
        } else {
            for (int i = 0; i < storyAdapter.getCount(); i++) {
                markReadStory(read, i);
            }
        }
        if (read.length() > 0) {
            MineRead.addReq(this, new JsonArrayRequest(Request.Method.POST, MineRead.get(this).MINE_URL + "/user/mark-read", read, null, null));
            MineRead.updateFeedProperties(this);
            storyAdapter.notifyDataSetChanged();
        }
    }

    private void markReadStory(JSONArray read, int position) throws JSONException {
        JSONObject so = storyAdapter.getItem(position);
        if (!so.has("Read")) {
            so.put("Read", true);
            String feed = so.getString("Feed");
            String story = so.getString("Id");
            read.put(new JSONObject()
                            .put("Feed", feed)
                            .put("Story", story)
            );
        }
    }

    public class StoryComparator implements Comparator<JSONObject> {
        @Override
        public int compare(JSONObject o1, JSONObject o2) {
            int c = 0;
            try {
                c = new Long(o1.getLong("Date")).compareTo(new Long(o2.getLong("Date")));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return c;
        }
    }
}