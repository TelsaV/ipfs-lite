package io.ipfs.utils;

import androidx.annotation.NonNull;

import java.util.List;

import io.ipfs.cid.Cid;
import io.ipfs.format.Block;
import io.ipfs.format.Blockstore;
import io.ipfs.format.Metrics;

public class MetricsBlockstore implements Blockstore {

    private final Blockstore blockstore;
    private final Metrics metrics;

    public MetricsBlockstore(@NonNull Blockstore blockstore, @NonNull Metrics metrics) {
        this.blockstore = blockstore;
        this.metrics = metrics;
    }

    @Override
    public Block Get(@NonNull Cid cid) {
        Block block = blockstore.Get(cid);
        if (block != null) {
            metrics.seeding(block.RawData().length);
        }
        return block;
    }

    @Override
    public void DeleteBlock(@NonNull Cid cid) {
        blockstore.DeleteBlock(cid);
    }

    @Override
    public void DeleteBlocks(@NonNull List<Cid> cids) {
        blockstore.DeleteBlocks(cids);
    }

    @Override
    public void Put(@NonNull Block block) {
        metrics.leeching(block.RawData().length);
        blockstore.Put(block);
    }

    @Override
    public int GetSize(@NonNull Cid cid) {
        return blockstore.GetSize(cid);
    }
}
