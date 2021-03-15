package io.ipfs.bitswap.internal;

import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.ipfs.format.Block;

public class SessionInterestManager {
    public Pair<List<Block>, List<Block>> SplitWantedUnwanted(@NonNull List<Block> blks) {

        List<Block> wantedBlks = new ArrayList<>(blks); // TODO set empty again
        List<Block> notWantedBlks = new ArrayList<>();
        /*
        sim.lk.RLock()
        defer sim.lk.RUnlock()


        // Get the wanted block keys as a set
        wantedKs := cid.NewSet()
        for _, b := range blks {
            c := b.Cid()
            // For each session that is interested in the key
            for ses := range sim.wants[c] {
                // If the session wants the key (rather than just being interested)
                if wanted, ok := sim.wants[c][ses]; ok && wanted {
                    // Add the key to the set
                    wantedKs.Add(c)
                }
            }
        }

        // Separate the blocks into wanted and unwanted
        wantedBlks := make([]blocks.Block, 0, len(blks))
        notWantedBlks := make([]blocks.Block, 0)
        for _, b := range blks {
            if wantedKs.Has(b.Cid()) {
                wantedBlks = append(wantedBlks, b)
            } else {
                notWantedBlks = append(notWantedBlks, b)
            }
        }*/
        return Pair.create(wantedBlks, notWantedBlks);

    }
}
