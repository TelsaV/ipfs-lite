package io.ipfs.bitswap.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Hashtable;
import java.util.concurrent.Executors;

import io.Closeable;
import io.LogUtils;
import io.ipfs.cid.Cid;
import io.ipfs.format.Block;
import threads.server.Settings;

public class Pubsub {

    private static final String TAG = Pubsub.class.getSimpleName();


    // TODO make this threadsafe
    private final Hashtable<Cid, Block> maps = new Hashtable<>();

    @Nullable
    public Block Subscribe(@NonNull Closeable closeable, @NonNull Cid cid) {


        // TODO this might be very expensive
        Executors.newSingleThreadExecutor().execute(() -> {
            while (true) {
                if (closeable.isClosed()) {
                    synchronized (cid.String().intern()) {
                        cid.String().intern().notify();
                    }
                }
                try {
                    Thread.sleep(Settings.REFRESH);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });

        synchronized (cid.String().intern()) {
            try {
                // Calling wait() will block this thread until another thread
                // calls notify() on the object.
                LogUtils.error(TAG, "Lock " + cid.String());
                cid.String().intern().wait();
                LogUtils.error(TAG, "Is Released " + cid.String());
                return maps.get(cid);

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                maps.remove(cid);
            }
        }
    }

    public void Publish(@NonNull Block block) {
        Cid cid = block.Cid();
        synchronized (cid.String().intern()) {
            maps.put(cid, block);
            cid.String().intern().notify();

            LogUtils.error(TAG, "Release " + cid.String());
        }
    }
}
