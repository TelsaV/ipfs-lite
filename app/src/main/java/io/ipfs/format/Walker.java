package io.ipfs.format;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.Stack;

import io.ipfs.Closeable;
import io.ipfs.unixfs.FSNode;

public class Walker {
    private static final String TAG = Walker.class.getSimpleName();
    private final NavigableNode root;

    private Walker(NavigableNode navigableNode) {
        this.root = navigableNode;

    }

    public static Walker NewWalker(@NonNull NavigableNode navigableNode) {
        return new Walker(navigableNode);
    }

    void incrementActiveChildIndex(@NonNull Visitor visitor, @NonNull NavigableNode activeNode) {
        if (visitor.peekStage().index() + 1 <= activeNode.ChildTotal()) {
            visitor.peekStage().incrementIndex();
        }
    }

    @Nullable
    public NavigableNode Next(@NonNull Closeable closeable, @NonNull Visitor visitor) {

        if (!visitor.isRootVisited(true)) {
            if (visitor.getActiveNode().equals(root)) {
                return root;
            }
        }

        boolean success = down(closeable, visitor, visitor.getActiveNode());
        if (success) {
            return visitor.getActiveNode();
        }

        success = up(visitor);

        if (success) {
            return Next(closeable, visitor);
        }

        return null;
    }

    private boolean up(@NonNull Visitor visitor) {

        if (!visitor.isEmpty()) {
            visitor.popStage();
        } else {
            return false;
        }
        return NextChild(visitor, visitor.getActiveNode());

    }


    private boolean NextChild(@NonNull Visitor visitor, @NonNull NavigableNode activeNode) {
        incrementActiveChildIndex(visitor, activeNode);
        return visitor.peekStage().index() != activeNode.ChildTotal();
    }


    public boolean down(@NonNull Closeable closeable,
                        @NonNull Visitor visitor,
                        @NonNull NavigableNode activeNode) {
        int index = visitor.peekStage().index();

        NavigableNode child = fetchChild(closeable, activeNode, index);
        if (child != null) {
            visitor.pushActiveNode(child);
            return true;
        }
        return false;
    }


    @Nullable
    private NavigableNode fetchChild(@NonNull Closeable closeable,
                                     @NonNull NavigableNode activeNode,
                                     int index) {
        Objects.requireNonNull(activeNode);

        if (index >= activeNode.ChildTotal()) {
            return null;
        }

        return activeNode.FetchChild(closeable, index);
    }

    @NonNull
    public NavigableNode getRoot() {
        return root;
    }

    public Pair<Stack<Stage>, Long> Seek(@NonNull Closeable closeable,
                                         @NonNull Stack<Stage> stack,
                                         long offset) {

        if (offset < 0) {
            throw new RuntimeException("invalid offset");
        }

        if (offset == 0) {
            return Pair.create(stack, 0L);
        }

        long left = offset;

        NavigableNode visitedNode = stack.peek().getNode();

        Node node = NavigableIPLDNode.ExtractIPLDNode(visitedNode);

        if (node.getLinks().size() > 0) {
            // Internal node, should be a `mdag.ProtoNode` containing a
            // `unixfs.FSNode` (see the `balanced` package for more details).
            FSNode fsNode = FSNode.ExtractFSNode(node);

            // If there aren't enough size hints don't seek
            // (see the `io.EOF` handling error comment below).
            if (fsNode.NumChildren() != node.getLinks().size()) {
                throw new RuntimeException("ErrSeekNotSupported");
            }


            // Internal nodes have no data, so just iterate through the
            // sizes of its children (advancing the child index of the
            // `dagWalker`) to find where we need to go down to next in
            // the search
            for (int i = 0; i < fsNode.NumChildren(); i++) {

                long childSize = fsNode.BlockSize(i);

                if (childSize > left) {
                    stack.peek().setIndex(i);

                    NavigableNode fetched = visitedNode.FetchChild(closeable, i);
                    stack.push(new Stage(fetched));

                    return Seek(closeable, stack, left);
                }
                left -= childSize;
            }
        }

        return Pair.create(stack, left);
    }

    public Pair<Stack<Stage>, Long> Seek(@NonNull Closeable closeable, long offset) {

        Stack<Stage> stack = new Stack<>();
        stack.push(new Stage(getRoot()));

        return Seek(closeable, stack, offset);

    }
}

