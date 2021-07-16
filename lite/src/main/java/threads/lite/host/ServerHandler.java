package threads.lite.host;

import androidx.annotation.NonNull;

import net.luminis.quic.QuicConnection;
import net.luminis.quic.server.ApplicationProtocolConnection;
import net.luminis.quic.stream.QuicStream;

import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.function.Consumer;

import threads.lite.LogUtils;
import threads.lite.cid.PeerId;
import threads.lite.crypto.PubKey;

public class ServerHandler extends ApplicationProtocolConnection implements Consumer<QuicStream> {
    private static final String TAG = ServerHandler.class.getSimpleName();
    private final QuicConnection connection;
    private final LiteHost liteHost;

    public ServerHandler(@NonNull LiteHost liteHost, @NonNull QuicConnection quicConnection) {
        this.liteHost = liteHost;
        this.connection = quicConnection;
        connection.setPeerInitiatedStreamCallback(this);
    }

    @Override
    public void accept(QuicStream quicStream) {
        try {
            X509Certificate cert = connection.getRemoteCertificate();
            Objects.requireNonNull(cert);
            PubKey pubKey = LiteHostCertificate.extractPublicKey(cert);
            Objects.requireNonNull(pubKey);
            PeerId peerId = PeerId.fromPubKey(pubKey);
            Objects.requireNonNull(peerId);
            new StreamHandler(connection, quicStream, peerId, liteHost);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }
}
