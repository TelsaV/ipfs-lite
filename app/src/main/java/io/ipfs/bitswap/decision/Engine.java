package io.ipfs.bitswap.decision;

import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.Closeable;
import io.LogUtils;
import io.ipfs.bitswap.message.BitSwapMessage;
import io.ipfs.bitswap.peertask.PeerTaskQueue;
import io.ipfs.bitswap.peertask.Task;
import io.ipfs.bitswap.peertask.TaskMerger;
import io.ipfs.cid.Cid;
import io.ipfs.format.Blockstore;
import io.libp2p.host.PeerTagger;
import io.libp2p.peer.ID;
import io.protos.bitswap.BitswapProtos;

public class Engine {
    public static final int MaxBlockSizeReplaceHasWithBlock = 1024;
    private static final String TAG = Engine.class.getSimpleName();
    private static final int queuedTagWeight = 10;
    private final BlockStoreManager bsm;
    private final PeerTaskQueue peerRequestQueue;
    private final ID self;
    private final PeerTagger peerTagger;
    // maxBlockSizeReplaceHasWithBlock is the maximum size of the block in
    // bytes up to which we will replace a want-have with a want-block
    private final int maxBlockSizeReplaceHasWithBlock;
    private final List<Task> activeEntries = new ArrayList<>();
    private final String tagQueued = UUID.randomUUID().toString();
    private boolean sendDontHaves;

    private Engine(@NonNull Blockstore bs, @NonNull PeerTagger peerTagger, int bstoreWorkerCount,
                   @NonNull ID self, int maxReplaceSize) {
        this.bsm = BlockStoreManager.NewBlockStoreManager(bs, bstoreWorkerCount);
        this.peerTagger = peerTagger;
        this.self = self;
        this.sendDontHaves = true;
        this.maxBlockSizeReplaceHasWithBlock = maxReplaceSize;
        this.peerRequestQueue = new PeerTaskQueue(
                this::onPeerAdded,
                this::onPeerRemoved
        );
        peerRequestQueue.TaskMerger(new TaskMerger() {
            @Override
            public boolean HasNewInfo(@NonNull Task task, @NonNull List<Task> existing) {
                boolean haveSize = false;
                boolean isWantBlock = false;
                for (Task et : existing) {

                    TaskData etd = (TaskData) et.Data;
                    if (etd.HaveBlock) {
                        haveSize = true;
                    }

                    if (etd.IsWantBlock) {
                        isWantBlock = true;
                    }

                }
                // If there is no active want-block and the new task is a want-block,
                // the new task is better

                TaskData newTaskData = (TaskData) task.Data;
                if (!isWantBlock && newTaskData.IsWantBlock) {
                    return true;
                }

                // If there is no size information for the CID and the new task has
                // size information, the new task is better
                return !haveSize && newTaskData.HaveBlock;
            }

            @Override
            public void Merge(@NonNull Task task, @NonNull Task existing) {
                TaskData newTask = (TaskData) task.Data;
                TaskData existingTask = (TaskData) existing.Data;

                // If we now have block size information, update the task with
                // the new block size
                if (!existingTask.HaveBlock && newTask.HaveBlock) {
                    existingTask.HaveBlock = newTask.HaveBlock;
                    existingTask.BlockSize = newTask.BlockSize;
                }

                // If replacing a want-have with a want-block
                if (!existingTask.IsWantBlock && newTask.IsWantBlock) {
                    // Change the type from want-have to want-block
                    existingTask.IsWantBlock = true;
                    // If the want-have was a DONT_HAVE, or the want-block has a size
                    if (!existingTask.HaveBlock || newTask.HaveBlock) {
                        // Update the entry size
                        existingTask.HaveBlock = newTask.HaveBlock;
                        existing.Work = task.Work;
                    }
                }

                // If the task is a want-block, make sure the entry size is equal
                // to the block size (because we will send the whole block)
                if (existingTask.IsWantBlock && existingTask.HaveBlock) {
                    existing.Work = existingTask.BlockSize;
                }
            }
        });
        peerRequestQueue.IgnoreFreezing(true);
    }

    // NewEngine creates a new block sending engine for the given block store
    public static Engine NewEngine(@NonNull Blockstore bs, @NonNull PeerTagger peerTagger,
                                   int bstoreWorkerCount, @NonNull ID self) {
        return new Engine(bs, peerTagger, bstoreWorkerCount, self, MaxBlockSizeReplaceHasWithBlock);
        // TODO
        //return newEngine(bs, bstoreWorkerCount, peerTagger, self, maxBlockSizeReplaceHasWithBlock, scoreLedger)
    }

    public void onPeerRemoved(@NonNull ID p) {
        peerTagger.UntagPeer(p, tagQueued);
    }

    public void onPeerAdded(@NonNull ID p) {
        peerTagger.TagPeer(p, tagQueued, queuedTagWeight);
    }

    // Split the want-have / want-block entries from the cancel entries
    public Pair<List<BitSwapMessage.Entry>, List<BitSwapMessage.Entry>> splitWantsCancels(
            @NonNull List<BitSwapMessage.Entry> es) {
        List<BitSwapMessage.Entry> wants = new ArrayList<>();
        List<BitSwapMessage.Entry> cancels = new ArrayList<>();
        for (BitSwapMessage.Entry et : es) {
            if (et.Cancel) {
                cancels.add(et);
            } else {
                wants.add(et);
            }
        }
        return Pair.create(wants, cancels);
    }

