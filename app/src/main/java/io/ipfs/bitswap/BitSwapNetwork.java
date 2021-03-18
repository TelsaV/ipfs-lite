package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import io.Closeable;
import io.ipfs.ClosedException;
import io.ipfs.bitswap.internal.MessageNetwork;
import io.ipfs.bitswap.message.BitSwapMessage;
import io.libp2p.host.ConnManager;
import io.libp2p.peer.ID;
import io.libp2p.routing.ContentRouting;
import lite.Stream;

public interface BitSwapNetwork extends ContentRouting, MessageNetwork {


    boolean SupportsHave(@NonNull Stream stream);

    // SendMessage sends a BitSwap message to a peer.
    void SendMessage(@NonNull Closeable closeable, @NonNull ID peer, @NonNull BitSwapMessage message);

    void SendMessage(@NonNull lite.Stream stream, @NonNull BitSwapMessage message);

    Stream NewStream(@NonNull Closeable closeable, @NonNull ID peer) throws ClosedException;

    // SetDelegate registers the Reciver to handle messages received from the
    // network.
    void SetDelegate(@NonNull Receiver receiver);


    void DisconnectFrom(@NonNull Closeable closeable, @NonNull ID peer);

    @NonNull
    ConnManager ConnectionManager();

    ID Self();
}
