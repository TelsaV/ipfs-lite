package threads.server.ipfs;

public interface Progress extends Closeable {

    void setProgress(int progress);

    boolean doProgress();

}
