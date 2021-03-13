package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import io.Closeable;
import io.libp2p.peer.ID;

public interface BitSwapNetwork {
    boolean ConnectTo(@NonNull Closeable ctx, @NonNull ID p);
}
