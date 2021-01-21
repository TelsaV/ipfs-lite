package threads.server.core.page;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;

@androidx.room.Entity
public class Page {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "hash")
    private final String hash;

    @Nullable
    @ColumnInfo(name = "content")
    private String content;
    @ColumnInfo(name = "outdated")
    private boolean outdated;

    public Page(@NonNull String hash) {
        this.hash = hash;
        this.outdated = false;
    }

    public boolean isOutdated() {
        return outdated;
    }

    public void setOutdated(boolean outdated) {
        this.outdated = outdated;
    }

    @NonNull
    @Override
    public String toString() {
        return "Page{" +
                "hash='" + hash + '\'' +
                ", content=" + content +
                ", outdated=" + outdated + '\'' +
                '}';
    }

    @NonNull
    public String getHash() {
        return hash;
    }

    @Nullable
    public String getContent() {
        return content;
    }

    public void setContent(@NonNull String content) {
        this.content = content;
    }


}