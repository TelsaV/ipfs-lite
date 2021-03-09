package io.ipfs.format;

import io.ipfs.Closeable;

public interface NavigableNode {


    // FetchChild returns the child of this node pointed to by `childIndex`.
    // A `Context` stored in the `Walker` is passed (`ctx`) that may contain
    // configuration attributes stored by the user before initiating the
    // walk operation.
    NavigableNode FetchChild(Closeable ctx, int childIndex);

    // ChildTotal returns the number of children of the `ActiveNode`.
    int ChildTotal();
}
