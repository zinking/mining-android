
package com.readmine.minereader;

import android.accounts.AccountManager;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.gms.auth.GoogleAuthException;
import com.jakewharton.disklrucache.DiskLruCache;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MainActivity extends ListActivity {

    public static final String K_OUTLINE_POS = "OUTLINE";
    public static final String K_FOLDER_POS = "FOLDER";
    public static final String K_FEED_URL = "FEED";
    private FeedAdapter feedsOpmlAdapter;
    private Intent i;
    private JSONArray omplOutlines;
    private JSONObject to = null;
    private int pos = -1;
    private SharedPreferences p;
    private String authToken = null;
    private MenuItem refreshMenuItem = null;
    private boolean refreshing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // load preferences
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        Log.d(MineRead.TAG, "Using URL " + MineRead.get(this).MINE_URL);

        try {
            Log.i(MineRead.TAG, "onCreate");
            setContentView(R.layout.activity_main);
            p = getPreferences(MODE_PRIVATE);
            feedsOpmlAdapter = new FeedAdapter(this, R.layout.item_row);
            setListAdapter(feedsOpmlAdapter);
            if (MineRead.get(this).listFeeds == null) {
                try {
                    BufferedReader br = new BufferedReader(new FileReader(MineRead.get(this).listFeedsCache));
                    try {
                        StringBuilder sb = new StringBuilder();
                        String line = br.readLine();

                        while (line != null) {
                            sb.append(line);
                            sb.append('\n');
                            line = br.readLine();
                        }
                        String s = sb.toString();
                        MineRead.get(this).listFeeds = new JSONObject(s);
                        MineRead.updateFeedProperties(this);
                        displayFeeds();
                        Log.e(MineRead.TAG, "read from feed cache");
                    }
                    finally {
                        br.close();
                    }
                } catch (Exception e) {
                    Log.e(MineRead.TAG, "br", e);
                }
            } else {
                displayFeeds();
            }
            start();
        } catch (Exception e) {
            Log.e(MineRead.TAG, "oc", e);
        }
    }

    protected void start() {
        refreshing = true;
        setRefreshing();
        if (!MineRead.get(this).loginDone) {
            if (p.contains(MineRead.P_ACCOUNT)) {
                Log.i(MineRead.TAG, "start gac");
                getAuthCookie();
            } else {
                Log.i(MineRead.TAG, "start pick account");
                getAuthCookie();
                //pickAccount();
            }
        } else if (MineRead.get(this).listFeeds == null) {
            Log.i(MineRead.TAG, "start fetch list of feeds");
            fetchListFeeds();
        } else {
            Log.i(MineRead.TAG, "start else");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        refreshMenuItem = menu.findItem(R.id.action_refresh);
        setRefreshing();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        try {
            switch (item.getItemId()) {
                case R.id.action_logout:
                    logout();
                    return true;
                case R.id.action_refresh:
                    refresh();
                    return true;
                case R.id.action_mark_read:
                    markRead();
                    return true;
                case R.id.action_preferences:
                    Intent launchPreferencesIntent = new Intent(this, SettingsActivity.class);
                    startActivity(launchPreferencesIntent);
                    return true;
            }
        } catch (Exception e) {
            Log.e(MineRead.TAG, "oois", e);
        }
        return super.onOptionsItemSelected(item);
    }

    protected void refresh() throws IOException, GoogleAuthException {
        // todo: make sure only one of this runs at once
        Log.i(MineRead.TAG, "refresh");
        MineRead.get(this).listFeeds = null;
        start();
    }

    protected void logout() {
        SharedPreferences.Editor e = p.edit();
        e.remove(MineRead.P_ACCOUNT);
        e.commit();
        pickAccount();
    }

    protected void markRead() {
        Log.i(MineRead.TAG, "mark read");
        JSONArray read = new JSONArray();
        markRead(read, omplOutlines);
        MineRead.addReq(this, new JsonArrayRequest(Request.Method.POST, MineRead.get(this).MINE_URL + "/user/mark-read", read, null, null));
        MineRead.updateFeedProperties(this);
        feedsOpmlAdapter.notifyDataSetChanged();
    }

    private void markRead(JSONArray read, JSONArray ja) {
        try {
            for (int i = 0; i < ja.length(); i++) {
                JSONObject o = ja.getJSONObject(i);
                if (o.has("Outline")) { //if folder, recursively mark the folder read
                    markRead(read, o.getJSONArray("Outline"));
                } else if (o.has("XmlUrl")) {
                    String u = o.getString("XmlUrl");
                    if (!MineRead.get(this).feed2Stories.isNull(u)) {
                        JSONArray ss = MineRead.get(this).feed2Stories.getJSONArray(u);
                        for (int j = 0; j < ss.length(); j++) {
                            JSONObject s = ss.getJSONObject(j);
                            read.put(new JSONObject()
                                            .put("Feed", u)
                                            .put("Story", s.getString("Id"))
                            );
                            s.put("read", true);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(MineRead.TAG, "mark read", e);
        }
    }

    protected void pickAccount() {
        Log.i(MineRead.TAG, "pickAccount");
        //Intent intent = AccountPicker.newChooseAccountIntent(null, null, new String[]{GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE}, false, null, null, null, null);
        //startActivityForResult(intent, MineRead.PICK_ACCOUNT_REQUEST);
    }

    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        try {
            if (requestCode == MineRead.PICK_ACCOUNT_REQUEST) {
                if (resultCode == RESULT_OK) {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    SharedPreferences.Editor e = p.edit();
                    e.putString(MineRead.P_ACCOUNT, accountName);
                    e.commit();
                    getAuthCookie();
                } else {
                    Log.e(MineRead.TAG, String.format("%d, %d, %s", requestCode, resultCode, data));
                    Log.e(MineRead.TAG, "pick not ok, try again");
                    //pickAccount();
                    getAuthCookie();
                }
            } else {
                Log.e(MineRead.TAG, String.format("activity result: %d, %d, %s", requestCode, resultCode, data));
            }
        } catch (Exception e) {
            Log.e(MineRead.TAG, "oar", e);
        }
    }

    protected void getAuthCookie() {
        Log.i(MineRead.TAG, "getAuthCookie");
        final Context c = this;
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    //String accountName = p.getString(MineRead.P_ACCOUNT, "");
                    //authToken = GoogleAuthUtil.getToken(c, accountName, MineRead.APP_ENGINE_SCOPE);
                    authToken = "something";
                    Log.e(MineRead.TAG, "auth: " + authToken);
                }
//                catch (UserRecoverableAuthException e) {
//                    Intent intent = e.getIntent();
//                    startActivityForResult(intent, MineRead.PICK_ACCOUNT_REQUEST);
//                }
                catch (Exception e) {
                    Log.e(MineRead.TAG, "get auth cookie", e);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void v) {
                if (authToken == null) {
                    return;
                }
                try {
                    //URL url = new URL(MineRead.get(c).MINE_URL + "/_ah/login" + "?continue=" + URLEncoder.encode(MineRead.get(c).MINE_URL, "UTF-8") + "&auth=" + URLEncoder.encode(authToken, "UTF-8"));
                    URL url = new URL(MineRead.get(c).MINE_URL + "/loginApi");
                    final JSONObject authParms = new JSONObject("{\"email\":\"zhangsan@mining.com\",\"pass\":\"123\"}");
                    MineRead.addReq(c, new JsonObjectRequest( url.toString(), authParms, new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject s) {
                            try{
                                Log.i(MineRead.TAG, "get response from auth");
                                authToken = s.getString("apiKey");
                                MineRead.get(c).loginDone = true;
                                fetchListFeeds();
                            }
                            catch(JSONException e){
                                Log.e(MineRead.TAG, "error parsing auth response", e);
                            }

                        }
                    }, new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError volleyError) {
                            Log.e(MineRead.TAG, volleyError.toString());
                            Toast toast = Toast.makeText(c, volleyError.getMessage(), Toast.LENGTH_LONG);
                            toast.show();
                            refreshing = false;
                            setRefreshing();
                        }
                    }
                    ));
                } catch (Exception e) {
                    Toast toast = Toast.makeText(c, "Error: could not log in; prefs reset", Toast.LENGTH_LONG);
                    toast.show();
                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(c);
                    sp.edit().clear().commit();
                    PreferenceManager.setDefaultValues(c, R.xml.preferences, true);
                    MineRead.get(c).MINE_URL = sp.getString(SettingsFragment.ServerDomain, getString(R.string.default_server_domain));
                    //pickAccount();
                    getAuthCookie();
                    Log.e(MineRead.TAG, "gac ope", e);
                }
            }
        };
        task.execute();
    }

    protected void addFeed(JSONObject opmlOutline) {
        try {
            MineRead.get(this).url2Opml.put(opmlOutline.getString("XmlUrl"), opmlOutline);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    protected void fetchListFeeds() {
        Log.i(MineRead.TAG, "fetchListFeeds");
        final Context c = this;
        MineRead.addReq(c, new JsonUTF8Request(
            Request.Method.GET, MineRead.get(this).MINE_URL + "/user/list-feeds", null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject jsonObject) {
                        MineRead.get(c).listFeeds = jsonObject;
                        MineRead.updateFeedProperties(c);
                        downloadStories();
                        displayFeeds();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(MineRead.TAG, error.toString());
                        Log.e(MineRead.TAG, "invalidate");
                        //GoogleAuthUtil.invalidateToken(c, authToken);
                        getAuthCookie();
                    }
                }
            ){
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> params = new HashMap<String, String>();
                    params.put("mining",authToken);
                    return params;
                }
            }
        );
    }

    protected void downloadStories() {
        try {
            final JSONArray feedStoryIdPairList = new JSONArray();
            JSONObject stories = MineRead.get(this).feed2Stories;
            if (stories == null) {
                return;
            }
            Iterator<String> keys = stories.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONArray feedStories = MineRead.get(this).feed2Stories.getJSONArray(key);
                for (int i = 0; i < feedStories.length(); i++) {
                    JSONObject feedStory = feedStories.getJSONObject(i);
                    JSONObject feedStoryIdPair = new JSONObject()
                            .put("Feed", key)
                            .put("Story", feedStory.getString("Id"));
                    String hash = MineRead.hashStory(feedStoryIdPair);
                    String storyContent = feedStory.getString("Content");
                    if (MineRead.get(this).storyCache.get(hash) == null) {
                        //feedStoryIdPairList.put(feedStoryIdPair);
                        cacheStory(feedStoryIdPair,storyContent);
                    }
                }
            }
            Log.e(MineRead.TAG, String.format("downloading %d feed2Stories", feedStoryIdPairList.length()));
            if (feedStoryIdPairList.length() > 0) {
//                MineRead.addReq(this, new JsonArrayRequest(Request.Method.POST, MineRead.get(this).MINE_URL + "/user/get-contents", feedStoryIdPairList, new Response.Listener<JSONArray>() {
//                    @Override
//                    public void onResponse(JSONArray jsonArray) {
//                        cacheStories(feedStoryIdPairList, jsonArray);
//                    }
//                }, null));
            }
        } catch (Exception e) {
            Log.e(MineRead.TAG, "ds", e);
        }
    }

    protected void cacheStory(JSONObject feedStoryIdPair, String storyContent){
        try{
            String key = MineRead.hashStory(feedStoryIdPair);
            DiskLruCache.Editor edit = MineRead.get(this).storyCache.edit(key);
            edit.set(0, storyContent);
            edit.commit();
        } catch (JSONException e) {
            Log.e(MineRead.TAG, "cache story json", e);
        } catch (IOException e) {
            Log.e(MineRead.TAG, "cache story io", e);
        }
    }

    protected void cacheStories(JSONArray feedStoryIdPairList, JSONArray contents) {
        for (int i = 0; i < feedStoryIdPairList.length(); i++) {
            try {
                JSONObject feedStoryIdPair = feedStoryIdPairList.getJSONObject(i);
                String content = contents.getString(i);
                cacheStory(feedStoryIdPair,content);
            }
            catch (JSONException e) {
                Log.e(MineRead.TAG, "cachestories json", e);
            }
        }
        try {
            MineRead.get(this).storyCache.flush();
        } catch (IOException e) {
            Log.e(MineRead.TAG, "cache flush", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // a sub folder may have updated the unread counts, so force a refresh
        feedsOpmlAdapter.notifyDataSetChanged();
    }

    protected void displayFeeds() {
        Log.i(MineRead.TAG, "displayFeeds");
        refreshing = false;
        setRefreshing();
        try {
            i = getIntent();
            feedsOpmlAdapter.clear();

            if (i.hasExtra(K_OUTLINE_POS)) {//???
                pos = i.getIntExtra(K_OUTLINE_POS, -1);
                try {
                    JSONArray ta = MineRead.get(this).listFeeds.getJSONArray("Opml");
                    to = ta.getJSONObject(pos);
                    String t = to.getString("Title");
                    setTitle(t);
                    addItem(t, OutlineType.FOLDER, t);
                    omplOutlines = to.getJSONArray("Outline");
                    parseJSON();
                } catch (JSONException e) {
                    Log.e(MineRead.TAG, "pos", e);
                }
            } else {
                addItem("all items", OutlineType.ALL, null);
                MineRead.get(this).url2Opml = new HashMap<String, JSONObject>();
                JSONObject lj = MineRead.get(this).listFeeds;
                if (lj == null || !lj.has("Opml")) {
                    return;
                }
                omplOutlines = lj.getJSONArray("Opml");
                for (int i = 0; i < omplOutlines.length(); i++) {
                    JSONObject opmlOutline = omplOutlines.getJSONObject(i);
                    if (opmlOutline.has("Outline")) {//if ith outline is folder
                        JSONArray outa = opmlOutline.getJSONArray("Outline");
                        for (int j = 0; j < outa.length(); j++) {
                            addFeed(outa.getJSONObject(j));
                        }
                    } else {
                        addFeed(opmlOutline);
                    }
                }
                parseJSON();
            }
        } catch (JSONException e) {
            Log.e(MineRead.TAG, "display url2Opml json", e);
        }
    }

    protected void addItem(String i, OutlineType type, String key) {
        feedsOpmlAdapter.add(new Outline(this, i, type, key));
    }

    protected void parseJSON() {
        try {
            for (int i = 0; i < omplOutlines.length(); i++) {
                JSONObject o = omplOutlines.getJSONObject(i);
                String t = o.getString("Title");
                if (o.has("Outline")) {
                    addItem(t, OutlineType.FOLDER, t);
                } else if (o.has("XmlUrl")) {
                    String u = o.getString("XmlUrl");
                    addItem(t, OutlineType.FEED, u);
                }
            }
        } catch (JSONException e) {
            Log.e(MineRead.TAG, "parse json", e);
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        try {
            if (position == 0) {
                Intent i = new Intent(this, StoryListActivity.class);
                i.putExtra(K_FOLDER_POS, pos);
                startActivity(i);
            } else {
                JSONObject o = omplOutlines.getJSONObject(position - 1);
                if (o.has("Outline")) {
                    Intent i = new Intent(this, MainActivity.class);
                    i.putExtra(K_OUTLINE_POS, position - 1);
                    startActivity(i);
                } else {
                    Intent i = new Intent(this, StoryListActivity.class);
                    i.putExtra(K_FEED_URL, o.getString("XmlUrl"));
                    startActivity(i);
                }
            }
        } catch (JSONException e) {
            Log.e(MineRead.TAG, "list item click error", e);
        }
    }

    private void setRefreshing() {
        if (refreshMenuItem == null) return;

        if (refreshing) {
            refreshMenuItem.setActionView(R.layout.actionbar_refresh_progress);
            refreshMenuItem.expandActionView();
        } else
            refreshMenuItem.setActionView(null);
    }

}