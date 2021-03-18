package io.ipfs.bitswap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.Closeable;
import io.LogUtils;
import io.ipfs.ClosedException;
import io.ipfs.IPFS;
import io.ipfs.bitswap.message.BitSwapMessage;
import io.ipfs.bitswap.network.DefaultMessageSender;
import io.ipfs.bitswap.network.MessageSender;
import io.ipfs.bitswap.network.Receiver;
import io.ipfs.cid.Cid;
import io.ipfs.utils.Connector;
import io.libp2p.host.ConnManager;
import io.libp2p.host.Host;
import io.libp2p.network.Stream;
import io.libp2p.peer.ID;
import io.libp2p.routing.ContentRouting;
import io.libp2p.routing.Providers;

public class LiteHost implements BitSwapNetwork {

    private static final String TAG = LiteHost.class.getSimpleName();
    @NonNull
    private final Host host;
    private Receiver receiver;
    @NonNull
    private final Connector connector;
    @Nullable
    private final ContentRouting contentRouting;
    private final List<io.libp2p.protocol.ID> protocols = new ArrayList<>();

    private LiteHost(@NonNull Host host,
                     @Nullable ContentRouting contentRouting,
                     @NonNull Connector connector,
                     @NonNull List<io.libp2p.protocol.ID> protos) {
        this.host = host;
        this.contentRouting = contentRouting;
        this.connector = connector;
        this.protocols.addAll(protos);
    }

    public static BitSwapNetwork NewLiteHost(@NonNull Host host,
                                             @Nullable ContentRouting contentRouting,
                                             @NonNull Connector connector,
                                             @NonNull List<io.libp2p.protocol.ID> protocols) {
        return new LiteHost(host, contentRouting, connector, protocols);
    }

    @Override
    public boolean ConnectTo(@NonNull Closeable closeable, @NonNull ID peer, boolean protect) throws ClosedException {
        if (connector.ShouldConnect(peer.String())) {
            return host.Connect(closeable, peer, protect);
        }
        return false;
    }

    public lite.Stream NewStream(@NonNull Closeable closeable, @NonNull ID peer) throws ClosedException {
        return host.NewStream(closeable, peer, protocols);
    }


    @Override
    public boolean SupportsHave(@NonNull String proto) {
        io.libp2p.protocol.ID protocol = io.libp2p.protocol.ID.Evaluate(proto);
        return protocol.isSupportHas();
    }


    @Override
    public void SetDelegate(@NonNull Receiver receiver) {
        this.receiver = receiver;

        for (io.libp2p.protocol.ID protocol : protocols) {
            host.SetStreamHandler(protocol, this::handleNewStream);
        }

    }

    @Override
    public void DisconnectFrom(@NonNull Closeable closeable, @NonNull ID peer) {
        throw new RuntimeException("should not be invoked");
    }


    @NonNull
    @Override
    public ConnManager ConnectionManager() {
        return host;
    }

    @Override
    public ID Self() {
        return host.Self();
    }

    private void handleNewStream(@NonNull Closeable closeable, @NonNull Stream stream) {

        try {
            if (receiver == null) {
                return;
            }

            ID peer = stream.RemotePeer();
            if (stream.GetError() == null) {
                byte[] data = stream.GetData();
                BitSwapMessage received = BitSwapMessage.fromData(data);

                if (connector.ShouldConnect(peer.String())) {
                    receiver.ReceiveMessage(closeable, peer, received);
                }
            } else {
                String error = stream.GetError();
                if (error != null) {
                    receiver.ReceiveError(peer, error);
                }
            }
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable); // TODO check
        }
    }


    @Override
    public void SendMessage(@NonNull lite.Stream stream, @NonNull BitSwapMessage message) {


        try {

            try {
                byte[] data;
                io.libp2p.protocol.ID evaluate = io.libp2p.protocol.ID.Evaluate(stream.protocol());
                if (io.libp2p.protocol.ID.ProtocolBitswap.equals(evaluate) ||
                        io.libp2p.protocol.ID.ProtocolBitswapOneOne.equals(evaluate) ||
                        io.libp2p.protocol.ID.ProtocolLite.equals(evaluate)) {
                    data = message.ToNetV1();
                } else {
                    LogUtils.error(TAG, "ToNetV0");
                    data = message.ToNetV0();
                }

                LogUtils.error(TAG, "Write message  size " + data.length);
                long res = stream.writeMessage(data, IPFS.WRITE_TIMEOUT);
                LogUtils.error(TAG, "Write message size " + res);
            } catch (Throwable throwable) {
                stream.reset();
                LogUtils.error(TAG, throwable);
                throw new RuntimeException(throwable);
            } finally {
                stream.close();
            }
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    @Override
    public void SendMessage(@NonNull Closeable closeable, @NonNull ID peer, @NonNull BitSwapMessage message) {

        if (!connector.ShouldConnect(peer.String())) {
            throw new RuntimeException("unknown user");
        }

        try {
            lite.Stream stream = host.NewStream(closeable, peer, protocols);
            SendMessage(stream, message);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }


    public MessageSender NewMessageSender(@NonNull Closeable ctx, @NonNull ID peer) throws ClosedException {

        if (!connector.ShouldConnect(peer.String())) {
            throw new RuntimeException("unknown user");
        }


        MessageSender sender = new DefaultMessageSender(this, peer);

        // TODO invoke form the outside
        sender.Connect(ctx, false);


        return sender;
    }


    @Override
    public void FindProvidersAsync(@NonNull Providers providers, @NonNull Cid cid, int number) throws ClosedException {
        if (contentRouting != null) {
            contentRouting.FindProvidersAsync(providers, cid, number);
        }
    }

    @Override
    public void Provide(@NonNull Closeable closeable, @NonNull Cid cid) {
        throw new RuntimeException("not supported");
    }
}
