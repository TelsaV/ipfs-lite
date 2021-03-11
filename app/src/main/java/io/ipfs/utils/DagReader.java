package io.ipfs.utils;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

import io.ipfs.Closeable;
import io.ipfs.format.NavigableIPLDNode;
import io.ipfs.format.NavigableNode;
import io.ipfs.format.Node;
import io.ipfs.format.NodeGetter;
import io.ipfs.format.ProtoNode;
import io.ipfs.format.RawNode;
import io.ipfs.format.Stage;
import io.ipfs.format.Visitor;
import io.ipfs.format.Walker;
import io.ipfs.unixfs.FSNode;

public class DagReader implements java.io.Closeable {
    private static final String TAG = DagReader.class.getSimpleName();
    private final Closeable ctx;
    private final long size;
    private final Visitor visitor;

    private final Walker dagWalker;
    public AtomicInteger atomicLeft = new AtomicInteger(0);

    public DagReader(@NonNull Closeable closeable, @NonNull Walker dagWalker, long size) {
        this.ctx = closeable;
        this.dagWalker = dagWalker;
        this.size = size;
        this.visitor = new Visitor(dagWalker.getRoot());

    }

    public static DagReader NewDagReader(@NonNull Closeable ctx,
                                         @NonNull Node node,
                                         @NonNull NodeGetter serv) {
        long size = 0;


        if (node instanceof RawNode) {
            size = node.getData().length;
        } else if (node instanceof ProtoNode) {
            FSNode fsNode = FSNode.FSNodeFromBytes(node.getData());

            switch (fsNode.Type()) {
                case Raw:
                case File:
                    size = fsNode.FileSize();
                    break;
                /*
                case Directory, HAMTShard:
                    // Dont allow reading directories
                    return nil, ErrIsDir

                case Metadata:
                    if len(n.Links()) == 0 {
                    return nil, errors.New("incorrectly formatted metadata object")
                }
                child, err := n.Links()[0].GetNode(ctx, serv)
                if err != nil {
                    return nil, err
                }

                childpb, ok := child.(*mdag.ProtoNode)
                if !ok {
                    return nil, mdag.ErrNotProtobuf
                }
                return NewDagReader(ctx, childpb, serv)
                case Symlink:
                    return nil, ErrCantReadSymlinks
                default:
                    throw new RuntimeException("type not supported");*/
            }
        } else {
            throw new RuntimeException("type not supported");
        }



/*
        switch n := n.(type) {
        case *mdag.RawNode:
            size = uint64(len(n.RawData()))

        case *mdag.ProtoNode:
            fsNode, err := unixfs.FSNodeFromBytes(n.Data())
            if err != nil {
            return nil, err
        }

        switch fsNode.Type() {

        }
        default:
            return nil, ErrUnkownNodeType


        //ctxWithCancel, cancel := context.WithCancel(ctx)
        */

        Walker dagWalker = Walker.NewWalker(NavigableIPLDNode.NewNavigableIPLDNode(node, serv));
        return new DagReader(ctx, dagWalker, size);

    }

    public long getSize() {
        return size;
    }

    @Override
    public void close() {
        // TODO
    }

    public void Seek(long offset) {
        Pair<Stack<Stage>, Long> result = dagWalker.Seek(ctx, offset);
        this.atomicLeft.set(result.second.intValue());
        this.visitor.reset(result.first);
    }

    @Nullable
    public byte[] loadNextData() {


        int left = atomicLeft.getAndSet(0);
        if (left > 0) {
            NavigableNode navigableNode = visitor.peekStage().getNode();

            Node node = NavigableIPLDNode.ExtractIPLDNode(navigableNode);

            if (node.getLinks().size() == 0) {

                byte[] data = FSNode.ReadUnixFSNodeData(node);

                return Arrays.copyOfRange(data, left, data.length);
                }
            }

            while (true) {
                NavigableNode visitedNode = dagWalker.Next(ctx, visitor);
                if (visitedNode == null) {
                    return null;
                }

                Node node = NavigableIPLDNode.ExtractIPLDNode(visitedNode);
                if (node.getLinks().size() > 0) {
                    continue;
                }

                return FSNode.ReadUnixFSNodeData(node);
            }

    }
}
