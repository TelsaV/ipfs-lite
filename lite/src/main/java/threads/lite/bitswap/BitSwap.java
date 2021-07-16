package threads.lite.bitswap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.luminis.quic.ConnectionIssue;
import net.luminis.quic.QuicClientConnection;
import net.luminis.quic.stream.QuicStream;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.Cid;
import threads.lite.cid.PeerId;
import threads.lite.core.Closeable;
import threads.lite.core.ClosedException;
import threads.lite.format.Block;
import threads.lite.format.BlockStore;
import threads.lite.host.LiteHost;
import threads.lite.utils.DataHandler;


public class BitSwap implements Interface {

    private static final String TAG = BitSwap.class.getSimpleName();

    @NonNull
    private final ContentManager contentManager;
    @NonNull
    private final BitSwapEngine engine;
    @NonNull
    private final LiteHost host;
    private final ConcurrentHashMap<PeerId, QuicClientConnection> connections = new ConcurrentHashMap<>();


    public BitSwap(@NonNull BlockStore blockstore, @NonNull LiteHost host) {
        this.host = host;
        contentManager = new ContentManager(this, blockstore, host);
        engine = new BitSwapEngine(this, blockstore, host.self());
    }

    @Nullable
    @Override
    public Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid, boolean root) throws ClosedException {
        return contentManager.getBlock(closeable, cid, root);
    }

    @Override
    public void preload(@NonNull Closeable closeable, @NonNull List<Cid> cids) {
        contentManager.loadBlocks(closeable, cids);
    }

    @Override
    public void reset() {
        contentManager.reset();

        for (PeerId peerId : connections.keySet()) {
            removeConnection(peerId);
        }

    }

    public void receiveMessage(@NonNull PeerId peer, @NonNull BitSwapMessage incoming) {

        LogUtils.verbose(TAG, "ReceiveMessage " + peer.toBase58());

        List<Block> blocks = incoming.Blocks();
        List<Cid> haves = incoming.Haves();
        if (blocks.size() > 0 || haves.size() > 0) {
            try {
                LogUtils.debug(TAG, "ReceiveMessage " + peer.toBase58());
                receiveBlocksFrom(peer, blocks, haves);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        }

        if (IPFS.BITSWAP_ENGINE_ACTIVE) {
            engine.MessageReceived(peer, incoming);
        }

    }

    private void receiveBlocksFrom(@NonNull PeerId peer,
                                   @NonNull List<Block> wanted,
                                   @NonNull List<Cid> haves) {

        for (Block block : wanted) {
            LogUtils.verbose(TAG, "ReceiveBlock " + peer.toBase58() +
                    " " + block.getCid().String());
            contentManager.blockReceived(peer, block);
        }

        contentManager.haveReceived(peer, haves);

    }


    private QuicClientConnection getConnection(@NonNull Closeable closeable, @NonNull PeerId peerId)
            throws ClosedException, ConnectionIssue {
        QuicClientConnection conn = connections.get(peerId);
        if (conn != null && conn.isConnected()) {
            LogUtils.verbose(TAG, "Reuse connection " + peerId.toBase58());
            return conn;
        }
        conn = host.connect(closeable, peerId, IPFS.CONNECT_TIMEOUT,
                IPFS.BITSWAP_GRACE_PERIOD, IPFS.MAX_STREAMS, IPFS.MESSAGE_SIZE_MAX);

        LogUtils.debug(TAG, "New connection " + peerId.toBase58());
        conn.setPeerInitiatedStreamCallback(quicStream ->
                new BitSwapStream(quicStream, peerId, this));

        connections.put(peerId, conn);
        return conn;
    }

    private void removeConnection(@NonNull PeerId peerId) {
        LogUtils.debug(TAG, "Remove connection " + peerId.toBase58());
        QuicClientConnection conn = connections.remove(peerId);
        if (conn != null) {
            conn.close();
        }
    }

    public void writeMessage(@NonNull Closeable closeable, @NonNull PeerId peerId,
                             @NonNull BitSwapMessage message, int readTimeout, short priority)
            throws ClosedException, ConnectionIssue {

        if (IPFS.BITSWAP_REQUEST_ACTIVE) {
            boolean success = false;

            long time = System.currentTimeMillis();
            QuicClientConnection conn = getConnection(closeable, peerId);


            try {
                QuicStream quicStream = conn.createStream(true,
                        IPFS.CREATE_STREAM_TIMEOUT, TimeUnit.SECONDS);
                BitSwapSend bitSwapSend = new BitSwapSend(quicStream, readTimeout, TimeUnit.SECONDS);

                // TODO streamChannel.updatePriority(new QuicStreamPriority(priority, false));

                bitSwapSend.writeAndFlush(DataHandler.writeToken(
                        IPFS.STREAM_PROTOCOL, IPFS.BITSWAP_PROTOCOL));
                bitSwapSend.writeAndFlush(DataHandler.encode(message.ToProtoV1()));
                bitSwapSend.closeOutputStream();
                bitSwapSend.reading();

                success = true;
            } catch (ConnectionIssue exception) {
                LogUtils.error(TAG, exception);
                removeConnection(peerId);
                throw exception;
            } catch (Throwable throwable) {
                removeConnection(peerId);
                LogUtils.error(TAG, throwable);
                throw new ConnectionIssue();

            } finally {
                LogUtils.debug(TAG, "Send took " + success + " " +
                        peerId.toBase58() + " " + (System.currentTimeMillis() - time));
            }
        }
    }

    public void receiveConnectionFailure(@NonNull PeerId peerId) {
        try {
            removeConnection(peerId);
            contentManager.receiveConnectionFailure(peerId);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }
}

