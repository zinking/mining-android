package com.readmine.minereader;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.NoCache;
import com.jakewharton.disklrucache.DiskLruCache;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

public final class MineRead {
    public static final String TAG = "mineread";
    public static final int PICK_ACCOUNT_REQUEST = 1;
    public static final String APP_ENGINE_SCOPE = "ah";
    public static final String P_ACCOUNT = "ACCOUNT_NAME";
    private static final MineRead INSTANCE = new MineRead();
    public JSONObject listFeeds = null;
    public JSONObject feed2Stories = null;
    public HashMap<String, JSONObject> url2Opml;
    public DiskLruCache storyCache = null;
    public UnreadCounts unread = null;
    public String MINE_URL = "";
    boolean loginDone = false;
    File listFeedsCache = null;
    private RequestQueue rq = null;
    private HashMap<String, String> icons = new HashMap<String, String>();

    private MineRead() {
        if (INSTANCE != null) {
            throw new IllegalStateException("Already instantiated");
        }
    }

    public static MineRead get(Context c) {
        MineRead g = INSTANCE;
        //clearSharedPreferences(c);
        try {
            if (g.listFeedsCache == null) {
                g.listFeedsCache = new File(c.getCacheDir(), "listFeedsCache");
            }
            if (g.storyCache == null) {
                File f = c.getCacheDir();
                f = new File(f, "storyCache");
                g.storyCache = DiskLruCache.open(f, 1, 1, (1 << 20) * 5);
            }
            if (g.MINE_URL == null || g.MINE_URL == "") {
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(c);
                g.MINE_URL = sharedPref.getString(SettingsActivity.KEY_PREF_URL, c.getString(R.string.default_server_domain));
            }
            if (g.url2Opml == null) {
                g.url2Opml = new HashMap<String, JSONObject>();
            }
            if (g.unread == null) {
                g.unread = new UnreadCounts();
            }
            if (g.listFeeds == null) {
                g.listFeeds = new JSONObject();
            }
        } catch (Exception e) {
            Log.e(MineRead.TAG, "get", e);
        }
        return g;
    }

    public static void clearSharedPreferences(Context ctx){
        File dir = new File(ctx.getFilesDir().getParent() + "/shared_prefs/");
        String[] children = dir.list();
        for (int i = 0; i < children.length; i++) {
            // clear each of the prefrances
            ctx.getSharedPreferences(children[i].replace(".xml", ""), Context.MODE_PRIVATE).edit().clear().commit();
        }
        // Make sure it has enough time to save all the commited changes
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        for (int i = 0; i < children.length; i++) {
            // delete the files
            new File(dir, children[i]).delete();
        }
    }

    public static void SetURL(String url) {
        INSTANCE.MINE_URL = url;
    }

    public static String getIcon(Context c, String f) {
        return get(c).icons.get(f);
    }

    public static void updateFeedProperties(Context c) {
        get(c).doUpdateFeedProperties(c);
    }

    public static String hashStory(JSONObject j) throws JSONException {
        return hashStory(j.getString("Feed"), j.getString("Story"));
    }

    public static String hashStory(String feed, String story) {
        MessageDigest cript = null;
        try {
            cript = MessageDigest.getInstance("SHA-1");
            cript.reset();
            cript.update(feed.getBytes("utf8"));
            cript.update("|".getBytes());
            cript.update(story.getBytes());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String sha = new BigInteger(1, cript.digest()).toString(16);
        return sha;
    }

    public static void addReq(Context c, Request r) {
        MineRead g = get(c);
        if (g.rq == null) {
            g.rq = new RequestQueue(new NoCache(), new BasicNetwork(new OkHttpStack()));
            g.rq.start();
        }
        g.rq.add(r);
    }

    private void doUpdateFeedProperties(Context c) {
        final String suffix = "=s16";
        try {
            Log.i(TAG, "update feed properties");
            //if (listFeeds.has("ErrorSubscription") && listFeeds.getBoolean("ErrorSubscription")) {
            //    Toast.makeText(c, "Free trial ended. Please subscribe on the website.", Toast.LENGTH_LONG).show();
            //    return;
            //}


            feed2Stories = listFeeds.getJSONObject("Stories");
            unread = new UnreadCounts();
            JSONArray opml = listFeeds.getJSONArray("Opml");
            updateFeedProperties(null, opml);
            HashMap<String, String> ic = new HashMap<String, String>();
            opml = listFeeds.getJSONArray("Feeds");
            for (int i = 0; i < opml.length(); i++) {
                JSONObject o = opml.getJSONObject(i); //this is one feed
                String im = o.getString("Image");
                if (im.length() == 0) {
                    continue;
                }
                if (im.endsWith(suffix)) {
                    im = im.substring(0, im.length() - suffix.length());
                }
                ic.put(o.getString("Url"), im);
            }
            icons = ic;
        } catch (JSONException e) {
            Log.e(TAG, "update feed properties, parsing error", e);
        }
    }

    private void updateFeedProperties(String folder, JSONArray opml) {
        try {
            for (int i = 0; i < opml.length(); i++) {
                JSONObject outline = opml.getJSONObject(i);
                if (outline.has("Outline")) { // this opml outline is a folder
                    updateFeedProperties(outline.getString("Title"), outline.getJSONArray("Outline"));
                } else {
                    String f = outline.getString("XmlUrl");
                    if (!feed2Stories.has(f)) { //if feed feed2Stories map don't have the data
                        continue;
                    }
                    JSONArray us = feed2Stories.getJSONArray(f);
                    Integer c = 0;
                    for (int j = 0; j < us.length(); j++) {
                        if (!us.getJSONObject(j).optBoolean("read", false)) {
                            c++;
                        }
                    }
                    if (c == 0) {
                        continue;
                    }
                    unread.All += c;
                    if (!unread.Feeds.containsKey(f)) {
                        unread.Feeds.put(f, 0);
                    }
                    unread.Feeds.put(f, unread.Feeds.get(f) + c);
                    if (folder != null) {
                        if (!unread.Folders.containsKey(folder)) {
                            unread.Folders.put(folder, 0);
                        }
                        unread.Folders.put(folder, unread.Folders.get(folder) + c);
                    }
                }
            }
            persistFeedList();
        } catch (JSONException e) {
            Log.e(TAG, "update folder properties", e);
        }
    }

    private void persistFeedList() {
        if (listFeeds == null) {
            return;
        }
        try {
            FileWriter fw = new FileWriter(listFeedsCache);
            fw.write(listFeeds.toString());
            fw.close();
            Log.i(TAG, "write feed cache");
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}