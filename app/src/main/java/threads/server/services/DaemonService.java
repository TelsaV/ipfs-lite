package threads.server.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import threads.LogUtils;
import threads.server.MainActivity;
import threads.server.R;
import threads.server.core.peers.Content;
import threads.server.core.peers.PEERS;
import threads.server.ipfs.IPFS;
import threads.server.work.SwarmConnectWorker;

public class DaemonService extends Service {

    private static final String TAG = DaemonService.class.getSimpleName();
    private final Gson gson = new Gson();

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                SwarmConnectWorker.connect(getApplicationContext());
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        }
    };


    public static void start(@NonNull Context context) {

        try {
            Intent intent = new Intent(context, DaemonService.class);
            intent.putExtra(Content.REFRESH, true);
            ContextCompat.startForegroundService(context, intent);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    private void createChannel(@NonNull Context context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                CharSequence name = context.getString(R.string.daemon_channel_name);
                String description = context.getString(R.string.daemon_channel_description);
                NotificationChannel mChannel = new NotificationChannel(TAG, name,
                        NotificationManager.IMPORTANCE_LOW);

                mChannel.setDescription(description);

                NotificationManager notificationManager = (NotificationManager) context.getSystemService(
                        Context.NOTIFICATION_SERVICE);
                if (notificationManager != null) {
                    notificationManager.createNotificationChannel(mChannel);
                }

            } catch (Throwable e) {
                LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
            }
        }
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        try {

            if (intent.getBooleanExtra(Content.REFRESH, false)) {

                IPFS ipfs = IPFS.getInstance(getApplicationContext());


                ipfs.setPusher((text, pid) -> {
                    try {
                        onMessageReceived(text, pid);
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                });

                NotificationCompat.Builder builder = new NotificationCompat.Builder(
                        getApplicationContext(), TAG);

                Intent notifyIntent = new Intent(getApplicationContext(), MainActivity.class);
                int viewID = (int) System.currentTimeMillis();
                PendingIntent viewIntent = PendingIntent.getActivity(getApplicationContext(),
                        viewID, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT);


                Intent stopIntent = new Intent(getApplicationContext(), DaemonService.class);
                stopIntent.putExtra(Content.REFRESH, false);
                int requestID = (int) System.currentTimeMillis();
                PendingIntent stopPendingIntent = PendingIntent.getService(
                        getApplicationContext(), requestID, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                String cancel = getApplicationContext().getString(android.R.string.cancel);
                NotificationCompat.Action action = new NotificationCompat.Action.Builder(
                        R.drawable.pause, cancel,
                        stopPendingIntent).build();
                builder.setSmallIcon(R.drawable.access_point_network);
                builder.addAction(action);
                builder.setUsesChronometer(true);
                builder.setOnlyAlertOnce(true);
                builder.setContentText(getString(R.string.service_is_running));
                builder.setContentIntent(viewIntent);
                builder.setSubText(getApplicationContext().getString(
                        R.string.port) + " " + ipfs.getSwarmPort());
                builder.setColor(ContextCompat.getColor(getApplicationContext(), R.color.colorAccent));
                builder.setCategory(Notification.CATEGORY_SERVICE);


                Notification notification = builder.build();
                startForeground(TAG.hashCode(), notification);
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
                registerReceiver(broadcastReceiver, intentFilter);

            } else {
                try {
                    stopForeground(true);
                    unregisterReceiver(broadcastReceiver);
                } finally {
                    stopSelf();
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

        return START_NOT_STICKY;
    }


    public void onMessageReceived(@NonNull String remoteMessage, @NonNull String pid) {

        try {
            Type hashMap = new TypeToken<HashMap<String, String>>() {
            }.getType();

            Objects.requireNonNull(pid);
            Map<String, String> data = gson.fromJson(remoteMessage, hashMap);

            LogUtils.error(TAG, "Push Message : " + data.toString());


            String ipns = data.get(Content.IPNS);
            Objects.requireNonNull(ipns);
            String seq = data.get(Content.SEQ);
            Objects.requireNonNull(seq);

            long sequence = Long.parseLong(seq);
            if (sequence >= 0) {
                IPFS ipfs = IPFS.getInstance(getApplicationContext());
                if (ipfs.isValidCID(ipns)) {

                    PEERS peers = PEERS.getInstance(getApplicationContext());
                    peers.setUserIpns(pid, ipns, sequence);
                }
            }


        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    @Override
    public void onCreate() {
        try {
            createChannel(getApplicationContext());
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        super.onCreate();
    }

}
