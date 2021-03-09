package io.ipfs.offline;

import androidx.annotation.NonNull;

import io.ipfs.Closeable;
import io.ipfs.blocks.Block;
import io.ipfs.blockstore.Blockstore;
import io.ipfs.cid.Cid;
import io.ipfs.exchange.Interface;

public class Exchange implements Interface {
    private final Blockstore blockstore;

    public Exchange(@NonNull Blockstore blockstore) {
        this.blockstore = blockstore;
    }

    @Override
    public Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid) {
        return blockstore.Get(cid);
    }
}
