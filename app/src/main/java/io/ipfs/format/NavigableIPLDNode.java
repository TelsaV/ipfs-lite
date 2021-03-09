package io.ipfs.format;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.ipfs.Closeable;
import io.ipfs.cid.Cid;
import io.ipfs.exchange.NodeGetter;

public class NavigableIPLDNode implements NavigableNode {

    // Number of nodes to preload every time a child is requested.
// TODO: Give more visibility to this constant, it could be an attribute
// set in the `Walker` context that gets passed in `FetchChild`.
    private static final int preloadSize = 10;
    private final Node node;
    private final NodeGetter nodeGetter;
    private final List<Cid> cids = new ArrayList<>();
    private final List<NodePromise> childPromises;

    private NavigableIPLDNode(@NonNull Node node, @NonNull NodeGetter nodeGetter) {
        this.node = node;
        this.nodeGetter = nodeGetter;
        fillLinkCids(node);
        childPromises = new ArrayList<>(cids.size());
    }

    public static NavigableIPLDNode NewNavigableIPLDNode(
            @NonNull Node node, @NonNull NodeGetter nodeGetter) {
        return new NavigableIPLDNode(node, nodeGetter);
    }

    // ExtractIPLDNode is a helper function that takes a `NavigableNode`
// and returns the IPLD `Node` wrapped inside. Used in the `Visitor`
// function.
// TODO: Check for errors to avoid a panic?
    public static Node ExtractIPLDNode(@NonNull NavigableNode node) {
        if (node instanceof NavigableIPLDNode) {
            NavigableIPLDNode navigableIPLDNode = (NavigableIPLDNode) node;
            return navigableIPLDNode.GetIPLDNode();
        }
        throw new RuntimeException("not expected behaviour");
    }

    // GetIPLDNode returns the IPLD `Node` wrapped into this structure.
    public Node GetIPLDNode() {
        return node;
    }

    private void fillLinkCids(@NonNull Node node) {
        List<Link> links = node.getLinks();

        for (Link link : links) {
            cids.add(link.getCid());
        }
    }

    // GetNodes returns an array of 'FutureNode' promises, with each corresponding
// to the key with the same index as the passed in keys
    public List<NodePromise> GetNodes(@NonNull Closeable ctx, @NonNull NodeGetter ds, @NonNull List<Cid> keys) {

        // Early out if no work to do
        if (keys.size() == 0) {
            return new ArrayList<>();
        }

        List<NodePromise> promises = new ArrayList<>(keys.size());
        for (Cid cid : keys) {
            Node nd = nodeGetter.Get(ctx, cid);
            promises.add(NodePromise.NewNodePromise(ctx, nd));
        }


        /*
        dedupedKeys := dedupeKeys(keys)
        go func() {
            ctx, cancel := context.WithCancel(ctx)
            defer cancel()

            nodechan := ds.GetMany(ctx, dedupedKeys);

            for count := 0; count < len(keys); {
                select {
                    case opt, ok := <-nodechan:
                        if !ok {
                        for _, p := range promises {
                            p.Fail(ErrNotFound)
                        }
                        return
                    }

                    if opt.Err != nil {
                        for _, p := range promises {
                            p.Fail(opt.Err)
                        }
                        return
                    }

                    nd := opt.Node
                    c := nd.Cid()
                    for i, lnk_c := range keys {
                        if c.Equals(lnk_c) {
                            count++
                            promises[i].Send(nd)
                        }
                    }
                    case <-ctx.Done():
                        return
                }
            /*}
        }()*/
        return promises;
    }

    // Preload at most `preloadSize` child nodes from `beg` through promises
// created using this `ctx`.
    private void preload(Closeable ctx, int beg) {
        int end = beg + preloadSize;
        if (end >= cids.size()) {
            end = cids.size();
        }
        List<Cid> keys = cids.subList(beg, end);

        List<NodePromise> promises = GetNodes(ctx, nodeGetter, keys);
        for (int i = 0; i < promises.size(); i++) {
            NodePromise promise = promises.get(0);
            childPromises.add(beg + i, promise);
        }
    }

    @Override
    public NavigableNode FetchChild(Closeable ctx, int childIndex) {

        // This function doesn't check that `childIndex` is valid, that's
        // the `Walker` responsibility.

        // If we drop to <= preloadSize/2 preloading nodes, preload the next 10.
        for (int i = childIndex; i < childIndex + preloadSize / 2 && i < childPromises.size(); i++) {
            // TODO: Check if canceled.
            if (childPromises.get(i) == null) {
                preload(ctx, i);
                break;
            }
        }

        Node child = getPromiseValue(ctx, childIndex);
        Objects.requireNonNull(child);

        /* TODO
        switch err {
            case nil:
            case context.DeadlineExceeded, context.Canceled:
                if ctx.Err() != nil {
                return nil, ctx.Err()
            }

            // In this case, the context used to *preload* the node (in a previous
            // `FetchChild` call) has been canceled. We need to retry the load with
            // the current context and we might as well preload some extra nodes
            // while we're at it.
            nn.preload(ctx, childIndex)
            child, err = nn.getPromiseValue(ctx, childIndex)
            if err != nil {
                return nil, err
            }
            default:
                return nil, err
        } */

        return NewNavigableIPLDNode(child, nodeGetter);

    }


    @Override
    public int ChildTotal() {
        return GetIPLDNode().getLinks().size();
    }


    // Fetch the actual node (this is the blocking part of the mechanism)
// and invalidate the promise. `preload` should always be called first
// for the `childIndex` being fetch.
//
// TODO: Include `preload` into the beginning of this function?
// (And collapse the two calls in `FetchChild`).
    private Node getPromiseValue(Closeable ctx, int childIndex) {

        return nodeGetter.Get(ctx, cids.get(childIndex));

    }
}
