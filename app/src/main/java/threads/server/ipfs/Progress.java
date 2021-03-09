package threads.server.ipfs;

import io.ipfs.Closeable;

public interface Progress extends Closeable {

    void setProgress(int progress);

    boolean doProgress();

}
