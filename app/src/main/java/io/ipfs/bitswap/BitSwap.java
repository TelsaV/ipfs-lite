package io.ipfs.bitswap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import io.Closeable;
import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.bitswap.decision.Engine;
import io.ipfs.bitswap.internal.PeerManager;
import io.ipfs.bitswap.message.BitSwapMessage;
import io.ipfs.cid.Cid;
import io.ipfs.exchange.Interface;
import io.ipfs.format.Block;
import io.ipfs.format.Blockstore;
import io.libp2p.peer.ID;

public class BitSwap implements Interface, Receiver {

    private static final String TAG = BitSwap.class.getSimpleName();

    private final Engine engine;
    private final Blockstore blockstore;
    private final PeerManager peerManager;


    public BitSwap(@NonNull Blockstore blockstore, @NonNull BitSwapNetwork network) {
        this.blockstore = blockstore;
        engine = Engine.NewEngine(blockstore, network, IPFS.EngineBlockstoreWorkerCount, network.Self());
        peerManager = new PeerManager(network);
    }

    public static Interface New(@NonNull BitSwapNetwork bitSwapNetwork, @NonNull Blockstore blockstore) {

        BitSwap bitSwap = new BitSwap(blockstore, bitSwapNetwork);
        bitSwapNetwork.SetDelegate(bitSwap);
        return bitSwap;
    }

    @Nullable
    @Override
    public Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid) {
        return peerManager.GetBlock(closeable, cid);
    }

    public void reset() {
        peerManager.reset();
    }

    @Override
    public void ReceiveMessage(@NonNull Closeable closeable,
                               @NonNull ID peer, @NonNull BitSwapMessage incoming) {


        engine.MessageReceived(closeable, peer, incoming);

        List<Block> blocks = incoming.Blocks();
        List<Cid> haves = incoming.Haves();
        if (blocks.size() > 0 || haves.size() > 0) {
            // Process blocks
            try {
                receiveBlocksFrom(closeable, peer, blocks, haves);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        }
    }


    private void receiveBlocksFrom(@NonNull Closeable closeable,
                                   @NonNull ID from,
                                   @NonNull List<Block> wanted,
                                   @NonNull List<Cid> haves) {

        if (closeable.isClosed()) {
            return;
        }

        // Put wanted blocks into block store
        if (wanted.size() > 0) {
            for (Block block : wanted) {
                blockstore.Put(block);
            }
        }

        // TODO check if necessary (what it is doing)
        engine.ReceiveFrom(closeable, from, wanted, haves);

        peerManager.HaveResponseReceived(from, haves);

        for (Block block : wanted) {
            peerManager.BlockReceived(from, block);
        }
    }

    @Override
    public void ReceiveError(@NonNull ID peer, @NonNull String error) {

        // TODO handle error
        LogUtils.error(TAG, peer.String() + " " + error);
    }


}

