package io.ipfs.bitswap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.Closeable;
import io.ipfs.bitswap.message.BitSwapMessage;
import io.ipfs.bitswap.network.ConnectEventManager;
import io.ipfs.bitswap.network.Receiver;
import io.libp2p.host.ConnManager;
import io.libp2p.peer.ID;
import io.libp2p.routing.ContentRouting;

public interface BitSwapNetwork extends ContentRouting {
    boolean ConnectTo(@NonNull Closeable ctx, @NonNull ID p);


    boolean SupportsHave(@NonNull io.libp2p.protocol.ID protocol);

    // SendMessage sends a BitSwap message to a peer.
    void SendMessage(@NonNull Closeable closeable, @NonNull ID peer, @NonNull BitSwapMessage message);

    void msgToStream(@NonNull Closeable closeable, @NonNull ID to, @NonNull BitSwapMessage message);

    // SetDelegate registers the Reciver to handle messages received from the
    // network.
    void SetDelegate(@NonNull Receiver receiver);


    void DisconnectFrom(@NonNull Closeable closeable, @NonNull ID peer);

    @Nullable
    ConnectEventManager getConnectEventManager();

    @NonNull
    ConnManager ConnectionManager();

    ID Self();
}
