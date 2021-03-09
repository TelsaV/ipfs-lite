package io.ipfs.merkledag;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.ipfs.Closeable;
import io.ipfs.blocks.Block;
import io.ipfs.blockservice.BlockService;
import io.ipfs.cid.Cid;
import io.ipfs.exchange.NodeGetter;
import io.ipfs.format.Decoder;
import io.ipfs.format.Node;

public class DagService implements NodeGetter {
    private final BlockService blockservice;
    public DagService(@NonNull BlockService blockService){
        this.blockservice = blockService;
    }

    @Override
    @Nullable
    public Node Get(@NonNull Closeable closeable, @NonNull Cid cid) {

            Block b = blockservice.GetBlock(closeable, cid);
            if(b == null) {
                return null;
            }
            return Decoder.Decode(b);
    }

}
