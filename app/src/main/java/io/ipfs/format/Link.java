package io.ipfs.format;

import androidx.annotation.NonNull;

import java.util.Objects;

import io.ipfs.cid.Cid;

public class Link {

    @NonNull
    private final Cid cid;
    @NonNull
    private final String name;
    private final long size;


    private Link(@NonNull Cid cid, @NonNull String name, long size) {
        this.cid = cid;
        this.name = name;
        this.size = size;
    }

    public static Link create(@NonNull byte[] hash, @NonNull String name, long size) {
        Objects.requireNonNull(hash);
        Objects.requireNonNull(name);
        Cid cid = new Cid(hash);
        return new Link(cid, name, size);
    }

    @NonNull
    public Cid getCid() {
        return cid;
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


    @NonNull
    @Override
    public String toString() {
        return "LinkInfo{" +
                "cid='" + cid + '\'' +
                ", name='" + name + '\'' +
                ", size=" + size +
                '}';
    }


// --Commented out by Inspection START (4/28/2020 9:50 PM):
//    public boolean isRaw() {
//        return type == 0;
//    }
// --Commented out by Inspection STOP (4/28/2020 9:50 PM)
}
