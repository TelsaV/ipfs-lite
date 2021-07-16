package threads.lite.host;

import androidx.annotation.NonNull;

import net.luminis.quic.QuicConnection;
import net.luminis.quic.stream.QuicStream;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

import bitswap.pb.MessageOuterClass;
import identify.pb.IdentifyOuterClass;
import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.PeerId;
import threads.lite.core.ProtocolIssue;
import threads.lite.utils.DataHandler;


public class StreamHandler {
    private static final String TAG = StreamHandler.class.getSimpleName();
    protected final int streamId;
    private final LiteHost host;
    @NonNull
    private final QuicConnection connection;
    @NonNull
    private final DataHandler reader = new DataHandler(new HashSet<>(
            Arrays.asList(IPFS.STREAM_PROTOCOL, IPFS.PUSH_PROTOCOL, IPFS.BITSWAP_PROTOCOL,
                    IPFS.IDENTITY_PROTOCOL, IPFS.DHT_PROTOCOL, IPFS.RELAY_PROTOCOL)
    ), IPFS.MESSAGE_SIZE_MAX);
    @NonNull
    private final PeerId peerId;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final AtomicBoolean init = new AtomicBoolean(false);
    private volatile String protocol = null;
    private long time = System.currentTimeMillis();

    public StreamHandler(@NonNull QuicConnection connection, @NonNull QuicStream quicStream,
                         @NonNull PeerId peerId, @NonNull LiteHost host) {
        this.inputStream = quicStream.getInputStream();
        this.outputStream = quicStream.getOutputStream();
        this.streamId = quicStream.getStreamId();
        this.connection = connection;
        this.host = host;
        this.peerId = peerId;
        new Thread(this::reading).start();
        LogUtils.debug(TAG, "Instance" + " StreamId " + streamId + " PeerId " + peerId);
    }


    protected void reading() {
        byte[] buf = new byte[4096];
        try {
            int length;

            while ((length = inputStream.read(buf, 0, 4096)) > 0) {
                byte[] data = Arrays.copyOfRange(buf, 0, length);
                channelRead0(data);
            }

        } catch (Throwable throwable) {
            exceptionCaught(throwable);
        }
    }


    public void writeAndFlush(@NonNull byte[] data) {
        try {
            outputStream.write(data);
            outputStream.flush();
        } catch (Throwable throwable) {
            exceptionCaught(throwable);
        }
    }

    public void exceptionCaught(@NonNull Throwable cause) {
        LogUtils.debug(TAG, "Error" + " StreamId " + streamId + " PeerId " + peerId + " " + cause);
        reader.clear();
    }


    public void channelRead0(@NonNull byte[] msg) throws Exception {

        reader.load(msg);

        if (reader.isDone()) {
            for (String token : reader.getTokens()) {

                LogUtils.debug(TAG, "Token " + token + " StreamId " + streamId + " PeerId " + peerId);

                protocol = token;
                switch (token) {
                    case IPFS.STREAM_PROTOCOL:
                        if (!init.getAndSet(true)) {
                            writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
                        }
                        break;
                    case IPFS.PUSH_PROTOCOL:
                        writeAndFlush(DataHandler.writeToken(IPFS.PUSH_PROTOCOL));
                        break;
                    case IPFS.BITSWAP_PROTOCOL:
                        writeAndFlush(DataHandler.writeToken(IPFS.BITSWAP_PROTOCOL));
                        time = System.currentTimeMillis();
                        break;
                    case IPFS.IDENTITY_PROTOCOL:
                        writeAndFlush(DataHandler.writeToken(IPFS.IDENTITY_PROTOCOL));

                        IdentifyOuterClass.Identify response =
                                host.createIdentity(connection.getRemoteAddress());

                        writeAndFlush(DataHandler.encode(response));
                        return;
                    default:
                        LogUtils.debug(TAG, "Ignore " + token +
                                " StreamId " + streamId + " PeerId " + peerId);
                        writeAndFlush(DataHandler.writeToken(IPFS.NA));
                        return;
                }
            }
            byte[] message = reader.getMessage();

            if (message != null) {
                if (protocol != null) {
                    switch (protocol) {
                        case IPFS.BITSWAP_PROTOCOL:
                            host.forwardMessage(peerId,
                                    MessageOuterClass.Message.parseFrom(message));

                            LogUtils.debug(TAG, "Time " + (System.currentTimeMillis() - time) +
                                    " StreamId " + streamId + " PeerId " + peerId);
                            break;
                        case IPFS.PUSH_PROTOCOL:
                            host.push(peerId, message);
                            break;
                        default:
                            throw new ProtocolIssue("StreamHandler invalid protocol");
                    }
                } else {
                    throw new ProtocolIssue("StreamHandler invalid protocol");
                }
            }
        } else {
            LogUtils.debug(TAG, "Iteration " + protocol + " " + reader.hasRead() + " "
                    + reader.expectedBytes() + " StreamId " + streamId + " PeerId " + peerId +
                    " Tokens " + reader.getTokens().toString());
        }
    }
}