    // SetSendDontHaves indicates what to do when the engine receives a want-block
    // for a block that is not in the blockstore. Either
// - Send a DONT_HAVE message
// - Simply don't respond
// Older versions of Bitswap did not respond, so this allows us to simulate
// those older versions for testing.
    public void SetSendDontHaves(boolean send) {
        sendDontHaves = send;
    }

    public void MessageReceived(@NonNull Closeable ctx, @NonNull ID peer, @NonNull BitSwapMessage m) {

        List<BitSwapMessage.Entry> entries = m.Wantlist();

        if (entries.size() > 0) {
            for (BitSwapMessage.Entry et : entries) {
                if (!et.Cancel) {

                    if (et.WantType == BitswapProtos.Message.Wantlist.WantType.Have) {
                        LogUtils.verbose(TAG,
                                "Bitswap engine <- want-have" +
                                        "  local " + self.String() + " from " + peer.String()
                                        + " cid " + et.Cid.String());
                    } else {
                        LogUtils.verbose(TAG,
                                "Bitswap engine <- want-block" +
                                        "  local " + self.String() + " from " + peer.String()
                                        + " cid " + et.Cid.String());
                    }
                }
            }
        }

        if (m.Empty()) {
            LogUtils.info(TAG, "received empty message from " + peer);
        }


        boolean newWorkExists = false;
        /* TODO
        defer func() {
            if(newWorkExists) {
                signalNewWork();
            }
        }()*/

        // Get block sizes
        Pair<List<BitSwapMessage.Entry>, List<BitSwapMessage.Entry>> result = splitWantsCancels(entries);
        List<BitSwapMessage.Entry> wants = result.first;
        List<BitSwapMessage.Entry> cancels = result.second;

        Set<Cid> wantKs = new HashSet<>();
        for (BitSwapMessage.Entry entry : wants) {
            wantKs.add(entry.Cid);
        }


        HashMap<Cid, Integer> blockSizes = bsm.getBlockSizes(ctx, wantKs);


        /* TODO

        // Get the ledger for the peer
        l := e.findOrCreate(p)
        l.lk.Lock()
        defer l.lk.Unlock()


        // If the peer sent a full wantlist, replace the ledger's wantlist
        if( m.Full() ) {
            l.wantList = wl.New()
        }
        */


        // Remove cancelled blocks from the queue
        for (BitSwapMessage.Entry entry : cancels) {
            LogUtils.verbose(TAG, "Bitswap engine <- cancel " + " local " +
                    self + " from " + peer.String() + " cid " + entry.Cid.String());
            // TODO if l.CancelWant(entry.Cid) {
            peerRequestQueue.Remove(entry.Cid, peer);
            //}
        }


        for (BitSwapMessage.Entry entry : wants) {
            // For each want-have / want-block

            Cid c = entry.Cid;
            Integer blockSize = blockSizes.get(entry.Cid);


            // Add each want-have / want-block to the ledger
            // TODO l.Wants(c, entry.Priority, entry.WantType);

            // If the block was not found
            if (blockSize == null) {
                // log.Debugw("Bitswap engine: block not found", "local", e.self, "from", p, "cid", entry.Cid, "sendDontHave", entry.SendDontHave);

                // Only add the task to the queue if the requester wants a DONT_HAVE
                if (sendDontHaves && entry.SendDontHave) {
                    newWorkExists = true;
                    boolean isWantBlock = false;
                    if (entry.WantType == BitswapProtos.Message.Wantlist.WantType.Block) {
                        isWantBlock = true;
                    }

                    Task task = new Task(c, entry.Priority, BitSwapMessage.BlockPresenceSize(c),
                            new TaskData(0, false, isWantBlock, entry.SendDontHave));
                    activeEntries.add(task);

                }
            } else {
                // The block was found, add it to the queue
                newWorkExists = true;

                boolean isWantBlock = sendAsBlock(entry.WantType, blockSize);

                LogUtils.verbose(TAG,
                        "Bitswap engine: block found" + "local" + self.String() +
                                "from " + peer + " cid " + entry.Cid.String()
                                + " isWantBlock " + isWantBlock);

                // entrySize is the amount of space the entry takes up in the
                // message we send to the recipient. If we're sending a block, the
                // entrySize is the size of the block. Otherwise it's the size of
                // a block presence entry.
                int entrySize = blockSize;
                if (!isWantBlock) {
                    entrySize = BitSwapMessage.BlockPresenceSize(c);
                }

                Task task = new Task(c, entry.Priority, entrySize,
                        new TaskData(blockSize, true, isWantBlock, entry.SendDontHave));
                activeEntries.add(task);

            }
        }

        // Push entries onto the request queue
        if (activeEntries.size() > 0) {
            peerRequestQueue.PushTasks(peer, activeEntries);
        }
    }

    private boolean sendAsBlock(BitswapProtos.Message.Wantlist.WantType wantType, Integer blockSize) {
        boolean isWantBlock = wantType == BitswapProtos.Message.Wantlist.WantType.Block;
        return isWantBlock || blockSize <= maxBlockSizeReplaceHasWithBlock;
    }
}
