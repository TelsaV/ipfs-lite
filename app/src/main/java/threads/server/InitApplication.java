package threads.server;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import threads.LogUtils;
import threads.server.core.Content;
import threads.server.core.peers.PEERS;
import threads.server.core.threads.SortOrder;
import threads.server.ipfs.IPFS;
import threads.server.ipfs.TimeoutProgress;
import threads.server.services.LiteService;
import threads.server.work.CleanupWorker;
import threads.server.work.PageWorker;

public class InitApplication extends Application {
    public static final String CHANNEL_ID = "CHANNEL_ID";
    public static final long REFRESH = 250;    //  250ms
    public static final boolean SUPPORT_VIDEO_UPDATE_THUMBNAIL = false;
    private static final String APP_KEY = "AppKey";
    private static final String UPDATE = "UPDATE";
    private static final String TAG = InitApplication.class.getSimpleName();
    private static final String PREF_KEY = "prefKey";
    private static final String TIMEOUT_KEY = "timeoutKey";
    private static final String AUTO_DISCOVERY_KEY = "autoDiscoveryKey";
    private static final String SORT_KEY = "sortKey";

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
        return SortOrder.toSort(sharedPref.getInt(SORT_KEY, 0));
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


    private static void createChannel(@NonNull Context context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                CharSequence name = context.getString(R.string.channel_name);
                String description = context.getString(R.string.channel_description);
                NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name,
                        NotificationManager.IMPORTANCE_LOW);
                mChannel.setDescription(description);

                NotificationManager notificationManager = (NotificationManager) context.getSystemService(
                        Context.NOTIFICATION_SERVICE);
                if (notificationManager != null) {
                    notificationManager.createNotificationChannel(mChannel);
                }

            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        }
    }

    public static void runUpdatesIfNecessary(@NonNull Context context) {
        try {
            int versionCode = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0).versionCode;
            SharedPreferences prefs = context.getSharedPreferences(
                    APP_KEY, Context.MODE_PRIVATE);
            if (prefs.getInt(UPDATE, 0) != versionCode) {


                IPFS.setLowWater(context, 50);
                IPFS.setHighWater(context, 500);
                IPFS.setGracePeriod(context, "10s");


                InitApplication.setConnectionTimeout(context, 60);
                LiteService.setPublisherServiceTime(context, 6);

                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(UPDATE, versionCode);
                editor.apply();
            }

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }


    @SuppressLint("SetJavaScriptEnabled")
    public static void setWebSettings(@NonNull WebView webView) {


        WebSettings settings = webView.getSettings();
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + ")");

        settings.setJavaScriptEnabled(true);
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


    public static void syncData(@NonNull Context context) {
        IPFS ipfs = IPFS.getInstance(context);
        PEERS peers = PEERS.getInstance(context);
        ipfs.swarmEnhance(peers.getSwarm());
    }

    private final Gson gson = new Gson();

    @Override
    public void onCreate() {
        super.onCreate();


        runUpdatesIfNecessary(getApplicationContext());
        syncData(getApplicationContext());


        // periodic jobs
        PageWorker.publish(getApplicationContext(), false);
        CleanupWorker.cleanup(getApplicationContext());


        if (LogUtils.isDebug()) {
            IPFS.logCacheDir(getApplicationContext());
            IPFS.logBaseDir(getApplicationContext());
        }


        createChannel(getApplicationContext());


        IPFS ipfs = IPFS.getInstance(getApplicationContext());


        ipfs.setPusher((pid, cid) -> {
            try {
                onMessageReceived(pid, cid);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });
    }

    public void onMessageReceived(@NonNull String pid, @NonNull String cid) {

        try {
            Type hashMap = new TypeToken<HashMap<String, String>>() {
            }.getType();

            Objects.requireNonNull(pid);
            IPFS ipfs = IPFS.getInstance(getApplicationContext());
            byte[] content = ipfs.loadData(cid, new TimeoutProgress(5));
            Objects.requireNonNull(content);
            Map<String, String> data = gson.fromJson(new String(content), hashMap);

            LogUtils.error(TAG, "Push Message : " + data.toString());


            String ipns = data.get(Content.IPNS);
            Objects.requireNonNull(ipns);
            String seq = data.get(Content.SEQ);
            Objects.requireNonNull(seq);

            long sequence = Long.parseLong(seq);
            if (sequence >= 0) {
                if (ipfs.isValidCID(ipns)) {

                    PEERS peers = PEERS.getInstance(getApplicationContext());
                    peers.setUserIpns(pid, ipns, sequence);
                }
            }


        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

}
