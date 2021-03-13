package threads.server.services;

import android.content.ClipData;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.io.File;
import java.io.PrintStream;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.LogUtils;
import threads.server.BuildConfig;
import threads.server.R;
import threads.server.core.Content;
import threads.server.core.DOCS;
import threads.server.core.events.EVENTS;
import threads.server.core.peers.PEERS;
import threads.server.core.peers.User;
import threads.server.core.threads.THREADS;
import io.ipfs.IPFS;
import threads.server.provider.FileDocumentsProvider;
import threads.server.provider.FileProvider;
import threads.server.work.UploadFileWorker;
import threads.server.work.UploadFilesWorker;
import threads.server.work.UserConnectWorker;


public class LiteService {

    private static final String TAG = LiteService.class.getSimpleName();

    private static final String APP_KEY = "AppKey";
    private static final String PIN_SERVICE_TIME_KEY = "pinServiceTimeKey";
    private static final String CONTENT_KEY = "contentKey";


    @NonNull
    public static FileInfo getFileInfo(@NonNull Context context) {

        Objects.requireNonNull(context);
        SharedPreferences sharedPref = context.getSharedPreferences(
                APP_KEY, Context.MODE_PRIVATE);
        String filename = sharedPref.getString(Content.INFO + Content.NAME, null);
        Objects.requireNonNull(filename);
        String mimeType = sharedPref.getString(Content.INFO + Content.TYPE, null);
        Objects.requireNonNull(mimeType);
        String uri = sharedPref.getString(Content.INFO + Content.URI, null);
        Objects.requireNonNull(uri);
        long size = sharedPref.getLong(Content.INFO + Content.SIZE, 0L);

        return new FileInfo(Uri.parse(uri), filename, mimeType, size);
    }

    public static void setFileInfo(@NonNull Context context, @NonNull Uri uri,
                                   @NonNull String filename, @NonNull String mimeType,
                                   long size) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(uri);
        Objects.requireNonNull(filename);
        Objects.requireNonNull(mimeType);
        SharedPreferences sharedPref = context.getSharedPreferences(
                APP_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        editor.putString(Content.INFO + Content.NAME, filename);
        editor.putString(Content.INFO + Content.TYPE, mimeType);
        editor.putLong(Content.INFO + Content.SIZE, size);
        editor.putString(Content.INFO + Content.URI, uri.toString());
        editor.apply();
    }

    @Nullable
    private static String getHost(@NonNull Uri uri) {
        try {
            if (Objects.equals(uri.getScheme(), Content.IPNS)) {
                return uri.getHost();
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return null;
    }

    public static void connectUri(@NonNull Context context, @NonNull Uri uri) {


        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {

                String host = getHost(uri);
                if (host != null) {
                    IPFS ipfs = IPFS.getInstance(context);

                    if (!ipfs.isConnected(host)) {
                        ipfs.swarmConnect(Content.P2P_PATH + host, null, 10);
                    }
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });


    }


    @Nullable
    public static Uri getContentUri(@NonNull Context context) {
        Objects.requireNonNull(context);
        SharedPreferences sharedPref = context.getSharedPreferences(
                APP_KEY, Context.MODE_PRIVATE);
        String content = sharedPref.getString(CONTENT_KEY, null);
        if (content != null) {
            return Uri.parse(content);
        }
        return null;
    }

    public static void setContentUri(@NonNull Context context, @NonNull Uri contentUri) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(contentUri);
        SharedPreferences sharedPref = context.getSharedPreferences(
                APP_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(CONTENT_KEY, contentUri.toString());
        editor.apply();

    }

    public static int getPublishServiceTime(@NonNull Context context) {
        Objects.requireNonNull(context);
        SharedPreferences sharedPref = context.getSharedPreferences(APP_KEY, Context.MODE_PRIVATE);
        return sharedPref.getInt(PIN_SERVICE_TIME_KEY, 6);
    }

    public static void setPublisherServiceTime(@NonNull Context context, int timeout) {
        Objects.requireNonNull(context);
        SharedPreferences sharedPref = context.getSharedPreferences(APP_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(PIN_SERVICE_TIME_KEY, timeout);
        editor.apply();
    }


    public static void file(@NonNull Context context, long parent, @NonNull Uri uri) {

        long start = System.currentTimeMillis();
        LogUtils.info(TAG, " start ... " + uri);

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                DOCS docs = DOCS.getInstance(context);
                THREADS threads = THREADS.getInstance(context);

                String name = FileDocumentsProvider.getFileName(context, uri);
                String mimeType = FileDocumentsProvider.getMimeType(context, uri);

                long size = FileDocumentsProvider.getFileSize(context, uri);

                long idx = docs.createDocument(parent, mimeType, null,
                        uri, name, size, false, true);

                UUID request = UploadFileWorker.load(context, idx);
                threads.setThreadWork(idx, request);

            } catch (Throwable e) {
                EVENTS.getInstance(context).error(
                        context.getString(R.string.file_not_found));
            } finally {
                LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
            }

        });
    }

    public static void files(@NonNull Context context, @NonNull ClipData data, long parent) {

        try {

            int items = data.getItemCount();

            if (items > 0) {
                FileProvider fileProvider =
                        FileProvider.getInstance(context);
                File file = fileProvider.createTempDataFile();

                try (PrintStream out = new PrintStream(file)) {
                    for (int i = 0; i < items; i++) {
                        ClipData.Item item = data.getItemAt(i);
                        Uri uri = item.getUri();
                        out.println(uri.toString());
                    }
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }

                Uri uri = androidx.core.content.FileProvider.getUriForFile(
                        context, BuildConfig.APPLICATION_ID, file);
                Objects.requireNonNull(uri);
                UploadFilesWorker.load(context, parent, uri);
            }

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public static void connect(@NonNull Context context,
                               @NonNull String user,
                               @Nullable String name,
                               @Nullable String address,
                               boolean addMessage) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(user);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                PEERS peers = PEERS.getInstance(context);
                EVENTS events = EVENTS.getInstance(context);
                IPFS ipfs = IPFS.getInstance(context);

                if (!peers.hasUser(user)) {
                    String userName = user;
                    if (name != null && !name.isEmpty()) {
                        userName = name;
                    }

                    User newUser = peers.createUser(ipfs.base58(user), userName);

                    if (address != null) {
                        newUser.setAddress(address);
                    }
                    peers.storeUser(newUser);

                    if (addMessage) {
                        events.warning(context.getString(R.string.added_pid_to_peers, user));
                    }

                } else {
                    events.warning(context.getString(R.string.peer_exists_with_pid));
                    return;
                }


                OneTimeWorkRequest work = UserConnectWorker.getWork(user);
                WorkManager.getInstance(context).enqueue(work);

            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });
    }

    public static class FileInfo {
        @NonNull
        private final Uri uri;
        @NonNull
        private final String filename;
        @NonNull
        private final String mimeType;

        private final long size;

        public FileInfo(@NonNull Uri uri, @NonNull String filename,
                        @NonNull String mimeType, long size) {
            this.uri = uri;
            this.filename = filename;
            this.mimeType = mimeType;
            this.size = size;
        }

        @NonNull
        public Uri getUri() {
            return uri;
        }

        @NonNull
        public String getFilename() {
            return filename;
        }

        @NonNull
        public String getMimeType() {
            return mimeType;
        }


        public long getSize() {
            return size;
        }
    }


}
