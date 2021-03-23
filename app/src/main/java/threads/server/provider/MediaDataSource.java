package threads.server.provider;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.LogUtils;
import io.ipfs.ClosedException;
import io.ipfs.Storage;
import io.ipfs.exchange.Interface;
import io.ipfs.format.BlockStore;
import io.ipfs.offline.Exchange;
import io.ipfs.utils.Reader;
import threads.server.core.blocks.BLOCKS;

public class MediaDataSource extends android.media.MediaDataSource {
    private static final String TAG = MediaDataSource.class.getSimpleName();
    private final AtomicBoolean release = new AtomicBoolean(false);
    private Reader fileReader;

    public MediaDataSource(@NonNull Storage storage, @NonNull String cid) throws ClosedException {
        BlockStore blockstore = BlockStore.NewBlockstore(storage);
        Interface exchange = new Exchange(blockstore);
        this.fileReader = Reader.getReader(release::get, blockstore, exchange, cid);
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
            release.set(true);
            fileReader = null;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        try {
            byte[] data = new byte[size];
            fileReader.readNextData(position, size, data);
            System.arraycopy(data, 0, buffer, offset, data.length);
            return data.length;
        } catch (Throwable throwable) {
            throw new IOException(throwable);
        }
    }

    @Override
    public long getSize() {
        return fileReader.getSize();
    }
}
