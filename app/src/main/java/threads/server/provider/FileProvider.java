package threads.server.provider;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

import threads.lite.LogUtils;
import threads.server.BuildConfig;

public class FileProvider {
    private static final String TAG = FileProvider.class.getSimpleName();
    private static final String IMAGES = "images";
    private static final String DATA = "data";
    private static FileProvider INSTANCE = null;
    private final File mImageDir;
    private final File mDataDir;

    private FileProvider(@NonNull Context context) {
        mImageDir = new File(context.getCacheDir(), IMAGES);
        mDataDir = new File(context.getCacheDir(), DATA);
    }

    public static Uri getDataUri(@NonNull Context context, long idx) {

        FileProvider fileProvider = FileProvider.getInstance(context);
        File newFile = fileProvider.getDataFile(idx);
        if (newFile.exists()) {
            return androidx.core.content.FileProvider.getUriForFile(
                    context, BuildConfig.APPLICATION_ID, newFile);
        }
        return null;
    }


    @NonNull
    public static File getFile(@NonNull Context context, long idx) throws Exception {
        FileProvider fileProvider = FileProvider.getInstance(context);
        return fileProvider.createDataFile(idx);
    }


    public static FileProvider getInstance(@NonNull Context context) {

        if (INSTANCE == null) {
            synchronized (FileProvider.class) {
                if (INSTANCE == null) {
                    INSTANCE = new FileProvider(context);
                }
            }
        }
        return INSTANCE;
    }

    public File getDataFile(long idx) {
        return new File(getDataDir(), "" + idx);
    }

    @NonNull
    public File createDataFile(long idx) throws IOException {

        File file = getDataFile(idx);
        if (file.exists()) {
            boolean result = file.delete();
            if (!result) {
                LogUtils.info(TAG, "Deleting failed");
            }
        }
        boolean succes = file.createNewFile();
        if (!succes) {
            LogUtils.info(TAG, "Failed create a new file");
        }
        return file;
    }

    public File getImageDir() {
        if (!mImageDir.isDirectory() && !mImageDir.exists()) {
            boolean result = mImageDir.mkdir();
            if (!result) {
                throw new RuntimeException("image directory does not exists");
            }
        }
        return mImageDir;
    }

    public File createTempDataFile() throws IOException {
        return File.createTempFile("tmp", ".data", getDataDir());
    }

    public File getDataDir() {
        if (!mDataDir.isDirectory() && !mDataDir.exists()) {
            boolean result = mDataDir.mkdir();
            if (!result) {
                throw new RuntimeException("data directory does not exists");
            }
        }
        return mDataDir;
    }

    public void cleanImageDir() {
        deleteFile(getImageDir());
    }

    private void deleteFile(@NonNull File root) {
        try {
            if (root.isDirectory()) {
                File[] files = root.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isDirectory()) {
                            deleteFile(file);
                            boolean result = file.delete();
                            if (!result) {
                                LogUtils.error(TAG, "File " + file.getName() + " not deleted");
                            }
                        } else {
                            boolean result = file.delete();
                            if (!result) {
                                LogUtils.error(TAG, "File " + file.getName() + " not deleted");
                            }
                        }
                    }
                }
                boolean result = root.delete();
                if (!result) {
                    LogUtils.error(TAG, "File " + root.getName() + " not deleted");
                }
            } else {
                boolean result = root.delete();
                if (!result) {
                    LogUtils.error(TAG, "File " + root.getName() + " not deleted");
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    public void cleanDataDir() {
        deleteFile(getDataDir());
    }
}
