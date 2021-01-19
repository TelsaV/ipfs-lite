package threads.server.services;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import threads.LogUtils;
import threads.server.R;
import threads.server.core.DOCS;
import threads.server.core.events.EVENTS;
import threads.server.core.threads.THREADS;
import threads.server.core.threads.Thread;
import threads.server.ipfs.CID;
import threads.server.ipfs.IPFS;
import threads.server.provider.FileDocumentsProvider;
import threads.server.utils.MimeType;
import threads.server.work.UploadThreadWorker;

public class UploadService {

    private static final String TAG = UploadService.class.getSimpleName();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();


    public static void storeText(@NonNull Context context, long parent, @NonNull String text,
                                 boolean createTxtFile) {

        final THREADS threads = THREADS.getInstance(context);
        final IPFS ipfs = IPFS.getInstance(context);
        final DOCS docs = DOCS.getInstance(context);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {

                CID cid = ipfs.storeText(text);
                Objects.requireNonNull(cid);
                if (!createTxtFile) {
                    List<Thread> sameEntries = threads.getThreadsByContentAndParent(
                            ipfs.getLocation(), cid, parent);


                    if (sameEntries.isEmpty()) {

                        long idx = docs.createDocument(parent, MimeType.PLAIN_MIME_TYPE, cid,
                                null, cid.getCid(), text.length(), true, false);

                        docs.finishDocument(idx, true);

                    } else {
                        EVENTS.getInstance(context).warning(
                                context.getString(R.string.content_already_exists, cid.getCid()));
                    }
                } else {

                    String timeStamp = DateFormat.getDateTimeInstance().
                            format(new Date()).
                            replace(":", "").
                            replace(".", "_").
                            replace("/", "_").
                            replace(" ", "_");

                    String name = "TXT_" + timeStamp + ".txt";

                    long idx = docs.createDocument(parent, MimeType.PLAIN_MIME_TYPE, cid,
                            null, name, text.length(), true, false);

                    docs.finishDocument(idx, true);
                }

            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        });
    }

    public static void uploadFile(@NonNull Context context, long parent, @NonNull Uri uri) {

        long start = System.currentTimeMillis();
        LogUtils.info(TAG, " start ... " + uri);

        EXECUTOR.submit(() -> {
            try {
                DOCS docs = DOCS.getInstance(context);
                THREADS threads = THREADS.getInstance(context);

                String name = FileDocumentsProvider.getFileName(context, uri);
                String mimeType = FileDocumentsProvider.getMimeType(context, uri);

                long size = FileDocumentsProvider.getFileSize(context, uri);

                long idx = docs.createDocument(parent, mimeType, null,
                        uri, name, size, false, true);

                UUID request = UploadThreadWorker.load(context, idx, false);
                threads.setThreadWork(idx, request);

            } catch (Throwable e) {
                EVENTS.getInstance(context).error(
                        context.getString(R.string.file_not_found));
            } finally {
                LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
            }

        });
    }
}
