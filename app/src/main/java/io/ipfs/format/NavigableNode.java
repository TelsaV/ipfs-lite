package io.ipfs.format;

import io.ipfs.Closeable;

public interface NavigableNode {

    NavigableNode FetchChild(Closeable ctx, int childIndex);

    int ChildTotal();

}
