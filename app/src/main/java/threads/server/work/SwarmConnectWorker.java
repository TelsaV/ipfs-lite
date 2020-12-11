package threads.server.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import threads.LogUtils;
import threads.server.core.peers.Content;
import threads.server.services.ConnectService;
import threads.server.services.LiteService;

public class SwarmConnectWorker extends Worker {


    private static final String TAG = SwarmConnectWorker.class.getSimpleName();

    @SuppressWarnings("WeakerAccess")
    public SwarmConnectWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }


    private static OneTimeWorkRequest getWork(boolean refresh) {

        Constraints.Builder builder = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED);
        Data.Builder data = new Data.Builder();
        data.putBoolean(Content.REFRESH, refresh);

        return new OneTimeWorkRequest.Builder(SwarmConnectWorker.class)
                .addTag(TAG)
                .setConstraints(builder.build())
                .setInputData(data.build())
                .build();

    }

    public static void connect(@NonNull Context context, boolean refresh) {
        WorkManager.getInstance(context).enqueueUniqueWork(
                TAG, ExistingWorkPolicy.KEEP, getWork(refresh));

    }

    @NonNull
    @Override
    public Result doWork() {

        long start = System.currentTimeMillis();
        boolean refresh = getInputData().getBoolean(Content.REFRESH, false);
        LogUtils.info(TAG, " start ...");
        try {

            LiteService.bootstrap(getApplicationContext(), 10, refresh);


            ConnectService.connect(getApplicationContext());

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }

        return Result.success();
    }
}

