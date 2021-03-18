package io.ipfs.bitswap.internal;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.Closeable;
import io.LogUtils;
import io.ipfs.ClosedException;
import io.ipfs.bitswap.BitSwapNetwork;
import io.ipfs.bitswap.message.MessageWriter;
import io.ipfs.cid.Cid;
import io.ipfs.format.Block;
import io.libp2p.peer.ID;
import io.libp2p.routing.Providers;

public class PeerManager {
    public static final int PROVIDERS = 50;
    private static final String TAG = PeerManager.class.getSimpleName();
    private static final ExecutorService WANTS = Executors.newFixedThreadPool(4);
    private static final ExecutorService PROVS = Executors.newFixedThreadPool(16);
    private final ConcurrentHashMap<Cid, ConcurrentLinkedQueue<ID>> searches = new ConcurrentHashMap<>();
    private final CopyOnWriteArraySet<ID> faulty = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<ID> priority = new CopyOnWriteArraySet<>();
    private final BitSwapNetwork network;
    private final Pubsub notify = new Pubsub();

    public PeerManager(@NonNull BitSwapNetwork network) {
        this.network = network;
    }

    public void HaveResponseReceived(@NonNull ID peer, @NonNull List<Cid> cids) {
        for (Cid cid : cids) {
            ConcurrentLinkedQueue<ID> queue = searches.get(cid);
            if (queue != null) {
                queue.add(peer);
                faulty.remove(peer);
                priority.add(peer);
            }
        }
    }

    public void reset() {
        searches.clear();
        priority.clear();
        notify.clear();
    }

    public void runFindProviders(@NonNull Closeable closeable, @NonNull Cid cid) throws ClosedException {


        network.FindProvidersAsync(new Providers() {
            @Override
            public void Peer(@NonNull String pid) {
                PROVS.execute(() -> {
                    try {
                        LogUtils.error(TAG, "Found Provider " + pid);
                        ID peer = new ID(pid);

                        if (network.ConnectTo(closeable, peer, true)) {
                            LogUtils.error(TAG, "Success Provider Connection " + pid);
                            try {
                                runWriteMessage(closeable, peer, cid);
                            } catch (ClosedException closedException) {
                                throw closedException;
                            } catch (Throwable throwable) {
                                LogUtils.error(TAG, throwable);
                                priority.remove(peer);
                                faulty.add(peer);
                            }
                        }
                    } catch (ClosedException ignore) {
                        // ignore
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                });
            }

            @Override
            public boolean isClosed() {
                return closeable.isClosed();
            }
        }, cid, PROVIDERS);


    }

    public void runWriteMessage(@NonNull Closeable closeable, @NonNull ID peer, @NonNull Cid cid) throws ClosedException {
        // TODO think if protext
        if (network.ConnectTo(closeable, peer, false)) {
            MessageWriter messageQueue = new MessageWriter();
            messageQueue.AddWants(Collections.singletonList(cid), Collections.singletonList(cid));
            messageQueue.sendMessage(closeable, network, peer);
        } else {
            // TODO think what to do
        }

    }

    public void runWantHaves(@NonNull Closeable closeable, @NonNull Cid cid) throws ClosedException {

        // TODO event for new connections add them to peers

        List<ID> handled = new ArrayList<>();
        do {
            LogUtils.error(TAG, "RunWantHaves " + cid.String());

            ConcurrentLinkedQueue<ID> queue = searches.get(cid);
            if (queue == null) {
                return;
            }

            for (ID peer : priority) {
                if (!handled.contains(peer)){
                    queue.offer(peer);
                }
            }

            List<ID> cons = network.ConnectionManager().getPeers();
            for (ID con : cons) {
                if (!faulty.contains(con) && !handled.contains(con) && !priority.contains(con)) {
                    queue.offer(con);
                }
            }

        } while (runWantHaves(closeable, cid, handled));


    }

    private boolean runWantHaves(@NonNull Closeable closeable, @NonNull Cid cid,
                                 @NonNull List<ID> handled) throws ClosedException {


        ConcurrentLinkedQueue<ID> queue = searches.get(cid);
        if (queue == null) {
            return false;
        }
        boolean run = false;
        while (!queue.isEmpty()) {

            ID peer = queue.poll();
            if (peer == null) {
                break;
            }
            handled.add(peer);
            LogUtils.error(TAG, "RunWantHaves try  " + peer.String());

            try {
                runWriteMessage(closeable, peer, cid);
            } catch (ClosedException closedException) {
                throw closedException;
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
                priority.remove(peer);
                faulty.add(peer);
            }

            run = true;
            LogUtils.error(TAG, "RunWantHaves finish try  " + peer.String());
        }
        return run;
    }


    public Block GetBlock(@NonNull Closeable closeable, @NonNull Cid cid) {

        try {
            LogUtils.error(TAG, "GetBlock " + cid.String());
            ConcurrentLinkedQueue<ID> queue = searches.get(cid);
            AtomicBoolean done = new AtomicBoolean(false);
            if (queue == null) {

                Closeable doneClose = () -> closeable.isClosed() || done.get();
                searches.put(cid, new ConcurrentLinkedQueue<>());
                Executors.newSingleThreadExecutor().execute(() -> {
                    try {
                        runFindProviders(doneClose, cid);
                    } catch (ClosedException closedException) {
                        notify.Release(cid);
                    }
                });

                Executors.newSingleThreadExecutor().execute(() -> {
                    try {
                        runWantHaves(doneClose, cid);
                    } catch (ClosedException closedException) {
                        notify.Release(cid);
                    }
                });
            }
            Block block = notify.Subscribe(cid);
            done.set(true);
            return block;
        } finally {
            LogUtils.error(TAG, "Finish " + cid.String());
            searches.remove(cid);
        }

    }

    public void BlockReceived(@NonNull ID peer, @NonNull Block block) {

        Cid cid = block.Cid();
        ConcurrentLinkedQueue<ID> queue = searches.get(cid);
        if (queue != null) {
            notify.Publish(block);
            faulty.remove(peer);
            priority.add(peer);
            queue.clear();

        }
    }
}
