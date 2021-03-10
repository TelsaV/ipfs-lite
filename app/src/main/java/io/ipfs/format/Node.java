package io.ipfs.format;

import androidx.annotation.Nullable;

import java.util.List;

import io.ipfs.blocks.Block;
import io.ipfs.cid.Builder;
import io.ipfs.cid.Cid;
import io.ipfs.cid.Prefix;
import io.ipfs.multihash.Multihash;

public interface Node extends Block {
    Prefix v0CidPrefix = new Prefix(
            Cid.DagProtobuf, -1, Multihash.Type.sha2_256.index, 0);
    Prefix v1CidPrefix = new Prefix(
            Cid.DagProtobuf, -1, Multihash.Type.sha2_256.index, 1);

    // PrefixForCidVersion returns the Protobuf prefix for a given CID version
    static Prefix PrefixForCidVersion(int version) {
        switch (version) {
            case 0:
                return v0CidPrefix;
            case 1:
                return v1CidPrefix;
            default:
                throw new RuntimeException("wrong version");
        }
    }

    static ProtoNode NodeWithData(byte[] data) {
        return new ProtoNode(data);
    }

    List<Link> getLinks();

    Cid Cid();

    byte[] getData();

    byte[] RawData();

    void SetCidBuilder(@Nullable Builder builder);

}
