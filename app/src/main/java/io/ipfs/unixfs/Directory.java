package io.ipfs.unixfs;

import androidx.annotation.NonNull;

import io.ipfs.Closeable;
import io.ipfs.cid.Builder;
import io.ipfs.format.Node;
import io.ipfs.format.ProtoNode;
import io.ipfs.merkledag.DagService;
import unixfs.pb.UnixfsProtos;


public interface Directory {


    static Directory NewDirectory(@NonNull DagService dagService) {

        return new BasicDirectory(dagService, Unixfs.EmptyDirNode());
    }

    static Directory NewDirectoryFromNode(@NonNull DagService dserv, @NonNull Node node) {
        ProtoNode protoNode = (ProtoNode) node;
        FSNode fsNode = FSNode.FSNodeFromBytes(protoNode.getData());

        if (fsNode.Type() == UnixfsProtos.Data.DataType.Directory) {
            return new BasicDirectory(dserv, (ProtoNode) protoNode.Copy());
        }
        throw new RuntimeException();
    }

    void SetCidBuilder(@NonNull Builder cidBuilder);

    Node GetNode();

    void AddChild(Closeable closeable, String name, Node link);

    class BasicDirectory implements Directory {
        private final ProtoNode protoNode;
        private final DagService dagService;

        BasicDirectory(@NonNull DagService dagService, @NonNull ProtoNode protoNode) {
            this.protoNode = protoNode;
            this.dagService = dagService;
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
        public void AddChild(@NonNull Closeable closeable, @NonNull String name, @NonNull Node link) {
            protoNode.RemoveNodeLink(name);
            protoNode.AddNodeLink(name, link);
        }
    }

}
