package io.ipfs.bitswap.network;

import androidx.annotation.NonNull;

import io.Closeable;
import io.ipfs.bitswap.message.BitSwapMessage;

public interface MessageSender {

    void SendMsg(@NonNull Closeable closeable, @NonNull BitSwapMessage message);

    // Indicates whether the remote peer supports HAVE / DONT_HAVE messages
    boolean SupportsHave();

    void multiAttempt(@NonNull Closeable closeable, @NonNull Attempt attempt);

    void Connect(@NonNull Closeable closeable);
}

