package io.ipfs.routing;

import androidx.annotation.NonNull;

import io.ipfs.Closeable;

public interface Providers extends Closeable {
    void Peer(@NonNull String peerID);
}
