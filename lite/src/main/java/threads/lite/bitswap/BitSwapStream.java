package threads.lite.bitswap;

import androidx.annotation.NonNull;

import net.luminis.quic.ConnectionIssue;
import net.luminis.quic.stream.QuicStream;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

import bitswap.pb.MessageOuterClass;
import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.PeerId;
import threads.lite.core.ProtocolIssue;
import threads.lite.utils.DataHandler;


public class BitSwapStream {
    private static final String TAG = BitSwapStream.class.getSimpleName();
    protected final int streamId;
    private final BitSwap bitSwap;
    @NonNull
    private final DataHandler reader = new DataHandler(
            new HashSet<>(Arrays.asList(IPFS.STREAM_PROTOCOL, IPFS.BITSWAP_PROTOCOL)
            ), IPFS.MESSAGE_SIZE_MAX);
    @NonNull
    private final PeerId peerId;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final AtomicBoolean init = new AtomicBoolean(false);
    private long time = System.currentTimeMillis();


    public BitSwapStream(@NonNull QuicStream quicStream, @NonNull PeerId peerId, @NonNull BitSwap bitSwap) {
        this.inputStream = quicStream.getInputStream();
        this.outputStream = quicStream.getOutputStream();
        this.streamId = quicStream.getStreamId();
        this.bitSwap = bitSwap;
        this.peerId = peerId;
        new Thread(this::reading).start();
        LogUtils.debug(TAG, "Instance" + " StreamId " + streamId + " PeerId " + peerId);
    }


    protected void reading() {
        time = System.currentTimeMillis();
        byte[] buf = new byte[4096];
        try {
            int length;

            while ((length = inputStream.read(buf, 0, 4096)) > 0) {
                byte[] data = Arrays.copyOfRange(buf, 0, length);
                channelRead0(data);
            }

        } catch (ConnectionIssue connectionIssue) {
            bitSwap.receiveConnectionFailure(peerId);
            exceptionCaught(connectionIssue);
        } catch (Throwable throwable) {
            closeOutputStream();
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


    public void closeOutputStream() {
        try {
            outputStream.close();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public void exceptionCaught(@NonNull Throwable cause) {
        LogUtils.debug(TAG, "Error" + " StreamId " + streamId + " PeerId " + peerId + " " + cause);
        reader.clear();
    }


    public void channelRead0(@NonNull byte[] msg) throws Exception {

        try {
            reader.load(msg);

            if (reader.isDone()) {

                for (String token : reader.getTokens()) {

                    switch (token) {
                        case IPFS.STREAM_PROTOCOL:
                            if (!init.getAndSet(true)) {
                                writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
                            }
                            break;
                        case IPFS.BITSWAP_PROTOCOL:
                            writeAndFlush(DataHandler.writeToken(IPFS.BITSWAP_PROTOCOL));
                            break;
                        default:
                            throw new Exception("Programming error");
                    }
                }
                byte[] message = reader.getMessage();

                if (message != null) {
                    bitSwap.receiveMessage(peerId,
                            BitSwapMessage.newMessageFromProto(
                                    MessageOuterClass.Message.parseFrom(message)));
                    LogUtils.debug(TAG, "Time " + (System.currentTimeMillis() - time) +
                            " StreamId " + streamId + " PeerId " + peerId);

                }
            } else {
                LogUtils.debug(TAG, "Iteration " + reader.hasRead() + " "
                        + reader.expectedBytes() + " StreamId " + streamId + " PeerId " + peerId +
                        " Tokens " + reader.getTokens().toString());
            }
        } catch (ProtocolIssue protocolIssue) {
            LogUtils.debug(TAG, protocolIssue.getMessage() +
                    " StreamId " + streamId + " PeerId " + peerId);
            writeAndFlush(DataHandler.writeToken(IPFS.NA));
            closeOutputStream();
        }
    }
}
