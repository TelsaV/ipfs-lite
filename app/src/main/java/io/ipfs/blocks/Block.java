package io.ipfs.blocks;

import androidx.annotation.NonNull;

import io.ipfs.cid.Cid;

public interface Block {
    byte[] RawData();

    Cid Cid();

    @NonNull
    String toString();
}
