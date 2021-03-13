package io.ipfs.bitswap.network;

import androidx.annotation.NonNull;

import io.Closeable;
import io.ipfs.bitswap.BitSwapNetwork;
import io.libp2p.network.Stream;
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


    public void multiAttempt(@NonNull Closeable closeable, @NonNull Attempt attempt) {

    }

    @Override
    public Stream Connect(@NonNull Closeable ctx) {
        return null;
    }
}
