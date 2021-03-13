package io.ipfs.bitswap.network;

import androidx.annotation.NonNull;

import io.Closeable;
import io.libp2p.network.Stream;

public interface MessageSender {
    void multiAttempt(@NonNull Closeable closeable, @NonNull Attempt attempt);

    Stream Connect(@NonNull Closeable ctx);
}

