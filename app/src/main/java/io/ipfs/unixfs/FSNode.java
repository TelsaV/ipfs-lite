package io.ipfs.unixfs;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import io.ipfs.format.Node;
import io.ipfs.format.ProtoNode;
import io.ipfs.format.RawNode;
import unixfs.pb.UnixfsProtos;

public class FSNode {
    private static final String TAG = FSNode.class.getSimpleName();
    private UnixfsProtos.Data data;

    private FSNode(@NonNull UnixfsProtos.Data.DataType dataType) {
        data = UnixfsProtos.Data.newBuilder().setType(dataType).
                setFilesize(0L).build();
    }

    private FSNode(byte[] content) {
        try {
            data = UnixfsProtos.Data.parseFrom(content);
        } catch (Throwable throwable) {
            throw new RuntimeException();
        }
    }


    // NewFSNode creates a new FSNode structure with the given `dataType`.
//
// It initializes the (required) `Type` field (that doesn't have a `Set()`
// accessor so it must be specified at creation), otherwise the `Marshal()`
// method in `GetBytes()` would fail (`required field "Type" not set`).
//
// It also initializes the `Filesize` pointer field to ensure its value
// is never nil before marshaling, this is not a required field but it is
// done to be backwards compatible with previous `go-ipfs` versions hash.
// (If it wasn't initialized there could be cases where `Filesize` could
// have been left at nil, when the `FSNode` was created but no data or
// child nodes were set to adjust it, as is the case in `NewLeaf()`.)
    public static FSNode NewFSNode(@NonNull UnixfsProtos.Data.DataType dataType) {
        return new FSNode(dataType);
    }

    private void UpdateFilesize(long filesize) {
        long previous = data.getFilesize();
        data = data.toBuilder().setFilesize(previous + filesize).build();
    }


    public static FSNode FSNodeFromBytes(byte[] data) {
        return new FSNode(data);
    }

    // ReadUnixFSNodeData extracts the UnixFS data from an IPLD node.
// Raw nodes are (also) processed because they are used as leaf
// nodes containing (only) UnixFS data.
    public static byte[] ReadUnixFSNodeData(@NonNull Node node) {

        if (node instanceof ProtoNode) {
            FSNode fsNode = FSNodeFromBytes(node.getData());
            switch (fsNode.Type()) {
                case File:
                case Raw:
                    return fsNode.Data();
                // Only leaf nodes (of type `Data_Raw`) contain data but due to a
                // bug the `Data_File` type (normally used for internal nodes) is
                // also used for leaf nodes, so both types are accepted here
                // (see the `balanced` package for more details).
                default:
                    throw new RuntimeException("found %s node in unexpected place " +
                            fsNode.Type().name());
            }
        } else if (node instanceof RawNode) {
            return node.RawData();
        } else {
            throw new RuntimeException("not supported type");
        }

    }

    public static FSNode ExtractFSNode(@NonNull Node node) {
        if (node instanceof ProtoNode) {
            return FSNodeFromBytes(node.getData());
        }
        throw new RuntimeException("expected a ProtoNode as internal node");

    }

    public byte[] Data() {

        // todo check
        return data.getData().toByteArray();
    }

    public UnixfsProtos.Data.DataType Type() {
        return data.getType();
    }

    public long FileSize() {
        return data.getFilesize();
    }

    public long BlockSize(int i) {
        return data.getBlocksizes(i);
    }

    public int NumChildren() {
        return data.getBlocksizesCount();
    }


    public void AddBlockSize(long size) {
        UpdateFilesize(size);
        data = data.toBuilder().addBlocksizes(size).build();
    }

    public byte[] GetBytes() {
        return data.toByteArray(); // TODO check

    }

    public void SetData(byte[] bytes) {
        UpdateFilesize(bytes.length - Data().length);
        data = data.toBuilder().setData(ByteString.copyFrom(bytes)).build();
    }
}


