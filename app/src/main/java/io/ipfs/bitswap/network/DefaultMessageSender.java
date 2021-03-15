package io.ipfs.bitswap.network;

import androidx.annotation.NonNull;

import java.util.Objects;

import io.Closeable;
import io.ipfs.bitswap.BitSwapNetwork;
import io.ipfs.bitswap.ErrNotSupported;
import io.ipfs.bitswap.message.BitSwapMessage;
import io.libp2p.peer.ID;

public class DefaultMessageSender implements MessageSender {
    private final BitSwapNetwork bitSwapNetwork;
    private final MessageSenderOpts opts;
    private final ID to;

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


        Connect(closeable);

        // The send timeout includes the time required to connect
        // (although usually we will already have connected - we only need to
        // connect after a failed attempt to send)
        bitSwapNetwork.msgToStream(closeable, to, message);
    }


    @Override
    public boolean SupportsHave() {
        return true;

    }

    public void multiAttempt(@NonNull Closeable closeable, @NonNull Attempt attempt) {
        // Try to call the function repeatedly
        for (int i = 0; i < opts.MaxRetries; i++) {
            try {
                attempt.invoke();
                return; // success
            } catch (Throwable throwable) {
                // Attempt failed

                if (throwable instanceof ErrNotSupported) {
                    Objects.requireNonNull(bitSwapNetwork.getConnectEventManager()).MarkUnresponsive(to);
                    throw throwable;
                }

            }

        }
        Objects.requireNonNull(bitSwapNetwork.getConnectEventManager()).MarkUnresponsive(to);
        throw new RuntimeException("failed multi attempt");
    }

    @Override
    public void Connect(@NonNull Closeable closeable) {
        bitSwapNetwork.ConnectTo(closeable, to);
    }
}
