package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import io.Closeable;
import io.libp2p.peer.ID;

public interface Receiver {
    void ReceiveMessage(@NonNull ID peer, @NonNull BitSwapMessage incoming);

    void ReceiveError(@NonNull ID peer, @NonNull String error);
}
