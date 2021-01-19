package threads.server.work;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import threads.LogUtils;
import threads.server.core.peers.Content;
import threads.server.ipfs.IPFS;
import threads.server.provider.FileDocumentsProvider;

public class DeleteThreadsWorker extends Worker {

    private static final String TAG = DeleteThreadsWorker.class.getSimpleName();

    @SuppressWarnings("WeakerAccess")
    public DeleteThreadsWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static OneTimeWorkRequest getWork(@NonNull Uri uri) {

        Data.Builder data = new Data.Builder();
        data.putString(Content.URI, uri.toString());

        return new OneTimeWorkRequest.Builder(DeleteThreadsWorker.class)
                .addTag(TAG)
                .setInputData(data.build())
                .build();

    }


    public static void delete(@NonNull Context context, @NonNull Uri uri) {
        WorkManager.getInstance(context).enqueue(DeleteThreadsWorker.getWork(uri));
    }


    @NonNull
    @Override
    public Result doWork() {

        long start = System.currentTimeMillis();

        String uriFile = getInputData().getString(Content.URI);

        LogUtils.info(TAG, " start ...");

        try {

            Objects.requireNonNull(uriFile);


            List<Long> idxs = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    getApplicationContext().getContentResolver()
                            .openInputStream(Uri.parse(uriFile))))) {
                Objects.requireNonNull(reader);
                while (reader.ready()) {
                    idxs.add(Long.valueOf(reader.readLine()));
                }
            }

            for (long idx : idxs) {
                Uri uri = FileDocumentsProvider.getUriForThread(idx);
                DocumentFile document = DocumentFile.fromSingleUri(
                        getApplicationContext(), uri);
                Objects.requireNonNull(document);
                document.delete();
            }

            {
                IPFS.getInstance(getApplicationContext()).gc();
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);

        } finally {
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }
        return Result.success();
    }

}

