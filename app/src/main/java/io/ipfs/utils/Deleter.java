package io.ipfs.utils;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.ipfs.Storage;
import io.ipfs.LogUtils;
import io.ipfs.Closeable;
import io.ipfs.blockservice.BlockService;
import io.ipfs.blockstore.Blockstore;
import io.ipfs.cid.Cid;
import io.ipfs.exchange.Interface;
import io.ipfs.merkledag.DagService;
import io.ipfs.offline.Exchange;
import io.ipfs.path.Path;

public class Deleter {
    private static final String TAG = Deleter.class.getSimpleName();

    public static void rm(@NonNull Storage storage, @NonNull String cid, boolean recursively){
        // TODO
        try {

            Cid m = Cid.Decode(cid);
            LogUtils.error(TAG, m.String());

            Closeable closeable = () -> false;
            Blockstore bs = Blockstore.NewBlockstore(storage);
            Interface rem = new Exchange(bs);
            BlockService blockservice = BlockService.New(bs, rem);
            DagService dags = new DagService(blockservice);
            io.ipfs.format.Node top = Resolver.ResolveNode(closeable, dags, Path.New(cid));

            List<Cid> cids = new ArrayList<>();
            if (recursively) {
                RefWriter rw = new RefWriter(closeable, dags, true, -1);

                rw.EvalRefs(top);
                cids.addAll(rw.getCids());
            }

            cids.add(top.Cid());
            bs.DeleteBlocks(cids);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }
}
