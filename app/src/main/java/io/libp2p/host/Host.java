package io.libp2p.host;

import androidx.annotation.NonNull;

import java.util.List;

import io.Closeable;
import io.ipfs.ClosedException;
import io.libp2p.network.StreamHandler;
import io.libp2p.peer.ID;
import lite.Stream;

public interface Host extends ConnManager {
    boolean Connect(@NonNull Closeable ctx, @NonNull ID peer, boolean protect) throws ClosedException;

    long WriteMessage(@NonNull Closeable closeable,
                      @NonNull ID peer,
                      @NonNull List<io.libp2p.protocol.ID> protocols,
                      @NonNull byte[] bytes) throws ClosedException;


    Stream NewStream(@NonNull Closeable closeable, @NonNull ID peer,
                     @NonNull List<io.libp2p.protocol.ID> protocols) throws ClosedException;

    // SetStreamHandler sets the protocol handler on the Host's Mux.
    // This is equivalent to:
    //   host.Mux().SetHandler(proto, handler)
    // (Threadsafe)
    void SetStreamHandler(@NonNull io.libp2p.protocol.ID proto, @NonNull StreamHandler handler);

    ID Self();
}
