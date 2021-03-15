package io.ipfs.bitswap.decision;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Set;

import io.Closeable;
import io.ipfs.cid.Cid;
import io.ipfs.format.Blockstore;

public class BlockStoreManager {
    @NonNull
    private final Blockstore blockstore;
    private final int workerCount;

    public BlockStoreManager(@NonNull Blockstore blockstore, int workerCount) {
        this.blockstore = blockstore;
        this.workerCount = workerCount;
    }

    public static BlockStoreManager NewBlockStoreManager(@NonNull Blockstore blockstore, int workerCount) {
        return new BlockStoreManager(blockstore, workerCount);
    }

    public HashMap<Cid, Integer> getBlockSizes(@NonNull Closeable ctx, @NonNull Set<Cid> wantKs) {

        HashMap<Cid, Integer> blocksizes = new HashMap<>();
        for (Cid cid : wantKs) {
            int size = blockstore.GetSize(cid);
            if (size > 0) {
                blocksizes.put(cid, size);
            }
        }
        return blocksizes;
    }
}
