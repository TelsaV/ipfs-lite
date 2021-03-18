package io.libp2p.host;

import java.util.List;

import io.libp2p.peer.ID;

public interface ConnManager {
    List<ID> getPeers();
}
