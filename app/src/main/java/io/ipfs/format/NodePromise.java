package io.ipfs.format;

import androidx.annotation.NonNull;

import io.ipfs.Closeable;

public class NodePromise {
    private final Node node;
    private final Closeable closeable;

    private NodePromise(@NonNull Closeable closeable, @NonNull Node node) {
        this.closeable = closeable;
        this.node = node;
    }


    // NodePromise provides a promise like interface for a dag Node
// the first call to Get will block until the Node is received
// from its internal channels, subsequent calls will return the
// cached node.
//
// Thread Safety: This is multiple-consumer/single-producer safe.
    public static NodePromise NewNodePromise(Closeable ctx, @NonNull Node node) {
        return new NodePromise(ctx, node);
    }

    public Node Get(@NonNull Closeable ctx) {
        return node;
    }
}
