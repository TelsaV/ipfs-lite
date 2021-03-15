package io.libp2p.host;

import androidx.annotation.NonNull;

import io.libp2p.peer.ID;

public interface PeerTagger {
    // TagPeer tags a peer with a string, associating a weight with the tag.
    void TagPeer(@NonNull ID peer, @NonNull String tag, int weight);

    // Untag removes the tagged value from the peer.
    void UntagPeer(@NonNull ID peer, @NonNull String tag);

}
