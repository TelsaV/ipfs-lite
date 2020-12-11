package threads.server.core;

public class DeleteOperation {
    public static final String THREADS = "THREADS";
    public static final String PEERS = "PEERS";
    public String kind;
    public long[] indices;
    public String[] pids;

    public String getKind() {
        return kind;
    }
}
