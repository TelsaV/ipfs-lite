package io.ipfs.bitswap.network;

import androidx.annotation.NonNull;

import io.Closeable;
import io.ipfs.ClosedException;
import io.ipfs.bitswap.BitSwapNetwork;
import io.ipfs.bitswap.message.BitSwapMessage;
import io.libp2p.peer.ID;
import lite.Stream;

public class DefaultMessageSender implements MessageSender {
    private final BitSwapNetwork bitSwapNetwork;
    private final ID to;

    private static final String TAG = DefaultMessageSender.class.getSimpleName();


    public DefaultMessageSender(@NonNull BitSwapNetwork bitSwapNetwork, @NonNull ID to) {
        this.bitSwapNetwork = bitSwapNetwork;
        this.to = to;
    }

    @Override
    public Stream NewStream(@NonNull Closeable closeable) throws ClosedException {
        return bitSwapNetwork.NewStream(closeable, to);
    }

    @Override
    public void SendMsg(@NonNull Closeable closeable, @NonNull Stream stream, @NonNull BitSwapMessage message) throws ClosedException {
        /// TODO invoke form the outside
        Connect(closeable, false);

        bitSwapNetwork.SendMessage(stream, message);
    }


    @Override
    public boolean SupportsHave(@NonNull Stream stream) {

        return bitSwapNetwork.SupportsHave(stream.protocol());

    }

    @Override
    public void Connect(@NonNull Closeable closeable, boolean protect) throws ClosedException {
        bitSwapNetwork.ConnectTo(closeable, to, protect);
    }
}
