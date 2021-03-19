package io.ipfs.bitswap;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.Closeable;
import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.bitswap.peertask.PeerTaskQueue;
import io.ipfs.bitswap.peertask.Task;
import io.ipfs.bitswap.peertask.TaskMerger;
import io.ipfs.cid.Cid;
import io.ipfs.format.Block;
import io.ipfs.format.BlockStore;
import io.libp2p.peer.ID;
import io.protos.bitswap.BitswapProtos;

public class Engine {
    public static final int MaxBlockSizeReplaceHasWithBlock = 1024;
    private static final String TAG = Engine.class.getSimpleName();
    private final BlockStore blockstore;
    private final PeerTaskQueue peerRequestQueue;
    private final ID self;


    // maxBlockSizeReplaceHasWithBlock is the maximum size of the block in
    // bytes up to which we will replace a want-have with a want-block
    private final int maxBlockSizeReplaceHasWithBlock;


    private boolean sendDontHaves;
    @NonNull
    private final BitSwapNetwork network;

    private Engine(@NonNull BlockStore bs, @NonNull BitSwapNetwork network, int bstoreWorkerCount,
                   @NonNull ID self, int maxReplaceSize) {
        this.blockstore = bs;
        this.network = network;

        this.self = self;
        this.sendDontHaves = IPFS.SEND_DONT_HAVES;
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
    public static Engine NewEngine(@NonNull BlockStore bs, @NonNull BitSwapNetwork network,

                                   int bstoreWorkerCount, @NonNull ID self) {
        return new Engine(bs, network, bstoreWorkerCount, self, MaxBlockSizeReplaceHasWithBlock);
        // TODO
        //return newEngine(bs, bstoreWorkerCount, peerTagger, self, maxBlockSizeReplaceHasWithBlock, scoreLedger)
    }


    private BitSwapMessage createMessage(@NonNull List<Task> nextTasks, int pendingBytes) {

        // Create a new message
        BitSwapMessage msg = BitSwapMessage.New(false);

        LogUtils.verbose(TAG,
                "Bitswap process tasks" + " local " + self.String() +
                        " taskCount " + nextTasks.size());

        // Amount of data in the request queue still waiting to be popped
        msg.SetPendingBytes(pendingBytes);

        // Split out want-blocks, want-haves and DONT_HAVEs
        List<Cid> blockCids = new ArrayList<>();
        Map<Cid, TaskData> blockTasks = new HashMap<>();

        for (Task t : nextTasks) {
            Cid c = t.Topic;
            TaskData td = (TaskData) t.Data;
            if (td.HaveBlock) {
                if (td.IsWantBlock) {
                    blockCids.add(c);
                    blockTasks.put(c, td);
                } else {
                    // Add HAVES to the message
                    msg.AddHave(c);
                }
            } else {
                // Add DONT_HAVEs to the message
                msg.AddDontHave(c);
            }
        }

        // Fetch blocks from datastore

        Map<Cid, Block> blks = getBlocks(blockCids);
        for (Map.Entry<Cid, TaskData> entry : blockTasks.entrySet()) {
            Block blk = blks.get(entry.getKey());
            // If the block was not found (it has been removed)
            if (blk == null) {
                // If the client requested DONT_HAVE, add DONT_HAVE to the message
                if (entry.getValue().SendDontHave) {
                    msg.AddDontHave(entry.getKey());
                }
            } else {

                LogUtils.error(TAG, "Block added to message " + blk.Cid().String());

                // Add the block to the message
                // log.Debugf("  make evlp %s->%s block: %s (%d bytes)", e.self, p, c, len(blk.RawData()))
                msg.AddBlock(blk);
            }
        }
        return msg;

    }

    public void onPeerRemoved(@NonNull ID p) {

    }

    public void onPeerAdded(@NonNull ID p) {

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

        final List<Task> activeEntries = new ArrayList<>(); // TODO it was global
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


        HashMap<Cid, Integer> blockSizes = getBlockSizes(ctx, wantKs);


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
                LogUtils.verbose(TAG,
                        "Bitswap engine: block not found" + " local " + self.String()
                                + " from " + peer.String() + " cid " + entry.Cid.String()
                                + " sendDontHave " + entry.SendDontHave);

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

            // TODO pendingBytes (replace with org code)
            BitSwapMessage msg = createMessage(activeEntries, 0);
            if (!msg.Empty()) {
                network.SendMessage(ctx, peer, msg);
            }

            // TODO peerRequestQueue.PushTasks(peer, activeEntries);
        }
    }

