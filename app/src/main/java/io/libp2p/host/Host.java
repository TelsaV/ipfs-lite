package io.libp2p.host;

import androidx.annotation.NonNull;

import io.Closeable;
import io.libp2p.peer.ID;

public interface Host {
    boolean Connect(@NonNull Closeable ctx, @NonNull ID p);
}
