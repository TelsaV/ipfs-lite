package io.ipfs.blocks;

import androidx.annotation.NonNull;

import io.ipfs.cid.Cid;

public class BasicBlock implements Block {
    private final Cid cid;
    private final byte[] data;

    public BasicBlock(@NonNull Cid cid, @NonNull byte[] data) {
        this.cid = cid;
        this.data = data;
    }

    public static Block NewBlockWithCid(@NonNull Cid cid, @NonNull byte[] data) {
        return new BasicBlock(cid, data);
    }

    @Override
    public byte[] RawData() {
        return data;
    }

    @Override
    public Cid Cid() {
        return cid;
    }

}
