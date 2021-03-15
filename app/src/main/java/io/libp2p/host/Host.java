package io.libp2p.host;

import androidx.annotation.NonNull;

import io.Closeable;
import io.libp2p.network.StreamHandler;
import io.libp2p.peer.ID;

public interface Host extends ConnManager {
    boolean Connect(@NonNull Closeable ctx, @NonNull ID peer);

    void WriteMessage(@NonNull Closeable closeable, @NonNull ID peer,
                      @NonNull io.libp2p.protocol.ID protocol,
                      @NonNull byte[] data);

    // SetStreamHandler sets the protocol handler on the Host's Mux.
    // This is equivalent to:
    //   host.Mux().SetHandler(proto, handler)
    // (Threadsafe)
    void SetStreamHandler(@NonNull io.libp2p.protocol.ID proto, @NonNull StreamHandler handler);

    ID Self();
}
