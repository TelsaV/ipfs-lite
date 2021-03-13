package io.ipfs.utils;

import androidx.annotation.NonNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.Closeable;
import io.ipfs.bitswap.BitSwapNetwork;
import io.ipfs.bitswap.network.DefaultMessageSender;
import io.ipfs.bitswap.network.MessageSender;
import io.ipfs.bitswap.network.MessageSenderOpts;
import io.ipfs.bitswap.network.Receiver;
import io.libp2p.host.Host;
import io.libp2p.peer.ID;

public class LiteHost implements BitSwapNetwork {
    private final Host host;
    private static final ID ProtocolBitswap = new ID("/ipfs/lite/1.0.0");
    private Receiver receiver;
    private final Connect connect;
    private final List<ID> supportedProtocols = new ArrayList<>();
    private final ID protocol;

    public LiteHost(@NonNull Host host, @NonNull Connect connect) {
        this.host = host;
        this.connect = connect;
        this.protocol = LiteHost.ProtocolBitswap;
        supportedProtocols.add(this.protocol);
    }

    public static BitSwapNetwork NewLiteHost(@NonNull Host host, @NonNull Connect connect) {
        return new LiteHost(host, connect);
    }


    public boolean ConnectTo(@NonNull Closeable ctx, @NonNull ID p) {
        if (connect.ShouldConnect(p.String())) {
            return host.Connect(ctx, p);
        }
        throw new RuntimeException("unknown user");
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

        if (!connect.ShouldConnect(peer.String())) {
            throw new RuntimeException("unknown user");
        }

        MessageSenderOpts copy = setDefaultOpts(opts);
        MessageSender sender = new DefaultMessageSender(this, copy, peer);

        sender.multiAttempt(ctx, () -> sender.Connect(ctx));


        return sender;
    }


}
