package threads.server.work;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import threads.LogUtils;
import threads.server.core.Content;
import threads.server.core.threads.THREADS;
import threads.server.core.threads.Thread;
import threads.server.ipfs.IPFS;
import threads.server.provider.FileProvider;
import threads.server.provider.MediaDataSource;

public class VideoImageWorker extends Worker {
    private static final String WID = "VIW";
    private static final String TAG = VideoImageWorker.class.getSimpleName();

    @SuppressWarnings("WeakerAccess")
    public VideoImageWorker(@NonNull Context context,
                            @NonNull WorkerParameters params) {
        super(context, params);
    }


    private static OneTimeWorkRequest getWork(long idx, long pos) {

        Data.Builder data = new Data.Builder();
        data.putLong(Content.IDX, idx);
        data.putLong(Content.POS, pos);

        return new OneTimeWorkRequest.Builder(VideoImageWorker.class)
                .addTag(TAG)
                .setInputData(data.build())
                .setInitialDelay(1, TimeUnit.MILLISECONDS)
                .build();
    }

    public static void load(@NonNull Context context, long idx, long pos) {
        WorkManager.getInstance(context).enqueueUniqueWork(
                WID + idx, ExistingWorkPolicy.REPLACE, getWork(idx, pos));

    }

    @NonNull
    @Override
    public Result doWork() {

        long idx = getInputData().getLong(Content.IDX, -1);
        long pos = getInputData().getLong(Content.POS, -1);
        long start = System.currentTimeMillis();
        LogUtils.info(TAG, " start ... " + idx);

        try {

            THREADS threads = THREADS.getInstance(getApplicationContext());


            Thread thread = threads.getThreadByIdx(idx);
            Objects.requireNonNull(thread);
            String cid = thread.getContent();
            Objects.requireNonNull(cid);

            Bitmap bitmap = MediaDataSource.getVideoFrame(getApplicationContext(),
                    cid, pos);
            FileProvider fileProvider =
                    FileProvider.getInstance(getApplicationContext());
            File file = fileProvider.getDataFile(idx);
            IPFS.copy(new ByteArrayInputStream(getBytes(bitmap)), new FileOutputStream(file));
            Uri uri = FileProvider.getDataUri(getApplicationContext(), idx);
            Objects.requireNonNull(uri);
            threads.setThreadUri(idx, uri.toString());

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }

        return Result.success();

    }


    public byte[] getBytes(@NonNull Bitmap bitmap) {
        Bitmap copy = bitmap.copy(bitmap.getConfig(), true);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        copy.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        copy.recycle();
        return byteArray;
    }
}
