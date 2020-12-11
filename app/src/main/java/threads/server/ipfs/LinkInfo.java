package threads.server.ipfs;

import androidx.annotation.NonNull;

import java.util.Objects;

public class LinkInfo {

    @NonNull
    private final String hash;
    @NonNull
    private final String name;
    private final long size;
    private final int type;


    private LinkInfo(@NonNull String hash,
                     @NonNull String name,
                     long size,
                     int type) {
        this.hash = hash;
        this.name = name;
        this.size = size;
        this.type = type;
    }

    public static LinkInfo create(String name, String hash, long size, int type) {
        Objects.requireNonNull(hash);
        Objects.requireNonNull(name);

        return new LinkInfo(hash, name, size, type);
    }

    @NonNull
    public CID getCid() {
        return CID.create(hash);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LinkInfo linkInfo = (LinkInfo) o;
        return size == linkInfo.size &&
                type == linkInfo.type &&
                hash.equals(linkInfo.hash) &&
                name.equals(linkInfo.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash, name, size, type);
    }

    public long getSize() {
        return size;
    }

    @NonNull
    public String getName() {
        return name;
    }


// --Commented out by Inspection START (4/28/2020 9:50 PM):
//    public int getType() {
//        return type;
//    }
// --Commented out by Inspection STOP (4/28/2020 9:50 PM)


    public boolean isDirectory() {
        return type == 1;

    }


    @NonNull
    @Override
    public String toString() {
        return "LinkInfo{" +
                "hash='" + hash + '\'' +
                ", name='" + name + '\'' +
                ", size=" + size +
                ", type=" + type +
                '}';
    }

    public boolean isFile() {
        return type == 2;

    }


// --Commented out by Inspection START (4/28/2020 9:50 PM):
//    public boolean isRaw() {
//        return type == 0;
//    }
// --Commented out by Inspection STOP (4/28/2020 9:50 PM)
}

