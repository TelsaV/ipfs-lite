package threads.server;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import threads.server.core.threads.SortOrder;

public class Settings {

    public static final long REFRESH = 250;    //  250ms
    public static final boolean SUPPORT_VIDEO_UPDATE_THUMBNAIL = false;
    public static final String DOWNLOADS = "content://com.android.externalstorage.documents/document/primary:Download";

    public static final int BITMAP_NAME_SIZE = 128;

    public static final String CHANNEL_ID = "CHANNEL_ID";

    public static final String BLOCKS = "/blocks";
    private static final String APP_KEY = "AppKey";
    private static final String PREF_KEY = "prefKey";
    private static final String TIMEOUT_KEY = "timeoutKey";
    private static final String AUTO_DISCOVERY_KEY = "autoDiscoveryKey";
    private static final String REDIRECT_URL_KEY = "redirectUrlKey";
    private static final String REDIRECT_INDEX_KEY = "redirectIndexKey";
    private static final String ENABLE_PUBLISHER_KEY = "enablePublisherKey";
    private static final String SORT_KEY = "sortKey";
    private static final String SEQUENCE = "sequence";

    public static int getSequence(@NonNull Context context) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        return sharedPref.getInt(SEQUENCE, 1000);
    }

    public static void setSequence(@NonNull Context context, int sequence) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(SEQUENCE, sequence);
        editor.apply();
    }


    public static void setPublisherEnabled(Context context, boolean enable) {
        SharedPreferences sharedPref = context.getSharedPreferences(APP_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(ENABLE_PUBLISHER_KEY, enable);
        editor.apply();
    }

    public static boolean isPublisherEnabled(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(APP_KEY, Context.MODE_PRIVATE);
        return sharedPref.getBoolean(ENABLE_PUBLISHER_KEY, true);
    }


    public static void setRedirectUrlEnabled(Context context, boolean enable) {
        SharedPreferences sharedPref = context.getSharedPreferences(APP_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(REDIRECT_URL_KEY, enable);
        editor.apply();
    }

    public static boolean isRedirectUrlEnabled(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(APP_KEY, Context.MODE_PRIVATE);
        return sharedPref.getBoolean(REDIRECT_URL_KEY, false);
    }

    public static void setRedirectIndexEnabled(Context context, boolean auto) {
        SharedPreferences sharedPref = context.getSharedPreferences(APP_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(REDIRECT_INDEX_KEY, auto);
        editor.apply();
    }

    public static boolean isRedirectIndexEnabled(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(APP_KEY, Context.MODE_PRIVATE);
        return sharedPref.getBoolean(REDIRECT_INDEX_KEY, true);

    }

    public static void setAutoDiscovery(Context context, boolean auto) {
        SharedPreferences sharedPref = context.getSharedPreferences(APP_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(AUTO_DISCOVERY_KEY, auto);
        editor.apply();
    }

    public static boolean isAutoDiscovery(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(APP_KEY, Context.MODE_PRIVATE);
        return sharedPref.getBoolean(AUTO_DISCOVERY_KEY, true);

    }

    @NonNull
    public static SortOrder getSortOrder(@NonNull Context context) {

        SharedPreferences sharedPref = context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
        return SortOrder.toSort(sharedPref.getInt(SORT_KEY, SortOrder.DATE.ordinal()));
    }

    public static void setSortOrder(@NonNull Context context, @NonNull SortOrder sort) {

        SharedPreferences sharedPref = context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(SORT_KEY, SortOrder.toInteger(sort));
        editor.apply();
    }

    public static int getConnectionTimeout(@NonNull Context context) {

        SharedPreferences sharedPref = context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
        return sharedPref.getInt(TIMEOUT_KEY, 25);
    }

    public static void setConnectionTimeout(@NonNull Context context, int timeout) {

        SharedPreferences sharedPref = context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(TIMEOUT_KEY, timeout);
        editor.apply();
    }

    @NonNull
    public static String getDefaultSearchEngine(@NonNull String query) {
        return "https://ipfs-search.com/#/search?search=" + query;
    }
    private static final String JAVASCRIPT_KEY = "javascriptKey";
    public static void setJavascriptEnabled(Context context, boolean auto) {
        SharedPreferences sharedPref = context.getSharedPreferences(APP_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(JAVASCRIPT_KEY, auto);
        editor.apply();
    }

    public static boolean isJavascriptEnabled(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(APP_KEY, Context.MODE_PRIVATE);
        return sharedPref.getBoolean(JAVASCRIPT_KEY, true);

    }

    @SuppressLint("SetJavaScriptEnabled")
    public static void setWebSettings(@NonNull WebView webView, boolean enableJavascript) {


        WebSettings settings = webView.getSettings();
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + ")");


        settings.setJavaScriptEnabled(enableJavascript);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);


        settings.setSafeBrowsingEnabled(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowContentAccess(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setAllowFileAccess(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkLoads(false);
        settings.setBlockNetworkImage(false);
        settings.setDomStorageEnabled(true);
        settings.setAppCacheEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setDatabaseEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportMultipleWindows(false);
        settings.setGeolocationEnabled(false);
    }
}
