package io.ipfs.utils;

import androidx.annotation.NonNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.Closeable;
import io.ipfs.bitswap.BitSwapNetwork;
import io.ipfs.bitswap.message.BitSwapMessage;
import io.ipfs.bitswap.network.ConnectEventManager;
import io.ipfs.bitswap.network.DefaultMessageSender;
import io.ipfs.bitswap.network.MessageSender;
import io.ipfs.bitswap.network.MessageSenderOpts;
import io.ipfs.bitswap.network.Receiver;
import io.libp2p.host.Host;
import io.libp2p.network.Stream;
import io.libp2p.peer.ID;

public class LiteHost implements BitSwapNetwork {
    private final Host host;
    private static final io.libp2p.protocol.ID ProtocolBitswap = new io.libp2p.protocol.ID("/ipfs/lite/1.0.0");
    private Receiver receiver;
    private static final Duration sendMessageTimeout = Duration.ofMinutes(10);
    private final Connector connector;
    private final List<io.libp2p.protocol.ID> supportedProtocols = new ArrayList<>();
    private final io.libp2p.protocol.ID protocol;
    private ConnectEventManager connectEvtMgr;

    public LiteHost(@NonNull Host host, @NonNull Connector connector) {
        this.host = host;
        this.connector = connector;
        this.protocol = LiteHost.ProtocolBitswap;
        supportedProtocols.add(this.protocol);
    }

    public static BitSwapNetwork NewLiteHost(@NonNull Host host, @NonNull Connector connector) {
        return new LiteHost(host, connector);
    }

    public boolean ConnectTo(@NonNull Closeable ctx, @NonNull ID p) {
        if (connector.ShouldConnect(p.String())) {
            return host.Connect(ctx, p);
        }
        throw new RuntimeException("unknown user");
    }

    @Override
    public Stream newStreamToPeer(@NonNull Closeable closeable, @NonNull ID to) {
        return host.NewStream(closeable, to, supportedProtocols);
    }

    @Override
    public boolean SupportsHave(@NonNull io.libp2p.protocol.ID protocol) {
        return true;
    }

    public void msgToStream(@NonNull Closeable closeable, @NonNull Stream stream,
                            @NonNull BitSwapMessage message, @NonNull Duration timeout) {

        stream.SetWriteDeadline(timeout);

        // Older Bitswap versions use a slightly different wire format so we need
        // to convert the message to the appropriate format depending on the remote
        // peer's Bitswap version.
        if (protocol.equals(stream.Protocol())) {
            message.ToNetV1(stream);
        } else {
            throw new RuntimeException("unrecognized protocol on remote: " + stream.Protocol());
        }

        stream.SetWriteDeadline(null);
    }

    @Override
    public void SetDelegate(@NonNull Receiver receiver) {
        this.receiver = receiver;

        this.connectEvtMgr = new ConnectEventManager(receiver);
        for (io.libp2p.protocol.ID proto : supportedProtocols) {
            host.SetStreamHandler(proto, this::handleNewStream);
        }

        // TODO host.Network().Notify((*netNotifiee)(bsnet))

    }

    private void handleNewStream(@NonNull Closeable closeable, @NonNull Stream stream) {

        try {
            if (receiver == null) {
                stream.Reset();
                return;
            }
            byte[] data = stream.getData();
            BitSwapMessage received = BitSwapMessage.fromData(data);
            ID p = stream.Conn().RemotePeer();
            if (connector.ShouldConnect(p.String())) {
                connectEvtMgr.OnMessage(p);
                receiver.ReceiveMessage(closeable, p, received);
                // TODO atomic.AddUint64( & bsnet.stats.MessagesRecvd, 1)
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
        } finally {
            stream.Close();
        }
    }

    @Override
    public void SendMessage(@NonNull Closeable closeable, @NonNull ID peer, @NonNull BitSwapMessage message) {

        if (!connector.ShouldConnect(peer.String())) {
            throw new RuntimeException("unknown user");
        }
        Stream s = newStreamToPeer(closeable, peer);


        try {
            msgToStream(closeable, s, message, sendMessageTimeout);
        } catch (Throwable throwable) {
            s.Reset();
        }

        s.Close();
    }


    private MessageSenderOpts setDefaultOpts(@NonNull MessageSenderOpts opts) {
        MessageSenderOpts copy = new MessageSenderOpts();
        if (opts.MaxRetries == 0) {
            copy.MaxRetries = 3;
        }
        if (opts.SendTimeout == null) {
            copy.SendTimeout = Duration.ofMinutes(10);
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


}
