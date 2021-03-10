package io.ipfs.utils;

import androidx.annotation.NonNull;

import io.ipfs.Closeable;
import io.ipfs.LogUtils;
import io.ipfs.Storage;
import io.ipfs.blockservice.BlockService;
import io.ipfs.blockstore.Blockstore;
import io.ipfs.cid.Prefix;
import io.ipfs.exchange.Interface;
import io.ipfs.format.Node;
import io.ipfs.merkledag.DagService;
import io.ipfs.multihash.Multihash;
import io.ipfs.offline.Exchange;
import io.ipfs.path.Path;
import io.ipfs.unixfs.Directory;

public class Stream {


    private static final String TAG = Stream.class.getSimpleName();

    public static Adder getFileAdder(@NonNull Storage storage, @NonNull Closeable closeable) {


        Blockstore bs = Blockstore.NewBlockstore(storage);
        Interface exchange = new Exchange(bs);
        BlockService blockservice = BlockService.New(bs, exchange);
        DagService dagService = new DagService(blockservice);
        Adder fileAdder = Adder.NewAdder(closeable, dagService);

        Prefix prefix = Node.PrefixForCidVersion(1);

        prefix.MhType = Multihash.Type.sha2_256.index;
        prefix.MhLength = -1;

        fileAdder.Chunker = "size-262144";
        fileAdder.RawLeaves = false;
        fileAdder.NoCopy = false;
        fileAdder.CidBuilder = prefix;


        return fileAdder;
    }

    public static boolean IsDir(@NonNull Storage storage,
                                @NonNull Closeable closeable,
                                @NonNull String path) {

        Blockstore bs = Blockstore.NewBlockstore(storage);
        Interface exchange = new Exchange(bs);
        BlockService blockservice = BlockService.New(bs, exchange);
        DagService dagService = new DagService(blockservice);

        io.ipfs.format.Node dagnode = Resolver.ResolveNode(closeable, dagService, Path.New(path));
        try {
            Directory dir = Directory.NewDirectoryFromNode(dagService, dagnode);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            return false;
        }
        return true;
    }

    public static String CreateEmptyDir(@NonNull Storage storage, @NonNull Closeable closeable) {

        Adder fileAdder = getFileAdder(storage, closeable);

        Node nd = fileAdder.CreateEmptyDir();
        return nd.Cid().String();
    }
}
