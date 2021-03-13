package io.ipfs.bitswap.network;

import androidx.annotation.NonNull;

import io.libp2p.peer.ID;

public interface ConnectionListener {
    // Connected/Disconnected warns bitswap about peer connections.
    void PeerConnected(@NonNull ID peer);

    void PeerDisconnected(@NonNull ID peer);
}
