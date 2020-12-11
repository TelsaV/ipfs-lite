package threads.server.work;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ForegroundInfo;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import threads.LogUtils;
import threads.server.InitApplication;
import threads.server.MainActivity;
import threads.server.R;
import threads.server.core.peers.Content;
import threads.server.core.threads.THREADS;
import threads.server.core.threads.Thread;
import threads.server.ipfs.CID;
import threads.server.ipfs.IPFS;
import threads.server.ipfs.Progress;

public class UploadDirectoryWorker extends Worker {
    private static final String WID = "UDW";
    private static final String TAG = UploadDirectoryWorker.class.getSimpleName();
    private final NotificationManager mNotificationManager;
    private final AtomicReference<Notification> mLastNotification = new AtomicReference<>(null);
    private int mNote;

    @SuppressWarnings("WeakerAccess")
    public UploadDirectoryWorker(@NonNull Context context,
                                 @NonNull WorkerParameters params) {
        super(context, params);
        mNotificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
    }


    private static OneTimeWorkRequest getWork(@NonNull Uri uri, long idx) {

        Data.Builder data = new Data.Builder();
        data.putLong(Content.IDX, idx);
        data.putString(Content.URI, uri.toString());

        return new OneTimeWorkRequest.Builder(UploadDirectoryWorker.class)
                .addTag(TAG)
                .setInputData(data.build())
                .setInitialDelay(1, TimeUnit.MILLISECONDS)
                .build();
    }

    public static void load(@NonNull Context context, @NonNull Uri uri, long idx) {
        WorkManager.getInstance(context).enqueueUniqueWork(
                WID + idx, ExistingWorkPolicy.KEEP, getWork(uri, idx));

    }

    private void closeNotification() {
        if (mNotificationManager != null) {
            mNotificationManager.cancel(mNote);
        }
    }

    private void reportProgress(@NonNull String title, int percent) {

        Notification notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = createNotification(title, percent);
        } else {
            notification = createCompatNotification(title, percent);
        }

        if (mNotificationManager != null) {
            mNotificationManager.notify(mNote, notification);
        }

    }

    @Override
    public void onStopped() {
        super.onStopped();
        closeNotification();
    }

    private Notification createCompatNotification(@NonNull String title, int progress) {

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                getApplicationContext(), InitApplication.CHANNEL_ID);


        PendingIntent intent = WorkManager.getInstance(getApplicationContext())
                .createCancelPendingIntent(getId());
        String cancel = getApplicationContext().getString(android.R.string.cancel);

        Intent main = new Intent(getApplicationContext(), MainActivity.class);

        int requestID = (int) System.currentTimeMillis();
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), requestID,
                main, PendingIntent.FLAG_UPDATE_CURRENT);


        NotificationCompat.Action action = new NotificationCompat.Action.Builder(
                R.drawable.pause, cancel, intent).build();


        builder.setContentTitle(title)
                .setSubText("" + progress + "%")
                .setContentIntent(pendingIntent)
                .setProgress(100, progress, false)
                .setOnlyAlertOnce(true)
                .setSmallIcon(R.drawable.download)
                .addAction(action)
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.colorAccent))
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setOngoing(true)
                .setUsesChronometer(true);

        return builder.build();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private Notification createNotification(@NonNull String title, int progress) {

        Notification.Builder builder;
        if (mLastNotification.get() != null) {
            builder = Notification.Builder.recoverBuilder(
                    getApplicationContext(), mLastNotification.get());
            builder.setProgress(100, progress, false);
            builder.setContentTitle(title);
            builder.setSubText("" + progress + "%");
            return builder.build();
        } else {
            builder = new Notification.Builder(getApplicationContext(), InitApplication.CHANNEL_ID);
        }

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


        builder.setContentTitle(title)
                .setSubText("" + progress + "%")
                .setContentIntent(pendingIntent)
                .setProgress(100, progress, false)
                .setOnlyAlertOnce(true)
                .setSmallIcon(R.drawable.download)
                .addAction(action)
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.colorAccent))
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setOngoing(true)
                .setUsesChronometer(true);

        return builder.build();
    }

    @NonNull
    @Override
    public Result doWork() {

        long idx = getInputData().getLong(Content.IDX, -1);
        String uri = getInputData().getString(Content.URI);
        Objects.requireNonNull(uri);

        mNote = (int) idx;
        long start = System.currentTimeMillis();
        LogUtils.info(TAG, " start ... " + idx);

        try {
            THREADS threads = THREADS.getInstance(getApplicationContext());

            Thread thread = threads.getThreadByIdx(idx);
            Objects.requireNonNull(thread);

            DocumentFile rootDocFile = DocumentFile.fromTreeUri(getApplicationContext(),
                    Uri.parse(uri));
            Objects.requireNonNull(rootDocFile);

            DocumentFile docFile = rootDocFile.createDirectory(thread.getName());
            Objects.requireNonNull(docFile);

            ForegroundInfo foregroundInfo = createForegroundInfo(thread.getName());
            setForegroundAsync(foregroundInfo);

            copyThreads(thread, docFile);

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            return Result.failure();
        } finally {
            closeNotification();
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }

        return Result.success();

    }

    @NonNull
    private ForegroundInfo createForegroundInfo(@NonNull String title) {
        Notification notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = createNotification(title, 0);
        } else {
            notification = createCompatNotification(title, 0);
        }
        mLastNotification.set(notification);
        return new ForegroundInfo(mNote, notification);
    }

    private void copyThreads(@NonNull Thread thread, @NonNull DocumentFile file) {
        THREADS threads = THREADS.getInstance(getApplicationContext());
        IPFS ipfs = IPFS.getInstance(getApplicationContext());
        List<Thread> children = threads.getChildren(ipfs.getLocation(), thread.getIdx());
        for (Thread child : children) {
            if (!isStopped()) {
                if (!child.isDeleting() && child.isSeeding()) {
                    if (child.isDir()) {
                        DocumentFile docFile = file.createDirectory(child.getName());
                        Objects.requireNonNull(docFile);
                        copyThreads(child, docFile);
                    } else {
                        DocumentFile childFile = file.createFile(
                                child.getMimeType(), child.getName());
                        Objects.requireNonNull(childFile);
                        if (!childFile.canWrite()) {
                            // throw message
                            LogUtils.error(TAG, "can not write");
                        } else {
                            copyThread(child, childFile);
                        }
                    }
                }
            }
        }
    }

    private void copyThread(@NonNull Thread thread, @NonNull DocumentFile file) {

        if (isStopped()) {
            return;
        }

        IPFS ipfs = IPFS.getInstance(getApplicationContext());

        CID cid = thread.getContent();
        Objects.requireNonNull(cid);

        long size = thread.getSize();


        try {
            AtomicLong refresh = new AtomicLong(System.currentTimeMillis());
            try (OutputStream os = getApplicationContext().getContentResolver().
                    openOutputStream(file.getUri())) {
                Objects.requireNonNull(os);
                ipfs.storeToOutputStream(os, new Progress() {


                    @Override
                    public void setProgress(int percent) {
                        reportProgress(thread.getName(), percent);
                    }

                    @Override
                    public boolean doProgress() {
                        return !isStopped();
                    }

                    @Override
                    public boolean isClosed() {

                        long time = System.currentTimeMillis();
                        long diff = time - refresh.get();
                        boolean doProgress = (diff > InitApplication.REFRESH);
                        if (doProgress) {
                            refresh.set(time);
                        }
                        return !isStopped() && doProgress;
                    }


                }, cid, size);
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }

            if (isStopped()) {
                if (file.exists()) {
                    file.delete();
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }
}
