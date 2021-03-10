package threads.server.provider;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;

import androidx.annotation.NonNull;

import java.io.IOException;

import io.ipfs.LogUtils;
import io.ipfs.Storage;
import io.ipfs.utils.Reader;
import threads.server.core.blocks.BLOCKS;

public class MediaDataSource extends android.media.MediaDataSource {
    private static final String TAG = MediaDataSource.class.getSimpleName();
    private Reader fileReader;


    public MediaDataSource(@NonNull Storage storage, @NonNull String cid) {
        this.fileReader = Reader.getReader(storage, cid);
    }

    public static Bitmap getVideoFrame(@NonNull Context context, @NonNull String cid, long time) {

        Bitmap bitmap = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();

        try {

            retriever.setDataSource(new MediaDataSource(BLOCKS.getInstance(context), cid));

            if (time <= 0) {
                return retriever.getFrameAtTime();
            }


            bitmap = retriever.getScaledFrameAtTime(time * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 256, 256);


        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);

        } finally {

            try {

                retriever.release();

            } catch (RuntimeException throwable) {
                LogUtils.error(TAG, throwable);
            }
        }

        return bitmap;
    }

    @Override
    public void close() throws IOException {

        try {
            if (fileReader != null) {
                fileReader.close();
            }
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
            fileReader = null;
        }
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        try {

            byte[] data = fileReader.readAt(position, size);
            System.arraycopy(data, 0, buffer, offset, data.length);

            return (int) data.length;

        } catch (Throwable throwable) {
            throw new IOException(throwable);
        }

    }

    @Override
    public long getSize() {
        return fileReader.getSize();
    }
}
