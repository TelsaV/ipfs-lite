package io.ipfs.exchange;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.ipfs.Closeable;
import io.ipfs.cid.Cid;
import io.ipfs.format.Node;

public interface NodeGetter {
    @Nullable
    Node Get(@NonNull Closeable closeable, @NonNull Cid cid);
}
