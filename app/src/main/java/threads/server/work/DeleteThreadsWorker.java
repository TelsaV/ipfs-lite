package threads.server.work;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.ForegroundInfo;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;
import java.util.Objects;

import threads.LogUtils;
import threads.server.InitApplication;
import threads.server.MainActivity;
import threads.server.R;
import threads.server.core.events.EVENTS;
import threads.server.core.threads.THREADS;
import threads.server.provider.FileDocumentsProvider;

public class DeleteThreadsWorker extends Worker {

    private static final String TAG = DeleteThreadsWorker.class.getSimpleName();
    private final NotificationManager mNotificationManager;
    @SuppressWarnings("WeakerAccess")
    public DeleteThreadsWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        mNotificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel(context);
    }

    private static OneTimeWorkRequest getWork() {


        return new OneTimeWorkRequest.Builder(DeleteThreadsWorker.class)
                .addTag(TAG)
                .build();

    }


    public static void cleanup(@NonNull Context context) {
        WorkManager.getInstance(context).enqueue(DeleteThreadsWorker.getWork());
    }


    @NonNull
    private ForegroundInfo createForegroundInfo() {
        Notification notification = createNotification();
        return new ForegroundInfo(getId().hashCode(), notification);
    }


    @Override
    public void onStopped() {
        closeNotification();
    }

    private void closeNotification() {
        if (mNotificationManager != null) {
            mNotificationManager.cancel(getId().hashCode());
        }
    }

    private void createChannel(@NonNull Context context) {

        try {
            CharSequence name = context.getString(R.string.channel_name);
            String description = context.getString(R.string.channel_description);
            NotificationChannel mChannel = new NotificationChannel(InitApplication.CHANNEL_ID, name,
                    NotificationManager.IMPORTANCE_HIGH);
            mChannel.setDescription(description);

            if (mNotificationManager != null) {
                mNotificationManager.createNotificationChannel(mChannel);
            }

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

    }

    private Notification createNotification() {

        Notification.Builder builder= new Notification.Builder(getApplicationContext(),
                InitApplication.CHANNEL_ID);


        PendingIntent intent = WorkManager.getInstance(getApplicationContext())
                .createCancelPendingIntent(getId());
        String cancel = getApplicationContext().getString(android.R.string.cancel);

        Intent main = new Intent(getApplicationContext(), MainActivity.class);

        int requestID = (int) System.currentTimeMillis();
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), requestID,
                main, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Action action = new Notification.Action.Builder(
                Icon.createWithResource(getApplicationContext(), R.drawable.pause), cancel,
                intent).build();

        builder.setContentTitle(getApplicationContext().getString(R.string.cleanup_caches))
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setSmallIcon(R.drawable.refresh)
                .addAction(action)
                .setColor(ContextCompat.getColor(getApplicationContext(), android.R.color.black))
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setUsesChronometer(true);

        return builder.build();
    }

    @NonNull
    @Override
    public Result doWork() {

        long start = System.currentTimeMillis();

        LogUtils.info(TAG, " start ...");

        try {

            ForegroundInfo foregroundInfo = createForegroundInfo();
            setForegroundAsync(foregroundInfo);

            THREADS threads = THREADS.getInstance(getApplicationContext());

            List<Long> idxs = threads.getDeletedThreads();
            for (long idx : idxs) {
                Uri uri = FileDocumentsProvider.getUriForThread(idx);
                DocumentFile document = DocumentFile.fromSingleUri(
                        getApplicationContext(), uri);
                Objects.requireNonNull(document);
                document.delete();
            }

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);

        } finally {
            PageWorker.publish(getApplicationContext());
            EVENTS.getInstance(getApplicationContext()).refresh();
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }
        return Result.success();
    }

}

