package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import java.time.Duration;

import io.Closeable;
import io.ipfs.bitswap.message.BitSwapMessage;
import io.ipfs.bitswap.network.Receiver;
import io.libp2p.network.Stream;
import io.libp2p.peer.ID;

public interface BitSwapNetwork extends Routing {
    boolean ConnectTo(@NonNull Closeable ctx, @NonNull ID p);

    Stream newStreamToPeer(@NonNull Closeable closeable, @NonNull ID to);

    boolean SupportsHave(@NonNull io.libp2p.protocol.ID protocol);

    // SendMessage sends a BitSwap message to a peer.
    void SendMessage(@NonNull Closeable closeable, @NonNull ID peer, @NonNull BitSwapMessage message);

    void msgToStream(@NonNull Closeable closeable, @NonNull Stream stream,
                     @NonNull BitSwapMessage message, @NonNull Duration sendTimeout);

    // SetDelegate registers the Reciver to handle messages received from the
    // network.
    void SetDelegate(@NonNull Receiver receiver);
}
