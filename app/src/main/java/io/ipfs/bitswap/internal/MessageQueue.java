package io.ipfs.bitswap.internal;

import android.util.Pair;

import androidx.annotation.NonNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import io.Closeable;
import io.ipfs.ClosedException;
import io.ipfs.bitswap.message.BitSwapMessage;
import io.ipfs.bitswap.network.MessageSender;
import io.ipfs.bitswap.wantlist.Entry;
import io.ipfs.bitswap.wantlist.Wantlist;
import io.ipfs.cid.Cid;
import io.libp2p.peer.ID;
import io.protos.bitswap.BitswapProtos;
import lite.Stream;

public class MessageQueue {

    public static Duration DefaultRebroadcastInterval = Duration.ofSeconds(30);
    // maxRetries is the number of times to attempt to send a message before
    // giving up
    public static int MaxRetries = 3;
    public static Duration sendTimeout = Duration.ofSeconds(30);
    // maxMessageSize is the maximum message size in bytes
    public static int MaxMessageSize = 1024 * 1024 * 2;
    // sendErrorBackoff is the time to wait before retrying to connect after
    // an error when trying to send a message
    public static Duration SendErrorBackoff = Duration.ofMillis(100);
    // maxPriority is the max priority as defined by the bitswap protocol
    public static int MaxPriority = Integer.MAX_VALUE;
    // sendMessageDebounce is the debounce duration when calling sendMessage()
    public static Duration SendMessageDebounce = Duration.ofMillis(1);
    // when we reach sendMessageCutoff wants/cancels, we'll send the message immediately.
    public static int sendMessageCutoff = 256;
    // when we debounce for more than sendMessageMaxDelay, we'll send the
    // message immediately.
    public static Duration SendMessageMaxDelay = Duration.ofMillis(20);
    // The maximum amount of time in which to accept a response as being valid
    // for latency calculation (as opposed to discarding it as an outlier)
    public static Duration MaxValidLatency = Duration.ofSeconds(30);


    // MessageQueue implements queue of want messages to send to peers.

    private final Closeable ctx;
    // TODO shutdown     func()
    private final ID p;
    private final MessageNetwork network;

    // Take lock whenever any of these variables are modified
    // TODO wllock    sync.Mutex
    private final RecallWantlist bcstWants = RecallWantlist.newRecallWantList();
    private final RecallWantlist peerWants = RecallWantlist.newRecallWantList();
    private final HashSet<Cid> cancels = new HashSet<>();

    // Signals that there are outgoing wants / cancels ready to be processed
    // TODO outgoingWork chan time.Time

    // Channel of CIDs of blocks / HAVEs / DONT_HAVEs received from the peer
    // TODO responses chan []cid.Cid
    // The maximum size of a message in bytes. Any overflow is put into the
    // next message
    int maxMessageSize;
    // The amount of time to wait when there's an error sending to a peer
    // before retrying
    Duration sendErrorBackoff;
    // The maximum amount of time in which to accept a response as being valid
    // for latency calculation
    Duration maxValidLatency;
    int priority = MaxPriority;


    // TODO rebroadcastIntervalLk sync.RWMutex
    // TODO rebroadcastInterval   time.Duration
    // TODO rebroadcastTimer      *time.Timer
    // For performance reasons we just clear out the fields of the message
    // instead of creating a new one every time.
    BitSwapMessage msg;


    // Fires when a timeout occurs waiting for a response from a peer running an
// older version of Bitswap that doesn't support DONT_HAVE messages.
    // TODO type OnDontHaveTimeout func(peer.ID, []cid.Cid)


    private MessageQueue(@NonNull Closeable closeable, @NonNull ID p,
                         @NonNull MessageNetwork messageNetwork) {
        this.ctx = closeable;
        this.p = p;
        this.network = messageNetwork;
        this.maxMessageSize = MaxMessageSize;
        this.sendErrorBackoff = SendErrorBackoff;
        this.maxValidLatency = MaxValidLatency;
        // For performance reasons we just clear out the fields of the message
        // after using it, instead of creating a new one every time.
        this.msg = BitSwapMessage.New(false);
    }

