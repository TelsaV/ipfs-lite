package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import java.util.List;

import io.Closeable;
import io.ipfs.ClosedException;
import io.ipfs.cid.Cid;
import io.libp2p.peer.PeerID;
import io.protos.bitswap.BitswapProtos;
import lite.Stream;

public class MessageWriter {

    public static int MaxPriority = Integer.MAX_VALUE;


    public static void sendHaveMessage(@NonNull Closeable closeable,
                                       @NonNull BitSwapNetwork network,
                                       @NonNull PeerID peer,
                                       @NonNull List<Cid> wantHaves) throws ClosedException {
        if (wantHaves.size() == 0) {
            return;
        }

        int priority = MaxPriority;

        BitSwapMessage message = BitSwapMessage.New(false);
        Stream stream = network.NewStream(closeable, peer);

        for (Cid c : wantHaves) {

            // Broadcast wants are sent as want-have
            BitswapProtos.Message.Wantlist.WantType wantType =
                    BitswapProtos.Message.Wantlist.WantType.Have;

            // If the remote peer doesn't support HAVE / DONT_HAVE messages,
            // send a want-block instead
            if (!network.SupportsHave(stream)) {
                wantType = BitswapProtos.Message.Wantlist.WantType.Block;
            }

            message.AddEntry(c, priority, wantType, false);

            priority--;
        }

        if (message.Empty()) {
            return;
        }

        network.SendMessage(stream, message);


    }

    public static void sendWantsMessage(@NonNull Closeable closeable,
                                        @NonNull BitSwapNetwork network,
                                        @NonNull PeerID peer,
                                        @NonNull List<Cid> wantBlocks) throws ClosedException {

        if (wantBlocks.size() == 0) {
            return;
        }
        BitSwapMessage message = BitSwapMessage.New(false);

        int priority = MaxPriority;

        for (Cid c : wantBlocks) {

            message.AddEntry(c, priority,
                    BitswapProtos.Message.Wantlist.WantType.Block, true);

            priority--;
        }

        if (message.Empty()) {
            return;
        }

        network.WriteMessage(closeable, peer, message);

    }


}
