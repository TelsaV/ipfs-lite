package io.ipfs.bitswap.network;

import androidx.annotation.NonNull;

import io.Closeable;
import io.ipfs.bitswap.message.BitSwapMessage;
import io.libp2p.peer.ID;

public interface Receiver extends ConnectionListener {
    void ReceiveMessage(@NonNull Closeable closeable,
                        @NonNull ID sender,
                        @NonNull BitSwapMessage incoming);


    void ReceiveError(@NonNull ID from, @NonNull String error);
}
