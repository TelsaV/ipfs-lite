package io.ipfs.bitswap.network;

import androidx.annotation.NonNull;

import io.Closeable;
import io.ipfs.ClosedException;
import io.ipfs.bitswap.message.BitSwapMessage;
import lite.Stream;

public interface MessageSender {

    lite.Stream NewStream(@NonNull Closeable closeable) throws ClosedException;

    void SendMsg(@NonNull Closeable closeable, @NonNull Stream stream, @NonNull BitSwapMessage message) throws ClosedException;

    // Indicates whether the remote peer supports HAVE / DONT_HAVE messages
    boolean SupportsHave(lite.Stream stream);

    void Connect(@NonNull Closeable closeable, boolean protect) throws ClosedException;
}

