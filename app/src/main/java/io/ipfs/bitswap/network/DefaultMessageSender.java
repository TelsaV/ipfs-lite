package io.ipfs.bitswap.network;

import androidx.annotation.NonNull;

import io.Closeable;
import io.ipfs.bitswap.BitSwapNetwork;
import io.ipfs.bitswap.message.BitSwapMessage;
import io.libp2p.network.Stream;
import io.libp2p.peer.ID;

public class DefaultMessageSender implements MessageSender {
    private final BitSwapNetwork bitSwapNetwork;
    private final MessageSenderOpts opts;
    private final ID to;
    private Stream stream;
    private boolean connected;

    public DefaultMessageSender(@NonNull BitSwapNetwork bitSwapNetwork, @NonNull MessageSenderOpts opts, @NonNull ID to) {
        this.bitSwapNetwork = bitSwapNetwork;
        this.opts = opts;
        this.to = to;
    }


    @Override
    public void SendMsg(@NonNull Closeable closeable, @NonNull BitSwapMessage message) {
        multiAttempt(closeable, () -> send(closeable, message));
    }

    private void send(@NonNull Closeable closeable, @NonNull BitSwapMessage message) {


        stream = Connect(closeable);

        // The send timeout includes the time required to connect
        // (although usually we will already have connected - we only need to
        // connect after a failed attempt to send)
        bitSwapNetwork.msgToStream(closeable, stream, message, opts.SendTimeout);
    }

    @Override
    public void Close() {
        if (stream != null) {
            stream.Close();
        }
    }

    @Override
    public void Reset() {
        if (stream != null) {
            try {
                stream.Reset();
            } finally {
                connected = false;
            }
        }
    }

    @Override
    public boolean SupportsHave() {
        return bitSwapNetwork.SupportsHave(stream.Protocol());

    }

    public void multiAttempt(@NonNull Closeable closeable, @NonNull Attempt attempt) {

    }

    @Override
    public Stream Connect(@NonNull Closeable closeable) {
        if (connected) {
            return stream;
        }

        bitSwapNetwork.ConnectTo(closeable, to);

        stream = bitSwapNetwork.newStreamToPeer(closeable, to);
        if (stream != null) { // TODO check
            connected = true;
        }
        return stream;
    }
}
