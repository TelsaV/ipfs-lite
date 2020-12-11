package threads.server.core.peers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;

import java.util.Objects;
import java.util.UUID;


@androidx.room.Entity
public class User {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "pid")
    private final String pid;
    @NonNull
    @ColumnInfo(name = "alias")
    private final String alias;
    @Nullable
    @ColumnInfo(name = "publicKey")
    private String publicKey;
    @ColumnInfo(name = "connected")
    private boolean connected;
    @Deprecated
    @ColumnInfo(name = "blocked")
    private boolean blocked;
    @ColumnInfo(name = "dialing")
    private boolean dialing;
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
    @Nullable
    @ColumnInfo(name = "work")
    private String work;
    @ColumnInfo(name = "sequence")
    private long sequence;
    @Nullable
    @ColumnInfo(name = "ipns")
    private String ipns;

    User(@NonNull String alias, @NonNull String pid) {
        this.alias = alias;
        this.pid = pid;
        this.blocked = false;
        this.dialing = false;
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
    public String getIpns() {
        return ipns;
    }

    public void setIpns(@Nullable String ipns) {
        this.ipns = ipns;
    }

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
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

    @Nullable
    public String getWork() {
        return work;
    }

    public void setWork(@Nullable String work) {
        this.work = work;
    }

    @Nullable
    public UUID getWorkUUID() {
        if (work != null) {
            return UUID.fromString(work);
        }
        return null;
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

    public boolean isDialing() {
        return dialing;
    }

    void setDialing(boolean dialing) {
        this.dialing = dialing;
    }


    boolean isBlocked() {
        return blocked;
    }

    void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    @NonNull
    public String getPid() {
        return pid;
    }

    @NonNull
    public String getAlias() {
        return alias;
    }


    @Nullable
    public String getPublicKey() {
        return publicKey;
    }


    public void setPublicKey(@Nullable String publicKey) {
        this.publicKey = publicKey;
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
                Objects.equals(dialing, user.isDialing()) &&
                Objects.equals(alias, user.getAlias()) &&
                Objects.equals(lite, user.isLite()) &&
                Objects.equals(blocked, user.isBlocked()) &&
                Objects.equals(publicKey, user.getPublicKey());
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
