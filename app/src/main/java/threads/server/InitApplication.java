package threads.server;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import threads.LogUtils;
import threads.server.core.Content;
import threads.server.core.pages.PAGES;
import threads.server.core.peers.PEERS;
import threads.server.core.threads.SortOrder;
import threads.server.ipfs.IPFS;
import threads.server.ipfs.TimeoutProgress;
import threads.server.services.LiteService;
import threads.server.work.InitApplicationWorker;

public class InitApplication extends Application {
    public static final String CHANNEL_ID = "CHANNEL_ID";
    public static final long REFRESH = 250;    //  250ms
    public static final boolean SUPPORT_VIDEO_UPDATE_THUMBNAIL = false;
    public static final String DOWNLOADS = "content://com.android.externalstorage.documents/document/primary:Download";
    private static final String APP_KEY = "AppKey";
    private static final String UPDATE = "UPDATE";
    private static final String TAG = InitApplication.class.getSimpleName();
    private static final String PREF_KEY = "prefKey";
    private static final String TIMEOUT_KEY = "timeoutKey";
    private static final String AUTO_DISCOVERY_KEY = "autoDiscoveryKey";
    private static final String REDIRECT_URL_KEY = "redirectUrlKey";
    private static final String REDIRECT_INDEX_KEY = "redirectIndexKey";
    private static final String ENABLE_PUBLISHER_KEY = "enablePublisherKey";
    private static final String SORT_KEY = "sortKey";
    private final Gson gson = new Gson();


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

    private static void createChannel(@NonNull Context context) {

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


        if (LogUtils.isDebug()) {
            IPFS.logCacheDir(getApplicationContext());
            IPFS.logBaseDir(getApplicationContext());
        }


        createChannel(getApplicationContext());


        try {
            IPFS ipfs = IPFS.getInstance(getApplicationContext());
            LogUtils.error(TAG, "startDaemon...");
            ipfs.startDaemon(IPFS.isPrivateSharingEnabled(getApplicationContext()));
            ipfs.setPusher((pid, cid) -> {
                try {
                    onMessageReceived(pid, cid);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
            });
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        InitApplicationWorker.initialize(getApplicationContext());

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
                    PAGES pages = PAGES.getInstance(getApplicationContext());
                    pages.setPageSequence(pid, sequence);
                    pages.setPageContent(pid, ipns);
                }
            }


        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }
}