    // New creates a new MessageQueue.
    public static MessageQueue New(Closeable ctx, ID p, MessageNetwork network) {

        return newMessageQueue(ctx, p, network);
    }

    // This constructor is used by the tests
    private static MessageQueue newMessageQueue(Closeable ctx, ID p, MessageNetwork network) {

        return new MessageQueue(ctx, p, network);
        /* TODO
        return  new MessageQueue{
                    shutdown:            cancel,

                    outgoingWork:        make(chan time.Time, 1),
                    responses:           make(chan []cid.Cid, 8),
            rebroadcastInterval: defaultRebroadcastInterval,
                    sendErrorBackoff:    sendErrorBackoff,
        }*/
    }


    // Add want-haves that are part of a broadcast to all connected peers
    // todo synchronited
    public synchronized void AddBroadcastWantHaves(List<Cid> wantHaves) {
        if (wantHaves.size() == 0) {
            return;
        }

        for (Cid c : wantHaves) {
            bcstWants.Add(c, priority, BitswapProtos.Message.Wantlist.WantType.Have);
            priority--;

            // We're adding a want-have for the cid, so clear any pending cancel
            // for the cid
            cancels.remove(c);
        }

        // Schedule a message send
        // TODO signalWorkReady();
    }

    // Add want-haves and want-blocks for the peer for this message queue.
    public synchronized void AddWants(List<Cid> wantBlocks, List<Cid> wantHaves) {
        if (wantBlocks.size() == 0 && wantHaves.size() == 0) {
            return;
        }

        //mq.wllock.Lock()
        //defer mq.wllock.Unlock()

        for (Cid c : wantHaves) {
            peerWants.Add(c, priority, BitswapProtos.Message.Wantlist.WantType.Have);
            priority--;

            // We're adding a want-have for the cid, so clear any pending cancel
            // for the cid
            cancels.remove(c);
        }
        for (Cid c : wantBlocks) {
            peerWants.Add(c, priority, BitswapProtos.Message.Wantlist.WantType.Block);
            priority--;

            // We're adding a want-block for the cid, so clear any pending cancel
            // for the cid
            cancels.remove(c);
        }

        // Schedule a message send
        //mq.signalWorkReady()
    }
    /*
    // Add cancel messages for the given keys.
    func (mq *MessageQueue) AddCancels(cancelKs []cid.Cid) {
        if len(cancelKs) == 0 {
            return
        }

        // Cancel any outstanding DONT_HAVE timers
        mq.dhTimeoutMgr.CancelPending(cancelKs)

        mq.wllock.Lock()

        workReady := false

        // Remove keys from broadcast and peer wants, and add to cancels
        for _, c := range cancelKs {
            // Check if a want for the key was sent
            _, wasSentBcst := mq.bcstWants.sent.Contains(c)
            _, wasSentPeer := mq.peerWants.sent.Contains(c)

            // Remove the want from tracking wantlists
            mq.bcstWants.Remove(c)
            mq.peerWants.Remove(c)

            // Only send a cancel if a want was sent
            if wasSentBcst || wasSentPeer {
                mq.cancels.Add(c)
                workReady = true
            }
        }

        mq.wllock.Unlock()

        // Unlock first to be nice to the scheduler.

        // Schedule a message send
        if workReady {
            mq.signalWorkReady()
        }
    }

    // ResponseReceived is called when a message is received from the network.
// ks is the set of blocks, HAVEs and DONT_HAVEs in the message
// Note that this is just used to calculate latency.
    func (mq *MessageQueue) ResponseReceived(ks []cid.Cid) {
        if len(ks) == 0 {
            return
        }

        // These messages are just used to approximate latency, so if we get so
        // many responses that they get backed up, just ignore the overflow.
        select {
            case mq.responses <- ks:
            default:
        }
    }

    // SetRebroadcastInterval sets a new interval on which to rebroadcast the full wantlist
    func (mq *MessageQueue) SetRebroadcastInterval(delay time.Duration) {
        mq.rebroadcastIntervalLk.Lock()
        mq.rebroadcastInterval = delay
        if mq.rebroadcastTimer != nil {
            mq.rebroadcastTimer.Reset(delay)
        }
        mq.rebroadcastIntervalLk.Unlock()
    }

    // Startup starts the processing of messages and rebroadcasting.
    func (mq *MessageQueue) Startup() {
        mq.rebroadcastIntervalLk.RLock()
        mq.rebroadcastTimer = time.NewTimer(mq.rebroadcastInterval)
        mq.rebroadcastIntervalLk.RUnlock()
        go mq.runQueue()
    }

    // Shutdown stops the processing of messages for a message queue.
    func (mq *MessageQueue) Shutdown() {
        mq.shutdown()
    }

    func (mq *MessageQueue) onShutdown() {
        // Shut down the DONT_HAVE timeout manager
        mq.dhTimeoutMgr.Shutdown()

        // Reset the streamMessageSender
        if mq.sender != nil {
            _ = mq.sender.Reset()
        }
    }

    func (mq *MessageQueue) runQueue() {
        defer mq.onShutdown()

        // Create a timer for debouncing scheduled work.
        scheduleWork := time.NewTimer(0)
        if !scheduleWork.Stop() {
            // Need to drain the timer if Stop() returns false
            // See: https://golang.org/pkg/time/#Timer.Stop
		<-scheduleWork.C
        }

        var workScheduled time.Time
        for mq.ctx.Err() == nil {
            select {
                case <-mq.rebroadcastTimer.C:
                    mq.rebroadcastWantlist()

                case when := <-mq.outgoingWork:
                    // If we have work scheduled, cancel the timer. If we
                    // don't, record when the work was scheduled.
                    // We send the time on the channel so we accurately
                    // track delay.
                    if workScheduled.IsZero() {
                    workScheduled = when
                } else if !scheduleWork.Stop() {
                    // Need to drain the timer if Stop() returns false
				<-scheduleWork.C
                }

                // If we have too many updates and/or we've waited too
                // long, send immediately.
                if mq.pendingWorkCount() > sendMessageCutoff ||
                        time.Since(workScheduled) >= sendMessageMaxDelay {
                    mq.sendIfReady()
                    workScheduled = time.Time{}
                } else {
                    // Otherwise, extend the timer.
                    scheduleWork.Reset(sendMessageDebounce)
                }

                case <-scheduleWork.C:
                    // We have work scheduled and haven't seen any updates
                    // in sendMessageDebounce. Send immediately.
                    workScheduled = time.Time{}
                mq.sendIfReady()

                case res := <-mq.responses:
                    // We received a response from the peer, calculate latency
                    mq.handleResponse(res)

                case <-mq.ctx.Done():
                    return
            }
        }
    }

    // Periodically resend the list of wants to the peer
    func (mq *MessageQueue) rebroadcastWantlist() {
        mq.rebroadcastIntervalLk.RLock()
        mq.rebroadcastTimer.Reset(mq.rebroadcastInterval)
        mq.rebroadcastIntervalLk.RUnlock()

        // If some wants were transferred from the rebroadcast list
        if mq.transferRebroadcastWants() {
            // Send them out
            mq.sendMessage()
        }
    }

    // Transfer wants from the rebroadcast lists into the pending lists.
    func (mq *MessageQueue) transferRebroadcastWants() bool {
        mq.wllock.Lock()
        defer mq.wllock.Unlock()

        // Check if there are any wants to rebroadcast
        if mq.bcstWants.sent.Len() == 0 && mq.peerWants.sent.Len() == 0 {
            return false
        }

        // Copy sent wants into pending wants lists
        mq.bcstWants.pending.Absorb(mq.bcstWants.sent)
        mq.peerWants.pending.Absorb(mq.peerWants.sent)

        return true
    }

    func (mq *MessageQueue) signalWorkReady() {
        select {
            case mq.outgoingWork <- time.Now():
            default:
        }
    }

    func (mq *MessageQueue) sendIfReady() {
        if mq.hasPendingWork() {
            mq.sendMessage()
        }
    }*/

