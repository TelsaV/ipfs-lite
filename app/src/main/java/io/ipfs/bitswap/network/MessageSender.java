package io.ipfs.bitswap.network;

import androidx.annotation.NonNull;

import io.Closeable;
import io.ipfs.bitswap.message.BitSwapMessage;
import io.libp2p.network.Stream;

public interface MessageSender {

    void SendMsg(@NonNull Closeable closeable, @NonNull BitSwapMessage message);

    void Close();

    void Reset();

    // Indicates whether the remote peer supports HAVE / DONT_HAVE messages
    boolean SupportsHave();

    void multiAttempt(@NonNull Closeable closeable, @NonNull Attempt attempt);

    Stream Connect(@NonNull Closeable ctx);
}

