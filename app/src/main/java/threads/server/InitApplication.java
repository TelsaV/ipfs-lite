package threads.server;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Objects;

import threads.LogUtils;
import threads.server.core.peers.PEERS;
import threads.server.core.threads.SortOrder;
import threads.server.ipfs.IPFS;
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
    private static final String WAIT_KEY = "waitKey";

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

    public static boolean getWaitFlag(@NonNull Context context) {

        SharedPreferences sharedPref = context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
        return sharedPref.getBoolean(WAIT_KEY, true);
    }

    public static void setWaitFlag(@NonNull Context context, boolean wait) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(WAIT_KEY, wait);
        editor.apply();
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
                IPFS.setHighWater(context, 200);
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
    public static void setWebSettings(@NonNull WebView webView, @NonNull String userAgent) {

        WebSettings settings = webView.getSettings();
        settings.setUserAgentString(userAgent);
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(false);
        }
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowContentAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowFileAccess(true);
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
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportMultipleWindows(true);
        settings.setGeolocationEnabled(false);
    }

    public static void checkExternalStorageDirectory(@NonNull Context context) {

        try {
            File dir = IPFS.getExternalStorageDirectory(context);
            if (dir != null) {
                String state = Environment.getExternalStorageState(dir);
                if (!Objects.equals("mounted", state)) {
                    IPFS.setExternalStorageDirectory(context, null);
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            IPFS.setExternalStorageDirectory(context, null);
        }

    }

    public static void syncData(@NonNull Context context) {
        IPFS ipfs = IPFS.getInstance(context);
        PEERS peers = PEERS.getInstance(context);
        ipfs.swarmEnhance(peers.getSwarm());
    }

    @Override
    public void onCreate() {
        super.onCreate();


        runUpdatesIfNecessary(getApplicationContext());
        syncData(getApplicationContext());
        checkExternalStorageDirectory(getApplicationContext());

        // periodic jobs
        PageWorker.publish(getApplicationContext(), false);
        CleanupWorker.cleanup(getApplicationContext());
        InitApplication.setWaitFlag(getApplicationContext(), true);

        if (LogUtils.isDebug()) {
            IPFS.logCacheDir(getApplicationContext());
            IPFS.logBaseDir(getApplicationContext());
        }


        createChannel(getApplicationContext());


    }


}
