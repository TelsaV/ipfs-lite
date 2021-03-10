package io.ipfs.utils;


import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Objects;

import io.ipfs.Closeable;
import io.ipfs.cid.Builder;
import io.ipfs.format.Node;
import io.ipfs.format.Reader;
import io.ipfs.merkledag.DagBuilderHelper;
import io.ipfs.merkledag.DagBuilderParams;
import io.ipfs.merkledag.DagService;
import io.ipfs.unixfs.Directory;
import io.ipfs.unixfs.Trickle;
import threads.server.Settings;

public class Adder {
    @NonNull
    private final Closeable closeable;
    @NonNull
    private final DagService dagService;
    public boolean RawLeaves;
    public Builder CidBuilder;

    private Adder(@NonNull Closeable closeable, @NonNull DagService dagService) {
        this.closeable = closeable;
        this.dagService = dagService;
    }

    public static Adder NewAdder(@NonNull Closeable closeable, @NonNull DagService dagService) {
        return new Adder(closeable, dagService);
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
        Objects.requireNonNull(dir);
        dir.SetCidBuilder(CidBuilder);
        dir.AddChild(closeable, name, link);
        Node fnd = dir.GetNode();
        dagService.Add(closeable, fnd);
        return fnd;
    }

    public Node RemoveChild(@NonNull Node dirNode, @NonNull String name) {
        Directory dir = Directory.NewDirectoryFromNode(dagService, dirNode);
        Objects.requireNonNull(dir);
        dir.SetCidBuilder(CidBuilder);
        dir.RemoveChild(closeable, name);
        Node fnd = dir.GetNode();
        dagService.Add(closeable, fnd);
        return fnd;
    }


    public Node AddReader(@NonNull final ReaderStream reader) {

        Splitter splitter = new Splitter() {

            @Override
            public Reader Reader() {
                return reader;
            }

            @Override
            public byte[] NextBytes() {

                int size = Settings.CHUNK_SIZE;
                byte[] buf = new byte[size];
                int read = reader.Read(buf);
                if (read < 0) {
                    return null;
                } else if (read < size) {
                    return Arrays.copyOfRange(buf, 0, read);
                } else {
                    return buf;
                }
            }

            @Override
            public boolean Done() {
                return reader.Done();
            }
        };


        DagBuilderParams params = new DagBuilderParams(dagService,
                CidBuilder, RawLeaves);

        DagBuilderHelper db = params.New(splitter);

        return Trickle.Layout(db);
    }

}
