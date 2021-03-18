package io.ipfs.bitswap.network;

import androidx.annotation.NonNull;

import io.Closeable;
import io.ipfs.bitswap.message.BitSwapMessage;
import io.libp2p.peer.ID;

public interface Receiver {
    void ReceiveMessage(@NonNull Closeable closeable, @NonNull ID peer,
                        @NonNull BitSwapMessage incoming);


    void ReceiveError(@NonNull ID peer, @NonNull String error);
}
