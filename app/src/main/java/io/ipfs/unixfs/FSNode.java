package io.ipfs.unixfs;

import androidx.annotation.NonNull;

import io.ipfs.format.Node;
import io.ipfs.format.ProtoNode;
import io.ipfs.format.RawNode;
import unixfs.pb.UnixfsProtos;

public class FSNode {
    private static final String TAG = FSNode.class.getSimpleName();
    private final UnixfsProtos.Data data;
    private FSNode(byte[]b){
        try {
            data = UnixfsProtos.Data.parseFrom(b);
        } catch (Throwable throwable){
            throw new RuntimeException();
        }
    }

    public byte[] Data(){
        return data.getData().toByteArray();
    }

    public static FSNode FSNodeFromBytes(byte[] data) {
        return new FSNode(data);
    }

    public UnixfsProtos.Data.DataType Type() {
        return data.getType();
    }

    public long FileSize() {
        return data.getFilesize();
    }



    // ReadUnixFSNodeData extracts the UnixFS data from an IPLD node.
// Raw nodes are (also) processed because they are used as leaf
// nodes containing (only) UnixFS data.
    public static byte[] ReadUnixFSNodeData(@NonNull Node node)  {

        if(node instanceof ProtoNode){
            FSNode fsNode = FSNodeFromBytes(node.getData());
            switch( fsNode.Type() ) {
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
        } else if(node instanceof RawNode){
            return node.RawData();
        } else {
            throw new RuntimeException("not supported type");
        }

    }

    public long BlockSize(int i) {
        return data.getBlocksizes(i);
    }

    public static FSNode ExtractFSNode(@NonNull Node node) {
        if(node instanceof  ProtoNode){
            return FSNodeFromBytes(node.getData());
        }
        throw new RuntimeException("expected a ProtoNode as internal node");
                
    }

    public int NumChildren() {
        return data.getBlocksizesCount();
    }


}


