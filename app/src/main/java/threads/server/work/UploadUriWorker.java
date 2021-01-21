package threads.server.work;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import threads.LogUtils;
import threads.server.R;
import threads.server.core.DOCS;
import threads.server.core.events.EVENTS;
import threads.server.core.Content;
import threads.server.provider.FileDocumentsProvider;

public class UploadUriWorker extends Worker {

    private static final String TAG = UploadUriWorker.class.getSimpleName();

    @SuppressWarnings("WeakerAccess")
    public UploadUriWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);

    }

    public static OneTimeWorkRequest getWork(@NonNull Uri uri, long delay) {

        Data.Builder data = new Data.Builder();
        data.putString(Content.URI, uri.toString());

        return new OneTimeWorkRequest.Builder(UploadUriWorker.class)
                .addTag(UploadUriWorker.TAG)
                .setInputData(data.build())
                .setInitialDelay(delay + 1, TimeUnit.MILLISECONDS)
                .build();
    }


    @NonNull
    @Override
    public Result doWork() {
        String uri = getInputData().getString(Content.URI);
        Objects.requireNonNull(uri);
        long start = System.currentTimeMillis();
        LogUtils.info(TAG, " start ... " + uri);

        try {

            DOCS docs = DOCS.getInstance(getApplicationContext());

            Uri url = Uri.parse(uri);
            String name = FileDocumentsProvider.getFileName(getApplicationContext(), url);
            long size = FileDocumentsProvider.getFileSize(getApplicationContext(), url);
            String mimeType = FileDocumentsProvider.getMimeType(getApplicationContext(), url);

            long idx = docs.createDocument(0L, mimeType, null, url,
                    name, size, false, true);

            Data.Builder data = new Data.Builder();
            data.putLong(Content.IDX, idx);
            return Result.success(data.build());

        } catch (Throwable e) {
            EVENTS.getInstance(getApplicationContext()).error(
                    getApplicationContext().getString(R.string.file_not_found));
            return Result.failure();
        } finally {
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }
    }
}
