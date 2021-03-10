package io.ipfs.merkledag;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.ipfs.cid.Builder;
import io.ipfs.format.Node;
import io.ipfs.format.ProtoNode;
import io.ipfs.format.RawNode;
import io.ipfs.unixfs.FSNode;
import io.ipfs.utils.Splitter;
import threads.server.Settings;
import unixfs.pb.UnixfsProtos;

public class DagBuilderHelper {
    private final DagService dagService;
    private final Builder builder;
    private final Splitter splitter;
    private final boolean rawLeaves;


    public DagBuilderHelper(@NonNull DagService dagService,
                            @NonNull Builder builder,
                            @NonNull Splitter splitter,
                            boolean rawLeaves) {
        this.dagService = dagService;
        this.builder = builder;
        this.splitter = splitter;
        this.rawLeaves = rawLeaves;
    }


    public FSNodeOverDag NewFSNodeOverDag(@NonNull UnixfsProtos.Data.DataType fsNodeType) {
        return new FSNodeOverDag(new ProtoNode(), FSNode.NewFSNode(fsNodeType), builder);
    }


    // NewLeafDataNode builds the `node` with the data obtained from the
// Splitter with the given constraints (BlockSizeLimit, RawLeaves)
// specified when creating the DagBuilderHelper. It returns
// `ipld.Node` with the `dataSize` (that will be used to keep track of
// the DAG file size). The size of the data is computed here because
// after that it will be hidden by `NewLeafNode` inside a generic
// `ipld.Node` representation.
    @Nullable
    public Pair<Node, Integer> NewLeafDataNode(@NonNull UnixfsProtos.Data.DataType dataType) {

        byte[] fileData = Next();
        if (fileData != null) {
            int dataSize = fileData.length;

            // Create a new leaf node containing the file chunk data.
            Node node = NewLeafNode(fileData, dataType);

            return Pair.create(node, dataSize);
        }
        return null;
    }

    private Node NewLeafNode(byte[] data, UnixfsProtos.Data.DataType fsNodeType) {


        if (data.length > Settings.BlockSizeLimit) {
            throw new RuntimeException("ErrSizeLimitExceeded"); // TODO
        }

        if (rawLeaves) {
            // Encapsulate the data in a raw node.
            if (builder == null) {
                return RawNode.NewRawNode(data);
            }
            return RawNode.NewRawNodeWPrefix(data, builder);
        }

        // Encapsulate the data in UnixFS node (instead of a raw node).
        FSNodeOverDag fsNodeOverDag = NewFSNodeOverDag(fsNodeType);
        fsNodeOverDag.SetFileData(data);

        return fsNodeOverDag.Commit();


    }

    private byte[] Next() {
        return splitter.NextBytes();
    }

    public void FillNodeLayer(@NonNull FSNodeOverDag node) {

        while ((node.NumChildren() < Settings.DefaultLinksPerBlock) && !Done()) {
            Pair<Node, Integer> result = NewLeafDataNode(UnixfsProtos.Data.DataType.Raw);
            if (result != null) {
                node.AddChild(result.first, result.second, this);
            }
        }
        node.Commit();
    }

    public void Add(@NonNull Node node) {
        dagService.Add(null, node);
    }

    public boolean Done() {
        return splitter.Done();
    }

    public static class FSNodeOverDag {
        private final ProtoNode dag;
        private final FSNode file;

        private FSNodeOverDag(@NonNull ProtoNode protoNode, @NonNull FSNode fsNode, @NonNull Builder builder) {
            dag = protoNode;
            file = fsNode;
            dag.SetCidBuilder(builder);
        }


        // NumChildren returns the number of children of the `ft.FSNode`.
        int NumChildren() {
            return file.NumChildren();
        }

        public void AddChild(@NonNull Node child, long fileSize, @NonNull DagBuilderHelper dagBuilderHelper) {

            dag.AddNodeLink("", child);
            file.AddBlockSize(fileSize);

            dagBuilderHelper.Add(child);
        }

        public Node Commit() {
            byte[] fileData = file.GetBytes();
            dag.SetData(fileData);
            return dag;
        }

        public void SetFileData(byte[] data) {
            file.SetData(data);
        }

        public long FileSize() {
            return file.FileSize();
        }
    }
}
