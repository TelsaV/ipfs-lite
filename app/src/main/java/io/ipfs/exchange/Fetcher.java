package io.ipfs.exchange;

import androidx.annotation.NonNull;

import io.ipfs.blocks.Block;
import io.ipfs.cid.Cid;
import io.ipfs.Closeable;

public interface Fetcher {
    Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid);
}
