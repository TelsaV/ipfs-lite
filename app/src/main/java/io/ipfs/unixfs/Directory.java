package io.ipfs.unixfs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.ipfs.cid.Builder;
import io.ipfs.format.Node;
import io.ipfs.format.ProtoNode;
import io.ipfs.merkledag.DagService;
import io.protos.unixfs.UnixfsProtos;


public interface Directory {


    static Directory NewDirectory() {

        return new BasicDirectory(Unixfs.EmptyDirNode());
    }

    @Nullable
    static Directory NewDirectoryFromNode(@NonNull DagService dagService,
                                          @NonNull Node node) {
        ProtoNode protoNode = (ProtoNode) node;
        FSNode fsNode = FSNode.FSNodeFromBytes(protoNode.getData());

        if (fsNode.Type() == UnixfsProtos.Data.DataType.Directory) {
            return new BasicDirectory((ProtoNode) protoNode.Copy());
        }

        if (fsNode.Type() == UnixfsProtos.Data.DataType.HAMTShard) {
            Shard shard = Hamt.NewHamtFromDag(dagService, node);
            return new HAMTDirectory(shard);
        }

        return null;
    }

    void SetCidBuilder(@NonNull Builder cidBuilder);

    Node GetNode();

    void AddChild(@NonNull String name, @NonNull Node link);

    void RemoveChild(@NonNull String name);

    class HAMTDirectory implements  Directory {
        private final Shard shard;

        public HAMTDirectory(@NonNull Shard shard) {
            this.shard = shard;
        }

        @Override
        public void SetCidBuilder(@NonNull Builder cidBuilder) {
            throw new RuntimeException("not yet supported");
        }

        @Override
        public Node GetNode() {
            return shard.Node();
        }

        @Override
        public void AddChild(@NonNull String name, @NonNull Node link) {
            throw new RuntimeException("not yet supported");
        }

        @Override
        public void RemoveChild(@NonNull String name) {
            throw new RuntimeException("not yet supported");
        }
    }

    class BasicDirectory implements Directory {
        private final ProtoNode protoNode;

        BasicDirectory(@NonNull ProtoNode protoNode) {
            this.protoNode = protoNode;
        }

        @Override
        public void SetCidBuilder(@NonNull Builder cidBuilder) {
            protoNode.SetCidBuilder(cidBuilder);
        }

        @Override
        public Node GetNode() {
            return protoNode;
        }

        @Override
        public void AddChild(@NonNull String name, @NonNull Node link) {
            protoNode.RemoveNodeLink(name);
            protoNode.AddNodeLink(name, link);
        }

        @Override
        public void RemoveChild(@NonNull String name) {
            protoNode.RemoveNodeLink(name);
        }
    }

}
