package threads.lite.host;

import androidx.annotation.NonNull;

import net.luminis.quic.QuicConnection;

import threads.lite.cid.PeerId;

public interface ConnectionHandler {
    void outgoingConnection(@NonNull PeerId peerId, @NonNull QuicConnection connection);

    void incomingConnection(@NonNull PeerId peerId, @NonNull QuicConnection connection);
}
