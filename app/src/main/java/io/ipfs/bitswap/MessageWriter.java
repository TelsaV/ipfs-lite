package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.Closeable;
import io.ipfs.ClosedException;
import io.ipfs.bitswap.internal.RecallWantlist;
import io.ipfs.bitswap.wantlist.Entry;
import io.ipfs.bitswap.wantlist.Wantlist;
import io.ipfs.cid.Cid;
import io.libp2p.peer.ID;
import io.protos.bitswap.BitswapProtos;
import lite.Stream;

public class MessageWriter {

    public static int MaxMessageSize = 1024 * 1024 * 2;
    public static int MaxPriority = Integer.MAX_VALUE;


    public static void sendBroadcastWantsMessage(@NonNull Closeable closeable,
                                          @NonNull BitSwapNetwork network,
                                          @NonNull ID peer,
                                          @NonNull List<Cid> wantHaves) throws ClosedException {
        if (wantHaves.size() == 0) {
            return;
        }

        int priority = MaxPriority;
        RecallWantlist peerWants = RecallWantlist.newRecallWantList();
        RecallWantlist bcstWants = RecallWantlist.newRecallWantList();


        for (Cid c : wantHaves) {
            bcstWants.Add(c, priority, BitswapProtos.Message.Wantlist.WantType.Have);
            priority--;
        }

        Stream stream = network.NewStream(closeable, peer);


        BitSwapMessage message = extractOutgoingMessage(bcstWants, peerWants,
                network.SupportsHave(stream));

        if (message.Empty()) {
            return;
        }

        network.SendMessage(stream, message);


    }

    public static void sendWantsMessage(@NonNull Closeable closeable,
                                        @NonNull BitSwapNetwork network,
                                        @NonNull ID peer,
                                        @NonNull List<Cid> wantBlocks,
                                        @NonNull List<Cid> wantHaves) throws ClosedException {


        if (wantBlocks.size() == 0 && wantHaves.size() == 0) {
            return;
        }
        int priority = MaxPriority;

        RecallWantlist peerWants = RecallWantlist.newRecallWantList();

        for (Cid c : wantHaves) {
            peerWants.Add(c, priority, BitswapProtos.Message.Wantlist.WantType.Have);
            priority--;
        }
        for (Cid c : wantBlocks) {
            peerWants.Add(c, priority, BitswapProtos.Message.Wantlist.WantType.Block);
            priority--;
        }


        Stream stream = network.NewStream(closeable, peer);


        BitSwapMessage message = extractOutgoingMessage(RecallWantlist.newRecallWantList(),
                peerWants, network.SupportsHave(stream));

        if (message.Empty()) {
            return;
        }

        network.SendMessage(stream, message);


    }

    private static BitSwapMessage extractOutgoingMessage(
            @NonNull RecallWantlist bcstWants,
            @NonNull RecallWantlist peerWants,
            boolean supportsHave) {

        BitSwapMessage msg = BitSwapMessage.New(false);

        List<Entry> peerEntries = new ArrayList<>(peerWants.pending.Entries());
        List<Entry> bcstEntries = new ArrayList<>(bcstWants.pending.Entries());


        if (!supportsHave) {
            List<Entry> filteredPeerEntries = new ArrayList<>(); // TODO := peerEntries[:0]
            // If the remote peer doesn't support HAVE / DONT_HAVE messages,
            // don't send want-haves (only send want-blocks)
            //
            // Doing this here under the lock makes everything else in this
            // function simpler.
            //
            // TODO: We should _try_ to avoid recording these in the first
            // place if possible.
            for (Entry e : peerEntries) {
                if (e.WantType == BitswapProtos.Message.Wantlist.WantType.Have) {
                    peerWants.RemoveType(e.Cid, BitswapProtos.Message.Wantlist.WantType.Have);
                } else {
                    filteredPeerEntries.add(e);
                }
            }
            peerEntries = filteredPeerEntries;
        }
        // mq.wllock.Unlock()

        // We prioritize cancels, then regular wants, then broadcast wants.
        int msgSize = 0; // size of message so far
        int sentPeerEntries = 0;// number of peer entries in message
        int sentBcstEntries = 0; // number of broadcast entries in message


        // Next, add the wants. If we have too many entries to fit into a single
        // message, sort by priority and include the high priority ones first.
        // However, avoid sorting till we really need to as this code is a
        // called frequently.

        // Add each regular want-have / want-block to the message.

        if (msgSize + (peerEntries.size() * BitSwapMessage.MaxEntrySize) > MaxMessageSize) {
            Wantlist.SortEntries(peerEntries);
        }

        for (Entry e : peerEntries) {

            msgSize += msg.AddEntry(e.Cid, e.Priority, e.WantType, true);
            sentPeerEntries++;
        }

        // Add each broadcast want-have to the message.
        if (msgSize + (bcstEntries.size() * BitSwapMessage.MaxEntrySize) > MaxMessageSize) {
            Wantlist.SortEntries(bcstEntries);
        }

        // Add each broadcast want-have to the message
        for (Entry e : bcstEntries) {

            // Broadcast wants are sent as want-have
            BitswapProtos.Message.Wantlist.WantType wantType =
                    BitswapProtos.Message.Wantlist.WantType.Have;

            // If the remote peer doesn't support HAVE / DONT_HAVE messages,
            // send a want-block instead
            if (!supportsHave) {
                wantType = BitswapProtos.Message.Wantlist.WantType.Block;
            }

            msgSize += msg.AddEntry(e.Cid, e.Priority, wantType, false);
            sentBcstEntries++;
        }


        // Finally, re-take the lock, mark sent and remove any entries from our
        // message that we've decided to cancel at the last minute.

        for (int i = 0; i < sentPeerEntries; i++) {
            Entry e = peerEntries.get(i);
            if (!peerWants.MarkSent(e)) {
                // It changed.
                msg.Remove(e.Cid);
                e.Cid = Cid.Undef();
            }
        }
        for (int i = 0; i < sentBcstEntries; i++) {
            Entry e = bcstEntries.get(i);
            if (!bcstWants.MarkSent(e)) {
                msg.Remove(e.Cid);
                e.Cid = Cid.Undef();
            }
        }

        return msg;
    }


}
