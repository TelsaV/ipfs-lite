package io.libp2p.host;

import java.util.List;

import io.libp2p.peer.PeerID;

public interface ConnManager {
    List<PeerID> getPeers();
}
