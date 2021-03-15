package io.ipfs.bitswap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Duration;

import io.Closeable;
import io.ipfs.bitswap.message.BitSwapMessage;
import io.ipfs.bitswap.network.ConnectEventManager;
import io.ipfs.bitswap.network.DefaultMessageSender;
import io.ipfs.bitswap.network.MessageSender;
import io.ipfs.bitswap.network.MessageSenderOpts;
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
    private final io.libp2p.protocol.ID protocol;
    private ConnectEventManager connectEventManager;

    private LiteHost(@NonNull Host host,
                     @Nullable ContentRouting contentRouting,
                     @NonNull Connector connector,
                     @NonNull io.libp2p.protocol.ID protocol) {
        this.host = host;
        this.contentRouting = contentRouting;
        this.connector = connector;
        this.protocol = protocol;
    }

    public static BitSwapNetwork NewLiteHost(@NonNull Host host,
                                             @Nullable ContentRouting contentRouting,
                                             @NonNull Connector connector,
                                             @NonNull io.libp2p.protocol.ID protocol) {
        return new LiteHost(host, contentRouting, connector, protocol);
    }

    public boolean ConnectTo(@NonNull Closeable closeable, @NonNull ID peer) {
        if (connector.ShouldConnect(peer.String())) {
            return host.Connect(closeable, peer);
        }
        throw new RuntimeException("unknown user");
    }


    @Override
    public boolean SupportsHave(@NonNull io.libp2p.protocol.ID protocol) {

        return true;
    }

    public void msgToStream(@NonNull Closeable closeable, @NonNull ID to,
                            @NonNull BitSwapMessage message) {


        // Older Bitswap versions use a slightly different wire format so we need
        // to convert the message to the appropriate format depending on the remote
        // peer's Bitswap version.
        byte[] data = message.ToNetV1();
        // TODO only support one protocol
        host.WriteMessage(closeable, to, protocol, data);

        /* TODO
        io.libp2p.protocol.ID id = stream.Protocol();
        if (LiteHost.ProtocolBitswapOneOne.equals(id) ||
                LiteHost.ProtocolBitswap.equals(id) ||
                LiteHost.ProtocolLite.equals(id)) {
            byte[] data = message.ToNetV1();
            stream.WriteData(data, timeout);
        } else if (LiteHost.ProtocolBitswapNoVers.equals(id) ||
                LiteHost.ProtocolBitswapOneZero.equals(id)) {
            byte[] data = message.ToNetV0();
            stream.WriteData(data, timeout);
        } else {
            throw new RuntimeException("unrecognized protocol on remote: " + stream.Protocol());
        }*/

    }

    @Override
    public void SetDelegate(@NonNull Receiver receiver) {
        this.receiver = receiver;

        this.connectEventManager = new ConnectEventManager(receiver);

        host.SetStreamHandler(protocol, this::handleNewStream);


        // TODO host.Network().Notify((*netNotifiee)(bsnet))

    }

    @Override
    public void DisconnectFrom(@NonNull Closeable closeable, @NonNull ID peer) {
        throw new RuntimeException("should not be invoked");
    }

    @Nullable
    @Override
    public ConnectEventManager getConnectEventManager() {
        return connectEventManager;
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
                    connectEventManager.OnMessage(peer);
                    receiver.ReceiveMessage(closeable, peer, received);
                    // TODO atomic.AddUint64( & bsnet.stats.MessagesRecvd, 1)
                }
            } else {
                receiver.ReceiveError(peer, stream.GetError());
            }

            // TODO
            /*
            reader:=msgio.NewVarintReaderSize(s, network.MessageSizeMax)
            for {
                received, err :=bsmsg.FromMsgReader(reader)
                if err != nil {
                    if err != io.EOF {
                        _ = s.Reset()
                        bsnet.receiver.ReceiveError(err)
                        log.Debugf("bitswap net handleNewStream from %s error: %s", s.Conn().RemotePeer(), err)
                    }
                    return
                }

                p:=s.Conn().RemotePeer()
                if !bsnet.listener.ShouldGate(p.String()) {
                    ctx:=context.Background()
                    bsnet.connectEvtMgr.OnMessage(s.Conn().RemotePeer())
                    bsnet.receiver.ReceiveMessage(ctx, p, received)
                    atomic.AddUint64( & bsnet.stats.MessagesRecvd, 1)
                }
            }*/
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    @Override
    public void SendMessage(@NonNull Closeable closeable, @NonNull ID peer, @NonNull BitSwapMessage message) {

        if (!connector.ShouldConnect(peer.String())) {
            throw new RuntimeException("unknown user");
        }

        msgToStream(closeable, peer, message);

    }


    private MessageSenderOpts setDefaultOpts(@NonNull MessageSenderOpts opts) {
        MessageSenderOpts copy = new MessageSenderOpts();
        if (opts.MaxRetries == 0) {
            copy.MaxRetries = 3;
        }
        if (opts.SendErrorBackoff == null) {
            copy.SendErrorBackoff = Duration.ofMillis(100);
        }
        return copy;
    }


    public MessageSender NewMessageSender(@NonNull Closeable ctx, @NonNull ID peer, @NonNull MessageSenderOpts opts) {

        if (!connector.ShouldConnect(peer.String())) {
            throw new RuntimeException("unknown user");
        }

        MessageSenderOpts copy = setDefaultOpts(opts);
        MessageSender sender = new DefaultMessageSender(this, copy, peer);

        sender.multiAttempt(ctx, () -> sender.Connect(ctx));


        return sender;
    }


    @Override
    public void FindProvidersAsync(@NonNull Providers providers, @NonNull Cid cid, int number) {
        if (contentRouting != null) {
            contentRouting.FindProvidersAsync(providers, cid, number);
        }
    }

    @Override
    public void Provide(@NonNull Closeable closeable, @NonNull Cid cid) {
        throw new RuntimeException("not supported");
    }
}
