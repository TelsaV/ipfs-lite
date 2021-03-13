package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import io.Closeable;
import io.ipfs.blocks.Block;
import io.ipfs.blockstore.Blockstore;
import io.ipfs.cid.Cid;
import io.ipfs.exchange.Interface;

public class BitSwap implements Interface {

    public static Interface New(@NonNull Closeable closeable,
                                @NonNull BitSwapNetwork bitSwapNetwork,
                                @NonNull Blockstore blockstore) {
        return new BitSwap();
    }

    @Override
    public Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid) {
        return null;
    }
}

