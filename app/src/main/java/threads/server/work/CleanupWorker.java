package threads.server.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;
import java.util.concurrent.TimeUnit;

import threads.LogUtils;
import threads.server.core.threads.THREADS;
import threads.server.core.threads.Thread;
import threads.server.ipfs.IPFS;

public class CleanupWorker extends Worker {

    private static final String TAG = CleanupWorker.class.getSimpleName();

    @SuppressWarnings("WeakerAccess")
    public CleanupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    private static PeriodicWorkRequest getWork() {

        Constraints.Builder builder = new Constraints.Builder()
                .setRequiresCharging(true);

        return new PeriodicWorkRequest.Builder(CleanupWorker.class, 6, TimeUnit.HOURS)
                .addTag(TAG)
                .setConstraints(builder.build())
                .setInitialDelay(60, TimeUnit.SECONDS)
                .build();

    }

    public static void cleanup(@NonNull Context context) {

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TAG, ExistingPeriodicWorkPolicy.KEEP, getWork());

    }


    @NonNull
    @Override
    public Result doWork() {

        long start = System.currentTimeMillis();

        LogUtils.info(TAG, " start ...");

        try {

            THREADS threads = THREADS.getInstance(getApplicationContext());

            IPFS ipfs = IPFS.getInstance(getApplicationContext());
            try {
                // just in case (normally should not occur)
                List<Thread> list = threads.getDeletedThreads(ipfs.getLocation());
                threads.removeThreads(list);
                for (Thread thread : list) {
                    removeThread(ipfs, threads, thread);
                }

            } finally {
                ipfs.gc();
            }


            return Result.success();

        } catch (Throwable e) {
            LogUtils.error(TAG, e);

            return Result.failure();

        } finally {
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }
    }


    private void removeThread(@NonNull IPFS ipfs, @NonNull THREADS threads, @NonNull Thread thread) {

        unpin(ipfs, threads, thread.getContent(), !thread.isDir());

        // delete all children
        List<Thread> entries = threads.getChildren(ipfs.getLocation(), thread.getIdx());
        threads.removeThreads(entries);
        for (Thread entry : entries) {
            removeThread(ipfs, threads, entry);
        }
    }

    private void unpin(@NonNull IPFS ipfs, @NonNull THREADS threads, @Nullable String cid, boolean recursively) {
        try {
            if (cid != null) {
                if (!threads.isReferenced(ipfs.getLocation(), cid)) {
                    ipfs.rm(cid, recursively);
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

}

