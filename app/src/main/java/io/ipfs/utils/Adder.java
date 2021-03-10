package io.ipfs.utils;

import androidx.annotation.NonNull;

import io.ipfs.Closeable;
import io.ipfs.cid.Builder;
import io.ipfs.format.Node;
import io.ipfs.merkledag.DagService;
import io.ipfs.unixfs.Directory;

public class Adder {
    @NonNull
    private final Closeable closeable;
    @NonNull
    private final DagService dagService;
    public boolean Trickle;
    public boolean RawLeaves;
    public boolean NoCopy;
    @NonNull
    public String Chunker;
    public Builder CidBuilder;

    private Adder(@NonNull Closeable closeable, @NonNull DagService dagService,
                  boolean trickle, @NonNull String chunker) {
        this.closeable = closeable;
        this.dagService = dagService;
        this.Trickle = trickle;
        this.Chunker = chunker;
    }

    public static Adder NewAdder(@NonNull Closeable closeable, @NonNull DagService dagService) {
        return new Adder(closeable, dagService, false, "");
    }


    public Node CreateEmptyDir() {
        Directory dir = Directory.NewDirectory(dagService);
        dir.SetCidBuilder(CidBuilder);
        Node fnd = dir.GetNode();
        dagService.Add(closeable, fnd);
        return fnd;
    }

    public Node AddLinkToDir(@NonNull Node dirNode, @NonNull String name, @NonNull Node link) {
        Directory dir = Directory.NewDirectoryFromNode(dagService, dirNode);
        dir.SetCidBuilder(CidBuilder);
        dir.AddChild(closeable, name, link);
        Node fnd = dir.GetNode();
        dagService.Add(closeable, fnd);
        return fnd;
    }
}
