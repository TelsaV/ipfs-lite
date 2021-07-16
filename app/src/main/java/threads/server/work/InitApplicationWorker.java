package threads.server.work;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;
import java.util.Objects;

import threads.lite.LogUtils;
import threads.server.core.events.EVENTS;
import threads.server.core.threads.THREADS;
import threads.server.provider.FileDocumentsProvider;

public class InitApplicationWorker extends Worker {

    private static final String TAG = InitApplicationWorker.class.getSimpleName();

    @SuppressWarnings("WeakerAccess")
    public InitApplicationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    private static OneTimeWorkRequest getWork() {


        return new OneTimeWorkRequest.Builder(InitApplicationWorker.class)
                .addTag(TAG)
                .build();

    }


    public static void initialize(@NonNull Context context) {
        WorkManager.getInstance(context).enqueue(InitApplicationWorker.getWork());
    }


    @NonNull
    @Override
    public Result doWork() {

        long start = System.currentTimeMillis();

        LogUtils.info(TAG, " start ...");

        try {

            THREADS threads = THREADS.getInstance(getApplicationContext());

            List<Long> idxs = threads.getDeletedThreads();
            for (long idx : idxs) {
                Uri uri = FileDocumentsProvider.getUriForThread(idx);
                DocumentFile document = DocumentFile.fromSingleUri(
                        getApplicationContext(), uri);
                Objects.requireNonNull(document);
                document.delete();
            }

        } catch (Throwable e) {
            LogUtils.error(TAG, e);

        } finally {
            PageWorker.publish(getApplicationContext());
            EVENTS.getInstance(getApplicationContext()).refresh();
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }
        return Result.success();
    }

}

