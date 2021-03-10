package io.ipfs.blockservice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.ipfs.Closeable;
import io.ipfs.blocks.Block;
import io.ipfs.cid.Cid;

public interface BlockGetter {
    @Nullable
    Block GetBlock(@NonNull Closeable closeable, @NonNull Cid cid);

    void AddBlock(@NonNull Block block);
}