    public void sendMessage() throws ClosedException {

        MessageSender sender = network.NewMessageSender(ctx, p);

        Stream stream = sender.NewStream(ctx);

        // Make sure the DONT_HAVE timeout manager has started
        // Note: Start is idempotent
        //mq.dhTimeoutMgr.Start()

        // Convert want lists to a Bitswap Message

        Pair<BitSwapMessage, OnSent> result = extractOutgoingMessage(sender.SupportsHave(stream));

        BitSwapMessage message = result.first;
        OnSent onSent = result.second;
        // After processing the message, clear out its fields to save memory
        // TODO defer mq.msg.Reset(false)

        if (message.Empty()) {
            return;
        }

        // TODO wantlist := message.Wantlist()
        // TODO mq.logOutgoingMessage(wantlist)


        sender.SendMsg(ctx, stream, message);


        // Record sent time so as to calculate message latency
        onSent.invoke();

        // Set a timer to wait for responses
        // TODO mq.simulateDontHaveWithTimeout(wantlist)

        // If the message was too big and only a subset of wants could be
        // sent, schedule sending the rest of the wants in the next
        // iteration of the event loop.
       /* TODO
        if mq.hasPendingWork() {
            mq.signalWorkReady()
        }*/
    }
        /*
    // If want-block times out, simulate a DONT_HAVE reponse.
// This is necessary when making requests to peers running an older version of
// Bitswap that doesn't support the DONT_HAVE response, and is also useful to
// mitigate getting blocked by a peer that takes a long time to respond.
    func (mq *MessageQueue) simulateDontHaveWithTimeout(wantlist []bsmsg.Entry) {
        // Get the CID of each want-block that expects a DONT_HAVE response
        wants := make([]cid.Cid, 0, len(wantlist))

        mq.wllock.Lock()

        for _, entry := range wantlist {
            if entry.WantType == pb.Message_Wantlist_Block && entry.SendDontHave {
                // Unlikely, but just in case check that the block hasn't been
                // received in the interim
                c := entry.Cid
                if _, ok := mq.peerWants.sent.Contains(c); ok {
                    wants = append(wants, c)
                }
            }
        }

        mq.wllock.Unlock()

        // Add wants to DONT_HAVE timeout manager
        mq.dhTimeoutMgr.AddPending(wants)
    }

    // handleResponse is called when a response is received from the peer,
// with the CIDs of received blocks / HAVEs / DONT_HAVEs
    func (mq *MessageQueue) handleResponse(ks []cid.Cid) {
        now := time.Now()
        earliest := time.Time{}

        mq.wllock.Lock()

        // Check if the keys in the response correspond to any request that was
        // sent to the peer.
        //
        // - Find the earliest request so as to calculate the longest latency as
        //   we want to be conservative when setting the timeout
        // - Ignore latencies that are very long, as these are likely to be outliers
        //   caused when
        //   - we send a want to peer A
        //   - peer A does not have the block
        //   - peer A later receives the block from peer B
        //   - peer A sends us HAVE / block
        for _, c := range ks {
            if at, ok := mq.bcstWants.sentAt[c]; ok {
                if (earliest.IsZero() || at.Before(earliest)) && now.Sub(at) < mq.maxValidLatency {
                    earliest = at
                }
                mq.bcstWants.ClearSentAt(c)
            }
            if at, ok := mq.peerWants.sentAt[c]; ok {
                if (earliest.IsZero() || at.Before(earliest)) && now.Sub(at) < mq.maxValidLatency {
                    earliest = at
                }
                // Clear out the sent time for the CID because we only want to
                // record the latency between the request and the first response
                // for that CID (not subsequent responses)
                mq.peerWants.ClearSentAt(c)
            }
        }

        mq.wllock.Unlock()

        if !earliest.IsZero() {
            // Inform the timeout manager of the calculated latency
            mq.dhTimeoutMgr.UpdateMessageLatency(now.Sub(earliest))
        }
    }

    func (mq *MessageQueue) logOutgoingMessage(wantlist []bsmsg.Entry) {
        // Save some CPU cycles and allocations if log level is higher than debug
        if ce := sflog.Check(zap.DebugLevel, "sent message"); ce == nil {
            return
        }

        self := mq.network.Self()
        for _, e := range wantlist {
            if e.Cancel {
                if e.WantType == pb.Message_Wantlist_Have {
                    log.Debugw("sent message",
                            "type", "CANCEL_WANT_HAVE",
                            "cid", e.Cid,
                            "local", self,
                            "to", mq.p,
                            )
                } else {
                    log.Debugw("sent message",
                            "type", "CANCEL_WANT_BLOCK",
                            "cid", e.Cid,
                            "local", self,
                            "to", mq.p,
                            )
                }
            } else {
                if e.WantType == pb.Message_Wantlist_Have {
                    log.Debugw("sent message",
                            "type", "WANT_HAVE",
                            "cid", e.Cid,
                            "local", self,
                            "to", mq.p,
                            )
                } else {
                    log.Debugw("sent message",
                            "type", "WANT_BLOCK",
                            "cid", e.Cid,
                            "local", self,
                            "to", mq.p,
                            )
                }
            }
        }
    }

    // Whether there is work to be processed
    func (mq *MessageQueue) hasPendingWork() bool {
        return mq.pendingWorkCount() > 0
    }

    // The amount of work that is waiting to be processed
    func (mq *MessageQueue) pendingWorkCount() int {
        mq.wllock.Lock()
        defer mq.wllock.Unlock()

        return mq.bcstWants.pending.Len() + mq.peerWants.pending.Len() + mq.cancels.Len()
    }*/

