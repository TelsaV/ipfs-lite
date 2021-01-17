package threads.server.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import threads.LogUtils;
import threads.server.services.ConnectService;
import threads.server.services.LiteService;

public class SwarmConnectWorker extends Worker {


    private static final String TAG = SwarmConnectWorker.class.getSimpleName();

    @SuppressWarnings("WeakerAccess")
    public SwarmConnectWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }


    private static OneTimeWorkRequest getWork() {

        Constraints.Builder builder = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED);

        return new OneTimeWorkRequest.Builder(SwarmConnectWorker.class)
                .addTag(TAG)
                .setConstraints(builder.build())
                .build();

    }

    public static void connect(@NonNull Context context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
                TAG, ExistingWorkPolicy.KEEP, getWork());

    }

    @NonNull
    @Override
    public Result doWork() {

        long start = System.currentTimeMillis();

        LogUtils.info(TAG, " start ...");
        try {

            LiteService.bootstrap(getApplicationContext(), 10);


            ConnectService.connect(getApplicationContext());

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }

        return Result.success();
    }
}

