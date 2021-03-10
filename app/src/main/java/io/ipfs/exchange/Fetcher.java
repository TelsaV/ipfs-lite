package io.ipfs.exchange;

import androidx.annotation.NonNull;

import io.ipfs.Closeable;
import io.ipfs.blocks.Block;
import io.ipfs.cid.Cid;

public interface Fetcher {
    Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid);
}
