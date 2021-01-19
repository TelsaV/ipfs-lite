package threads.server.services;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import threads.LogUtils;
import threads.server.BuildConfig;
import threads.server.InitApplication;
import threads.server.R;
import threads.server.core.events.EVENTS;
import threads.server.core.peers.Content;
import threads.server.core.peers.PEERS;
import threads.server.core.peers.User;
import threads.server.ipfs.IPFS;
import threads.server.provider.FileProvider;
import threads.server.utils.StorageLocation;
import threads.server.work.DeleteThreadsWorker;
import threads.server.work.UploadThreadsWorker;
import threads.server.work.UserConnectWorker;


public class LiteService {

    private static final String TAG = LiteService.class.getSimpleName();

    private static final String APP_KEY = "AppKey";
    private static final String GATEWAY_KEY = "gatewayKey";
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
                        ipfs.swarmConnect("/p2p/" + host, 10);
                    }
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });


    }

    @NonNull
    public static String getFileName(@NonNull Uri uri) {

        List<String> paths = uri.getPathSegments();
        if (!paths.isEmpty()) {
            return paths.get(paths.size() - 1);
        } else {
            return "" + uri.getHost();
        }

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

    @NonNull
    public static String getGateway(@NonNull Context context) {
        Objects.requireNonNull(context);
        SharedPreferences sharedPref = context.getSharedPreferences(
                APP_KEY, Context.MODE_PRIVATE);
        return Objects.requireNonNull(sharedPref.getString(GATEWAY_KEY,
                "https://gateway.ipfs.io"));
    }

    public static void setGateway(@NonNull Context context, @NonNull String gateway) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(gateway);
        SharedPreferences sharedPref = context.getSharedPreferences(
                APP_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(GATEWAY_KEY, gateway);
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

    public static void threads(@NonNull Context context, long... idxs) {

        try {
            FileProvider fileProvider =
                    FileProvider.getInstance(context);
            File file = fileProvider.createTempDataFile();

            try (PrintStream out = new PrintStream(file)) {
                for (long idx : idxs) {
                    out.println(idx);
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }

            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    context, BuildConfig.APPLICATION_ID, file);
            Objects.requireNonNull(uri);

            DeleteThreadsWorker.delete(context, uri);

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
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
                UploadThreadsWorker.load(context, parent, uri);
            }

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public static void connect(@NonNull Context context, @NonNull String pid) {

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {

                IPFS ipfs = IPFS.getInstance(context);
                if (!Objects.equals(ipfs.getHost(), pid)) {
                    if (!ipfs.isConnected(pid)) {
                        int timeout = InitApplication.getConnectionTimeout(context);
                        ipfs.swarmConnect(pid, timeout);
                    }
                }

            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });
    }

    public static void bootstrap(@NonNull Context context, int minPeers) {

        try {


            IPFS ipfs = IPFS.getInstance(context);

            if (!ipfs.isPrivateNetwork()) {

                ipfs.bootstrap(minPeers, 10);
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

    private static List<Pair<File, StorageVolume>> getExternalDirectories(@NonNull Context context) {
        List<Pair<File, StorageVolume>> externals = new ArrayList<>();
        StorageManager manager = (StorageManager)
                context.getSystemService(Activity.STORAGE_SERVICE);
        if (manager != null) {
            File[] dirs = context.getExternalFilesDirs(null);
            for (File dir : dirs) {

                StorageVolume volume = manager.getStorageVolume(dir);
                if (volume != null) {
                    if (!volume.isEmulated() && !volume.isPrimary()) {
                        externals.add(Pair.create(dir, volume));
                    }
                }
            }
        }
        return externals;
    }

    public static List<StorageLocation> getStorageLocations(@NonNull Context context) {

        List<StorageLocation> locations = new ArrayList<>();
        locations.add(new StorageLocation(context.getString(R.string.harddisk),
                context.getFilesDir(), 0, true));

        List<Pair<File, StorageVolume>> externals = getExternalDirectories(context);
        for (Pair<File, StorageVolume> entry : externals) {
            File file = entry.first;
            StorageVolume storageVolume = entry.second;
            locations.add(new StorageLocation(storageVolume.getDescription(context),
                    file, storageVolume.hashCode(), false));
        }
        return locations;

    }

    @NonNull
    public static StorageLocation getStorageLocation(@NonNull Context context) {
        File dir = IPFS.getExternalStorageDirectory(context);
        if (dir != null) {
            StorageManager manager = (StorageManager)
                    context.getSystemService(Activity.STORAGE_SERVICE);
            if (manager != null) {
                StorageVolume storageVolume = manager.getStorageVolume(dir);
                if (storageVolume != null) {
                    return new StorageLocation(storageVolume.getDescription(context),
                            dir, storageVolume.hashCode(), false);
                }
            }
        }
        return new StorageLocation(context.getString(R.string.harddisk),
                context.getFilesDir(), 0, true);
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
