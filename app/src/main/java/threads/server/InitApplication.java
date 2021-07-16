package threads.server;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.PeerId;
import threads.server.core.Content;
import threads.server.core.pages.PAGES;
import threads.server.core.peers.PEERS;
import threads.server.work.InitApplicationWorker;

public class InitApplication extends Application {
    private static final String TAG = InitApplication.class.getSimpleName();

    private final Gson gson = new Gson();

    private static void createChannel(@NonNull Context context) {

        try {
            CharSequence name = context.getString(R.string.channel_name);
            String description = context.getString(R.string.channel_description);
            NotificationChannel mChannel = new NotificationChannel(
                    Settings.CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW);
            mChannel.setDescription(description);

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(
                    Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(mChannel);
            }

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }


    public static void syncData(@NonNull Context context) {
        IPFS ipfs = IPFS.getInstance(context);
        PEERS peers = PEERS.getInstance(context);
        List<String> swarm = peers.getSwarm();
        for (String pid:swarm) {
            ipfs.swarmEnhance(PeerId.fromBase58(pid));
        }

    }

    @Override
    public void onCreate() {
        super.onCreate();

        syncData(getApplicationContext());


        createChannel(getApplicationContext());


        try {
            IPFS ipfs = IPFS.getInstance(getApplicationContext());
            LogUtils.error(TAG, "startDaemon...");
            ipfs.daemon();
            ipfs.setPusher((pid, content) -> {
                try {
                    onMessageReceived(pid, content);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
            });
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        InitApplicationWorker.initialize(getApplicationContext());

    }

    public void onMessageReceived(@NonNull PeerId pid, @NonNull String content) {

        try {
            Type hashMap = new TypeToken<HashMap<String, String>>() {
            }.getType();

            Objects.requireNonNull(pid);
            IPFS ipfs = IPFS.getInstance(getApplicationContext());

            Objects.requireNonNull(content);
            Map<String, String> data = gson.fromJson(content, hashMap);

            LogUtils.error(TAG, "Push Message : " + data.toString());


            String ipns = data.get(Content.IPNS);
            Objects.requireNonNull(ipns);
            String seq = data.get(Content.SEQ);
            Objects.requireNonNull(seq);

            long sequence = Long.parseLong(seq);
            if (sequence >= 0) {
                if (ipfs.isValidCID(ipns)) {
                    PAGES pages = PAGES.getInstance(getApplicationContext());
                    pages.setPageSequence(pid.toBase58(), sequence);
                    pages.setPageContent(pid.toBase58(), ipns);
                }
            }


        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }
}
