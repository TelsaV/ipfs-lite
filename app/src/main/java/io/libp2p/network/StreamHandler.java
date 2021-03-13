package io.libp2p.network;

import androidx.annotation.NonNull;

import io.Closeable;

public interface StreamHandler {
    void handle(@NonNull Closeable closeable, @NonNull Stream stream);
}
