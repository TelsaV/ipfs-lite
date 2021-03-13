package io.libp2p.host;

import androidx.annotation.NonNull;

import java.util.List;

import io.Closeable;
import io.libp2p.network.Stream;
import io.libp2p.network.StreamHandler;
import io.libp2p.peer.ID;

public interface Host {
    boolean Connect(@NonNull Closeable ctx, @NonNull ID p);

    Stream NewStream(@NonNull Closeable closeable, @NonNull ID to,
                     @NonNull List<io.libp2p.protocol.ID> supportedProtocols);

    // SetStreamHandler sets the protocol handler on the Host's Mux.
    // This is equivalent to:
    //   host.Mux().SetHandler(proto, handler)
    // (Threadsafe)
    void SetStreamHandler(@NonNull io.libp2p.protocol.ID proto, @NonNull StreamHandler handler);
}