    // Convert the lists of wants into a Bitswap message
    private Pair<BitSwapMessage, OnSent> extractOutgoingMessage(boolean supportsHave) /* ( func())*/ {
        // Get broadcast and regular wantlist entries.
        // mq.wllock.Lock()
        List<Entry> peerEntries = new ArrayList<>(peerWants.pending.Entries());
        List<Entry> bcstEntries = new ArrayList<>(bcstWants.pending.Entries());
        List<Cid> cancels = new ArrayList<>(this.cancels);
        //cancels = cancels.Keys(); // TODO copy
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
        int sentCancels = 0; // number of cancels in message
        int sentPeerEntries = 0;// number of peer entries in message
        int sentBcstEntries = 0; // number of broadcast entries in message


        // Add each cancel to the message
        for (Cid c : cancels) {
            msgSize += msg.Cancel(c);
            sentCancels++;

            if (msgSize >= maxMessageSize) {
                // TODO goto FINISH;
            }
        }

        // Next, add the wants. If we have too many entries to fit into a single
        // message, sort by priority and include the high priority ones first.
        // However, avoid sorting till we really need to as this code is a
        // called frequently.

        // Add each regular want-have / want-block to the message.

        if (msgSize + (peerEntries.size() * BitSwapMessage.MaxEntrySize) > maxMessageSize) {
            Wantlist.SortEntries(peerEntries);
        }

        for (Entry e : peerEntries) {

            msgSize += msg.AddEntry(e.Cid, e.Priority, e.WantType, true);
            sentPeerEntries++;

            if (msgSize >= maxMessageSize) {
                // TODO goto FINISH
            }
        }

        // Add each broadcast want-have to the message.
        if (msgSize + (bcstEntries.size() * BitSwapMessage.MaxEntrySize) > maxMessageSize) {
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

            if (msgSize >= maxMessageSize) {
                // TODO goto FINISH
            }
        }

        FINISH:

        // Finally, re-take the lock, mark sent and remove any entries from our
        // message that we've decided to cancel at the last minute.
        // TODO mq.wllock.Lock()
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
        for (int i = 0; i < sentCancels; i++) {
            Cid c = cancels.get(i);
            if (!this.cancels.contains(c)) {
                msg.Remove(c);
            } else {
                this.cancels.remove(c);
            }
        }

        // TODO  mq.wllock.Unlock()

        // When the message has been sent, record the time at which each want was
        // sent so we can calculate message latency

        return Pair.create(msg, () -> {
            /* TODO
            now := time.Now()

            mq.wllock.Lock()
            defer mq.wllock.Unlock()

            for _, e := range peerEntries1[:sentPeerEntries] {
                if e.Cid.Defined() { // Check if want was cancelled in the interim
                    mq.peerWants.SentAt(e.Cid, now)
                }
            }

            for _, e := range bcstEntries1[:sentBcstEntries] {
                if e.Cid.Defined() { // Check if want was cancelled in the interim
                    mq.bcstWants.SentAt(e.Cid, now)
                }
            }*/
        });
    }


}
