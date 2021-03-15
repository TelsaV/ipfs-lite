package io.ipfs.bitswap;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.Closeable;
import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.bitswap.decision.Engine;
import io.ipfs.bitswap.internal.Pubsub;
import io.ipfs.bitswap.internal.SessionInterestManager;
import io.ipfs.bitswap.message.BitSwapMessage;
import io.ipfs.bitswap.network.Receiver;
import io.ipfs.cid.Cid;
import io.ipfs.exchange.Interface;
import io.ipfs.format.Block;
import io.ipfs.format.Blockstore;
import io.libp2p.peer.ID;

public class BitSwap implements Interface, Receiver {


    private static final String TAG = BitSwap.class.getSimpleName();
    // maxBlockSizeReplaceHasWithBlock is the maximum size of the block in
    // bytes up to which we will replace a want-have with a want-block


    private final Engine engine;
    private final SessionInterestManager sim;
    private final Blockstore blockstore;
    private final Pubsub notify;

    public BitSwap(@NonNull Blockstore blockstore, @NonNull BitSwapNetwork network) {
        this.blockstore = blockstore;
        engine = Engine.NewEngine(blockstore, network.ConnectionManager(),
                IPFS.EngineBlockstoreWorkerCount, network.Self());
        notify = new Pubsub();
        sim = new SessionInterestManager();
    }

    public static Interface New(@NonNull Closeable closeable,
                                @NonNull BitSwapNetwork bitSwapNetwork,
                                @NonNull Blockstore blockstore) {


        BitSwap bs = new BitSwap(blockstore, bitSwapNetwork);
        bitSwapNetwork.SetDelegate(bs);
        return bs;
    }

    @Nullable
    @Override
    public Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid) {


        //Getter.SyncGetBlock(closeable, cid, );
        return notify.Subscribe(closeable, cid);
    }

    @Override
    public void ReceiveMessage(@NonNull Closeable closeable, @NonNull ID p, @NonNull BitSwapMessage incoming) {


        // This call records changes to wantlists, blocks received,
        // and number of bytes transfered.
        engine.MessageReceived(closeable, p, incoming);
        // TODO: this is bad, and could be easily abused.
        // Should only track *useful* messages in ledger

        /*
        if bs.wiretap != nil {
            bs.wiretap.MessageReceived(p, incoming)
        }*/

        List<Block> iblocks = incoming.Blocks();


        List<Cid> haves = incoming.Haves();
        List<Cid> dontHaves = incoming.DontHaves();
        if (iblocks.size() > 0 || haves.size() > 0 || dontHaves.size() > 0) {
            // Process blocks
            try {
                receiveBlocksFrom(closeable, p, iblocks, haves, dontHaves);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        }
    }


    // TODO: Some of this stuff really only needs to be done when adding a block
// from the user, not when receiving it from the network.
// In case you run `git blame` on this comment, I'll save you some time: ask
// @whyrusleeping, I don't know the answers you seek.
    private void receiveBlocksFrom(@NonNull Closeable closeable,
                                   @Nullable ID from,
                                   @NonNull List<Block> blks,
                                   @NonNull List<Cid> haves,
                                   @NonNull List<Cid> dontHaves) {

        if (closeable.isClosed()) {
            return;
        }

        List<Block> wanted = blks;

        // If blocks came from the network
        if (from != null) {
            Pair<List<Block>, List<Block>> result = sim.SplitWantedUnwanted(blks);
            wanted = result.first;
            List<Block> notWanted = result.second;
            for (Block nw : notWanted) {
                LogUtils.verbose(TAG, "[recv] block not in wantlist; cid= " +
                        nw.Cid().String() + " " + from.String());
            }
        }

        // Put wanted blocks into blockstore
        if (wanted.size() > 0) {
            for (Block block : wanted) {
                blockstore.Put(block);
            }
        }

        // NOTE: There exists the possiblity for a race condition here.  If a user
        // creates a node, then adds it to the dagservice while another goroutine
        // is waiting on a GetBlock for that object, they will receive a reference
        // to the same node. We should address this soon, but i'm not going to do
        // it now as it requires more thought and isnt causing immediate problems.
        List<Cid> allKs = new ArrayList<>();
        for (Block b : blks) {
            allKs.add(b.Cid());
        }

        // If the message came from the network
        if (from != null) {
            // Inform the PeerManager so that we can calculate per-peer latency
                /* maybe TODO
                combined := make([]cid.Cid, 0, len(allKs)+len(haves)+len(dontHaves))
                combined = append(combined, allKs...)
                combined = append(combined, haves...)
                combined = append(combined, dontHaves...)
                bs.pm.ResponseReceived(from, combined) */
        }

        // Send all block keys (including duplicates) to any sessions that want them.
        // (The duplicates are needed by sessions for accounting purposes)
        // TODO bs.sm.ReceiveFrom(closeable, from, allKs, haves, dontHaves);

        // Send wanted blocks to decision engine
        // TODO  bs.engine.ReceiveFrom(from, wanted, haves);

        // Publish the block to any Bitswap clients that had requested blocks.
        // (the sessions use this pubsub mechanism to inform clients of incoming
        // blocks)
        for (Block b : wanted) {
            notify.Publish(b);
        }

        // If the reprovider is enabled, send wanted blocks to reprovider
            /* maybe todo
            if bs.provideEnabled {
                for _, blk := range wanted {
                    select {
                        case bs.newBlocks <- blk.Cid():
                            // send block off to be reprovided
                        case <-bs.process.Closing():
                            return bs.process.Close()
                    }
                }
            } */

            /*
            if( from != null) {
                for _, b := range wanted {
                    log.Debugw("Bitswap.GetBlockRequest.End", "cid", b.Cid())
                }
            }*/


    }

    @Override
    public void ReceiveError(@NonNull ID from, @NonNull String error) {
        LogUtils.error(TAG, from.String() + " " + error);
    }

    @Override
    public void PeerConnected(@NonNull ID peer) {

    }

    @Override
    public void PeerDisconnected(@NonNull ID peer) {

    }


}

