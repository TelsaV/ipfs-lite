package io.ipfs.format;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.ipfs.Closeable;
import io.ipfs.cid.Cid;

public interface NodeGetter {
    @Nullable
    Node Get(@NonNull Closeable closeable, @NonNull Cid cid);
}
