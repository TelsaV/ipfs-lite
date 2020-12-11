package threads.server.ipfs;

import androidx.annotation.NonNull;

import java.util.Objects;

public class CID {
    @NonNull
    private final String cid;

    private CID(@NonNull String cid) {
        this.cid = cid;
    }

    public static CID create(@NonNull String cid) {
        Objects.requireNonNull(cid);
        return new CID(cid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CID pid1 = (CID) o;
        return Objects.equals(cid, pid1.cid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cid);
    }

    @Override
    @NonNull
    public String toString() {
        return cid;
    }

    @NonNull
    public String getCid() {
        return cid;
    }
}
