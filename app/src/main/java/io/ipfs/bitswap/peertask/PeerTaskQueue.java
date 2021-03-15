package io.ipfs.bitswap.peertask;

import androidx.annotation.NonNull;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import io.ipfs.cid.Cid;
import io.libp2p.peer.ID;

public class PeerTaskQueue {
    public final Hook peerAdded;
    public final Hook peerRemoved;
    private final Map<ID, PeerTracker> peerTrackers = new HashMap<>();
    private final PriorityQueue<PeerTracker> pQueue;
    private TaskMerger taskMerger;
    private boolean ignoreFreezing;

    public PeerTaskQueue(@NonNull Hook peerAdded, @NonNull Hook peerRemoved) {
        taskMerger = TaskMerger.getDefault();
        this.peerAdded = peerAdded;
        this.peerRemoved = peerRemoved;
        this.pQueue = new PriorityQueue<>(new Comparator<PeerTracker>() {


            @Override
            public int compare(PeerTracker pa, PeerTracker pb) {

                // PeerCompare implements pq.ElemComparator
                // returns 1 if peer 'a' has higher priority than peer 'b'

                // having no pending tasks means lowest priority
                int paPending = pa.pendingTasks.size();
                int pbPending = pb.pendingTasks.size();
                if (paPending == 0) {
                    return -1;
                }
                if (pbPending == 0) {
                    return 1;
                }

                // Frozen peers have lowest priority
                if (pa.freezeVal > pb.freezeVal) {
                    return -1;
                }
                if (pa.freezeVal < pb.freezeVal) {
                    return 1;
                }

                // If each peer has an equal amount of work in its active queue, choose the
                // peer with the most amount of work pending
                if (pa.activeWork == pb.activeWork) {
                    return Integer.compare(paPending, pbPending);
                }

                // Choose the peer with the least amount of work in its active queue.
                // This way we "keep peers busy" by sending them as much data as they can
                // process.
                return Integer.compare(pb.activeWork, pa.activeWork);
            }
        });
    }

    // todo better optimization for synchronized
    public synchronized void PushTasks(@NonNull ID to, @NonNull List<Task> tasks) {

        PeerTracker peerTracker = peerTrackers.get(to);
        if (peerTracker == null) {
            peerTracker = new PeerTracker(to, taskMerger);
            pQueue.offer(peerTracker);
            peerTrackers.put(to, peerTracker);
            peerAdded.hook(to);
        }

        peerTracker.PushTasks(tasks);
        pQueue.remove(peerTracker);
        pQueue.offer(peerTracker);
    }

    public void TaskMerger(@NonNull TaskMerger taskMerger) {
        this.taskMerger = taskMerger;
    }

    public void IgnoreFreezing(boolean value) {
        ignoreFreezing = value;
    }

    public synchronized void Remove(@NonNull Cid cid, @NonNull ID peer) {
        PeerTracker peerTracker = peerTrackers.get(peer);
        if (peerTracker != null) {
            if (peerTracker.Remove(cid)) {
                // we now also 'freeze' that partner. If they sent us a cancel for a
                // block we were about to send them, we should wait a short period of time
                // to make sure we receive any other in-flight cancels before sending
                // them a block they already potentially have
                if (!ignoreFreezing) {
                    /* TODO not yet implemented
                    if(!peerTracker.IsFrozen()) {
                        frozenPeers[p] = struct{}{}
                    }

                    peerTracker.Freeze() */
                }
                pQueue.remove(peerTracker);
                pQueue.offer(peerTracker);
            }
        }
    }


    public interface Hook {
        void hook(@NonNull ID peer);
    }
}
