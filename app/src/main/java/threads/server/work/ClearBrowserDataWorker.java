package threads.server.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

import threads.LogUtils;
import threads.server.core.DOCS;
import threads.server.core.blocks.BLOCKS;
import threads.server.core.events.EVENTS;
import threads.server.core.pages.PAGES;
import threads.server.core.threads.THREADS;
import threads.server.provider.FileProvider;

public class ClearBrowserDataWorker extends Worker {

    private static final String TAG = ClearBrowserDataWorker.class.getSimpleName();

    @SuppressWarnings("WeakerAccess")
    public ClearBrowserDataWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    private static OneTimeWorkRequest getWork() {

        return new OneTimeWorkRequest.Builder(ClearBrowserDataWorker.class)
                .setInitialDelay(1, TimeUnit.MILLISECONDS)
                .build();

    }

    public static void clearCache(@NonNull Context context) {

        WorkManager.getInstance(context).enqueueUniqueWork(
                TAG, ExistingWorkPolicy.REPLACE, getWork());

    }


    @NonNull
    @Override
    public Result doWork() {

        long start = System.currentTimeMillis();

        LogUtils.info(TAG, " start ...");

        try {


            FileProvider fileProvider = FileProvider.getInstance(getApplicationContext());
            fileProvider.cleanImageDir();
            fileProvider.cleanDataDir();

            PAGES.getInstance(getApplicationContext()).clear();
            THREADS.getInstance(getApplicationContext()).clear();
            BLOCKS.getInstance(getApplicationContext()).clear();

            DOCS.getInstance(getApplicationContext()).initPinsPage();

            PageWorker.publish(getApplicationContext());

            EVENTS.getInstance(getApplicationContext()).refresh();

        } catch (Throwable e) {
            LogUtils.error(TAG, e);

        } finally {
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }
        return Result.success();
    }

}

