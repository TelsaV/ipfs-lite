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
import threads.server.ipfs.IPFS;
import threads.server.provider.FileDocumentsProvider;
import threads.server.utils.MimeType;
import threads.server.work.UploadContentWorker;

public class UploadService {

    private static final String TAG = UploadService.class.getSimpleName();



    public static void storeText(@NonNull Context context, long parent, @NonNull String text,
                                 boolean createTxtFile) {

        final THREADS threads = THREADS.getInstance(context);
        final IPFS ipfs = IPFS.getInstance(context);
        final DOCS docs = DOCS.getInstance(context);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {

                String cid = ipfs.storeText(text);
                Objects.requireNonNull(cid);
                if (!createTxtFile) {
                    List<Thread> sameEntries = threads.getThreadsByContentAndParent(
                            cid, parent);


                    if (sameEntries.isEmpty()) {

                        long idx = docs.createDocument(parent, MimeType.PLAIN_MIME_TYPE, cid,
                                null, cid, text.length(), true, false);

                        docs.finishDocument(idx);

                    } else {
                        EVENTS.getInstance(context).warning(
                                context.getString(R.string.content_already_exists, cid));
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

                    docs.finishDocument(idx);
                }

            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        });
    }
}
