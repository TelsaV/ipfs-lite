package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import io.Closeable;
import io.ipfs.ClosedException;
import io.libp2p.host.ConnManager;
import io.libp2p.peer.PeerID;
import io.libp2p.routing.ContentRouting;
import lite.Stream;

public interface BitSwapNetwork extends ContentRouting {

    boolean ConnectTo(@NonNull Closeable closeable, @NonNull PeerID peer, boolean protect) throws ClosedException;

    boolean SupportsHave(@NonNull Stream stream);

    void WriteMessage(@NonNull Closeable closeable, @NonNull PeerID peer, @NonNull BitSwapMessage message) throws ClosedException;

    void SendMessage(@NonNull Closeable closeable, @NonNull PeerID peer, @NonNull BitSwapMessage message);

    void SendMessage(@NonNull lite.Stream stream, @NonNull BitSwapMessage message);

    Stream NewStream(@NonNull Closeable closeable, @NonNull PeerID peer) throws ClosedException;

    void SetDelegate(@NonNull Receiver receiver);

    @NonNull
    ConnManager ConnectionManager();

    PeerID Self();
}
