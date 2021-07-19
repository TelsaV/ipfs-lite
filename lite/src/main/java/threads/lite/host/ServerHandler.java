package threads.lite.host;

import androidx.annotation.NonNull;

import net.luminis.quic.QuicConnection;
import net.luminis.quic.server.ApplicationProtocolConnection;
import net.luminis.quic.stream.QuicStream;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.function.Consumer;

import threads.lite.cid.PeerId;
import threads.lite.crypto.PubKey;

public class ServerHandler extends ApplicationProtocolConnection implements Consumer<QuicStream> {
    private static final String TAG = ServerHandler.class.getSimpleName();
    private final QuicConnection connection;
    private final LiteHost liteHost;
    private final PeerId peerId;

    public ServerHandler(@NonNull LiteHost liteHost, @NonNull QuicConnection quicConnection) throws IOException {
        this.liteHost = liteHost;
        this.connection = quicConnection;


        X509Certificate cert = connection.getRemoteCertificate();
        Objects.requireNonNull(cert);
        PubKey pubKey = LiteHostCertificate.extractPublicKey(cert);
        Objects.requireNonNull(pubKey);
        peerId = PeerId.fromPubKey(pubKey);
        Objects.requireNonNull(peerId);
        liteHost.handleConnection(peerId, connection, false);

        connection.setPeerInitiatedStreamCallback(this);

    }

    @Override
    public void accept(QuicStream quicStream) {
        new StreamHandler(connection, quicStream, peerId, liteHost);
    }
}
