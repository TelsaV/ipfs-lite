package io.ipfs.bitswap.network;

import androidx.annotation.NonNull;

import io.Closeable;
import io.ipfs.bitswap.message.BitSwapMessage;

public interface Receiver {
    void ReceiveMessage(@NonNull Closeable closeable,
                        @NonNull String sender,
                        @NonNull BitSwapMessage incoming);

    //ReceiveError(error)

    // Connected/Disconnected warns bitswap about peer connections.
    //PeerConnected(peer.ID)
    //PeerDisconnected(peer.ID)
}
