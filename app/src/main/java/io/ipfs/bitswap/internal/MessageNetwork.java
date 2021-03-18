package io.ipfs.bitswap.internal;

import androidx.annotation.NonNull;

import io.Closeable;
import io.ipfs.ClosedException;
import io.libp2p.peer.ID;

public interface MessageNetwork {
    boolean ConnectTo(@NonNull Closeable closeable, @NonNull ID peer, boolean protect) throws ClosedException;

    ID Self();
}
