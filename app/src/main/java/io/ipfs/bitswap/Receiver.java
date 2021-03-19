package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import io.libp2p.peer.PeerID;

public interface Receiver {
    void ReceiveMessage(@NonNull PeerID peer, @NonNull BitSwapMessage incoming);

    void ReceiveError(@NonNull PeerID peer, @NonNull String error);
}
