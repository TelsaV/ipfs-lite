package io.ipfs.format;

import androidx.annotation.NonNull;

import io.ipfs.Closeable;

public interface NodeAdder {
    void Add(@NonNull Closeable ctx, @NonNull Node nd);
}
