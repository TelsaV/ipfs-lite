package threads.server.core.peers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;

import java.util.Objects;


@androidx.room.Entity
public class User {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "pid")
    private final String pid;
    @NonNull
    @ColumnInfo(name = "alias")
    private final String alias;
    @ColumnInfo(name = "connected")
    private boolean connected;
    @ColumnInfo(name = "lite")
    private boolean lite;
    @ColumnInfo(name = "visible")
    private boolean visible;
    @NonNull
    @ColumnInfo(name = "address")
    private String address;
    @ColumnInfo(name = "timestamp")
    private long timestamp;
    @Nullable
    @ColumnInfo(name = "agent")
    private String agent;
    @Deprecated
    @ColumnInfo(name = "sequence")
    private long sequence;
    @Deprecated
    @Nullable
    @ColumnInfo(name = "ipns")
    private String ipns;

    User(@NonNull String alias, @NonNull String pid) {
        this.alias = alias;
        this.pid = pid;
        this.connected = false;
        this.visible = true;
        this.lite = false;
        this.address = "";
        this.timestamp = 0L;
        this.sequence = 0L;
    }

    @NonNull
    static User createUser(@NonNull String alias, @NonNull String pid) {
        return new User(alias, pid);
    }

    @Nullable
    String getIpns() {
        return ipns;
    }

    void setIpns(@Nullable String ipns) {
        this.ipns = ipns;
    }

    long getSequence() {
        return sequence;
    }

    void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public boolean hasName() {
        return !Objects.equals(alias, pid);
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isLite() {
        return lite;
    }

    public void setLite(boolean lite) {
        this.lite = lite;
    }


    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }


    @NonNull
    public String getPid() {
        return pid;
    }

    @NonNull
    public String getAlias() {
        return alias;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(pid, user.pid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pid);
    }

    public boolean areItemsTheSame(@NonNull User user) {

        return this.pid.equals(user.pid);

    }

    public boolean sameContent(@NonNull User user) {

        if (this == user) return true;
        return Objects.equals(connected, user.isConnected()) &&
                Objects.equals(alias, user.getAlias()) &&
                Objects.equals(lite, user.isLite());
    }

    @Nullable
    public String getAgent() {
        return agent;
    }

    public void setAgent(@Nullable String agent) {
        this.agent = agent;
    }

    @NonNull
    public String getAddress() {
        return address;
    }

    public void setAddress(@NonNull String address) {
        this.address = address;
    }
}