    private boolean sendAsBlock(BitswapProtos.Message.Wantlist.WantType wantType, Integer blockSize) {
        boolean isWantBlock = wantType == BitswapProtos.Message.Wantlist.WantType.Block;
        return isWantBlock || blockSize <= maxBlockSizeReplaceHasWithBlock;
    }


    // ReceiveFrom is called when new blocks are received and added to the block
// store, meaning there may be peers who want those blocks, so we should send
// the blocks to them.
//
// This function also updates the receive side of the ledger.
    public void ReceiveFrom(@NonNull Closeable ctx, @Nullable ID from, @NonNull List<Block> blks, @NonNull List<Cid> haves) {


        if (blks.size() == 0) {
            return;
        }

        if (from != null) {
            /* TODO
            l := e.findOrCreate(from);
            l.lk.Lock()

            // Record how many bytes were received in the ledger
            for _, blk := range blks {
                log.Debugw("Bitswap engine <- block", "local", e.self, "from", from, "cid", blk.Cid(), "size", len(blk.RawData()))
                e.scoreLedger.AddToReceivedBytes(l.Partner, len(blk.RawData()))
            }

            l.lk.Unlock()*/
        }

        // Get the size of each block
        Map<Cid, Integer> blockSizes = new HashMap<>();
        for (Block blk : blks) {
            blockSizes.put(blk.Cid(), blk.RawData().length);
        }


        // Check each peer to see if it wants one of the blocks we received
        boolean work = false;
        // TODO e.lock.RLock()

        // TODO for _, l := range e.ledgerMap {
        // TODO    l.lk.RLock()
        for (Block b : blks) {
            Cid k = b.Cid();

            // TODO  if entry, ok := l.WantListContains(k); ok {
            work = true;

            Integer blockSize = blockSizes.get(k);
            boolean isWantBlock = true; // TODO sendAsBlock(entry.WantType, blockSize);

            Integer entrySize = blockSize;
            if (!isWantBlock) {
                entrySize = BitSwapMessage.BlockPresenceSize(k);
            }

            Task task = new Task(k, 1, entrySize,
                    new TaskData(blockSize, true, isWantBlock, false));
            List<Task> activeEntries = Collections.singletonList(task);
            BitSwapMessage msg = createMessage(activeEntries, 0);
            if (!msg.Empty()) {
                network.SendMessage(ctx, from, msg);
            }

                    /* TODO
                    peerRequestQueue.PushTasks(l.Partner, peertask.Task{
                        Topic:    entry.Cid,
                                Priority: int(entry.Priority),
                                Work:     entrySize,
                                Data: &taskData{
                            BlockSize:    blockSize,
                                    HaveBlock:    true,
                                    IsWantBlock:  isWantBlock,
                                    SendDontHave: false,
                        },
                    })*/
        }
        // }
        // TODO l.lk.RUnlock()
        // TODO }
        // TODO e.lock.RUnlock()

        if (work) {
            // TODO    e.signalNewWork()
        }
    }

    public Map<Cid, Block> getBlocks(@NonNull List<Cid> cids) {
        Map<Cid, Block> blks = new HashMap<>();
        for (Cid c : cids) {
            Block block = blockstore.Get(c);
            if (block != null) {
                blks.put(c, block);
            }
        }
        return blks;
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
