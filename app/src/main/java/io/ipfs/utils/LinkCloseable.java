package io.ipfs.utils;

import androidx.annotation.NonNull;

import io.ipfs.Closeable;

public interface LinkCloseable extends Closeable {
    void info(@NonNull String name, @NonNull String hash, long size, int type);
}
