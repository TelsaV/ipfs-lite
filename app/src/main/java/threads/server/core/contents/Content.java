package threads.server.core.contents;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;


@androidx.room.Entity
public class Content {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "cid")
    private final String cid;

    @ColumnInfo(name = "timestamp")
    private final long timestamp;

    @ColumnInfo(name = "recursively")
    private final boolean recursively;

    Content(@NonNull String cid, long timestamp, boolean recursively) {
        this.cid = cid;
        this.timestamp = timestamp;
        this.recursively = recursively;

    }

    public static Content create(@NonNull String cid, long timestamp, boolean recursively) {

        return new Content(cid, timestamp, recursively);
    }

    public boolean isRecursively() {
        return recursively;
    }


    public long getTimestamp() {
        return timestamp;
    }


    @NonNull
    public String getCid() {
        return cid;
    }


    public boolean isExpired() {
        return getTimestamp() < System.currentTimeMillis();
    }
}
