package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.Closeable;
import io.LogUtils;
import io.ipfs.ClosedException;
import io.ipfs.cid.Cid;
import io.ipfs.format.Block;
import io.libp2p.peer.ID;
import io.libp2p.routing.Providers;

public class ContentManager {
    public static final int PROVIDERS = 10;
    private static final String TAG = ContentManager.class.getSimpleName();
    private static final ExecutorService WANTS = Executors.newFixedThreadPool(32);
    private final ConcurrentLinkedQueue<Cid> searches = new ConcurrentLinkedQueue<>();
    private final CopyOnWriteArraySet<ID> faulty = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<ID> priority = new CopyOnWriteArraySet<>();
    private final BitSwapNetwork network;
    private final Pubsub notify = new Pubsub();

    public ContentManager(@NonNull BitSwapNetwork network) {
        this.network = network;
    }

    public void HaveResponseReceived(@NonNull ID peer, @NonNull List<Cid> cids) {
        for (Cid cid : cids) {
            if (searches.contains(cid)) {
                LogUtils.error(TAG, "HaveResponseReceived " + cid.String());
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

    public void runWantHaves(@NonNull Closeable closeable, @NonNull Cid cid) throws ClosedException {

        // TODO event for new connections add them to peers

        CopyOnWriteArraySet<ID> handled = new CopyOnWriteArraySet<>();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                network.FindProvidersAsync(new Providers() {
                    @Override
                    public void Peer(@NonNull String pid) {
                        ID peer = new ID(pid);

                        if (!handled.contains(peer)) {
                            handled.add(peer);
                            WANTS.execute(() -> {
                                LogUtils.error(TAG, "Found New Provider " + pid);

                                try {
                                    if (network.ConnectTo(closeable, peer, true)) {
                                        LogUtils.error(TAG, "Success Provider Connection " + pid);

                                        MessageWriter.sendWantsMessage(closeable, network, peer,
                                                Collections.singletonList(cid));
                                    }
                                } catch (ClosedException ignore) {
                                    // ignore
                                } catch (Throwable throwable) {
                                    LogUtils.error(TAG, throwable);
                                    priority.remove(peer);
                                    faulty.add(peer);
                                }

                            });

                        }
                    }

                    @Override
                    public boolean isClosed() {
                        return closeable.isClosed();
                    }
                }, cid, PROVIDERS);
            } catch (ClosedException closedException) {
                // ignore here
            }
        });


        boolean hasRun;
        do {
            LogUtils.error(TAG, "RunWantHaves " + cid.String());

            hasRun = false;

            if (closeable.isClosed()) {
                throw new ClosedException();
            }

            for (ID peer : priority) {
                if (!handled.contains(peer)) {
                    try {
                        if (network.ConnectTo(closeable, peer, true)) {
                            MessageWriter.sendWantsMessage(closeable, network, peer,
                                    Collections.singletonList(cid));
                        } else {
                            // TODO think what to do
                        }
                    } catch (ClosedException closedException) {
                        throw closedException;
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                        priority.remove(peer);
                        faulty.add(peer);
                    } finally {
                        handled.add(peer);
                        hasRun = true;
                    }


                }
            }

            if (closeable.isClosed()) {
                throw new ClosedException();
            }

            if (!hasRun) {
                List<ID> cons = network.ConnectionManager().getPeers();
                for (ID peer : cons) {
                    if (!faulty.contains(peer) && !handled.contains(peer) && !priority.contains(peer)) {


                        try {
                            if (network.ConnectTo(closeable, peer, false)) {
                                MessageWriter.sendWantsMessage(closeable, network, peer,
                                        Collections.singletonList(cid));
                            } else {
                                // TODO think what to do
                            }
                        } catch (ClosedException closedException) {
                            throw closedException;
                        } catch (Throwable throwable) {
                            LogUtils.error(TAG, throwable);
                            priority.remove(peer);
                            faulty.add(peer);
                        } finally {
                            handled.add(peer);
                        }


                        // check priority after each run
                        break;

                    }
                }
            }

        } while (searches.contains(cid));


    }

    public Block GetBlock(@NonNull Closeable closeable, @NonNull Cid cid) {

        try {
            LogUtils.error(TAG, "GetBlock " + cid.String());
            if (!searches.contains(cid)) {
                searches.add(cid);
                Executors.newSingleThreadExecutor().execute(() -> {
                    try {
                        runWantHaves(closeable, cid);
                    } catch (ClosedException closedException) {
                        notify.Release(cid);
                    }
                });
            }
            return notify.Subscribe(cid);
        } finally {
            LogUtils.error(TAG, "ReleaseBlock " + cid.String());
            searches.remove(cid);
        }

    }

    public void BlockReceived(@NonNull ID peer, @NonNull Block block) {

        Cid cid = block.Cid();

        if (searches.contains(cid)) {

            LogUtils.error(TAG, "BlockReceived " + cid.String());

            notify.Publish(block);
            faulty.remove(peer);
            priority.add(peer);

        }
    }

}
