package io.ipfs.bitswap.internal;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import io.ipfs.bitswap.wantlist.Entry;
import io.ipfs.bitswap.wantlist.Wantlist;
import io.ipfs.cid.Cid;
import io.protos.bitswap.BitswapProtos;

public class RecallWantlist {
    // The list of wants that have been sent
    private final Wantlist sent = new Wantlist();
    // The time at which each want was sent
    private final Map<Cid, Long> sentAt = new HashMap<>();
    // The list of wants that have not yet been sent
    public Wantlist pending = new Wantlist();

    public static RecallWantlist newRecallWantList() {
        return new RecallWantlist();
    }

    // Add want to the pending list
    public void Add(@NonNull Cid c, int priority, BitswapProtos.Message.Wantlist.WantType wtype) {
        pending.Add(c, priority, wtype);
    }

    // Remove wants from both the pending list and the list of sent wants
    public void Remove(@NonNull Cid c) {
        pending.Remove(c);
        sent.Remove(c);
        sentAt.remove(c);
    }

    // Remove wants by type from both the pending list and the list of sent wants
    public void RemoveType(Cid c, BitswapProtos.Message.Wantlist.WantType wtype) {
        pending.RemoveType(c, wtype);
        sent.RemoveType(c, wtype);
        sentAt.remove(c);
    }

    // MarkSent moves the want from the pending to the sent list
//
// Returns true if the want was marked as sent. Returns false if the want wasn't
// pending.
    public boolean MarkSent(Entry e) {
        if (!pending.RemoveType(e.Cid, e.WantType)) {
            return false;
        }
        sent.Add(e.Cid, e.Priority, e.WantType);
        return true;
    }

    // SentAt records the time at which a want was sent
    public void SentAt(@NonNull Cid c, long at) {
        // The want may have been cancelled in the interim
        Long sent = sentAt.get(c);
        if (sent != null) {
            sent = at;
        }
    }

    // ClearSentAt clears out the record of the time a want was sent.
// We clear the sent at time when we receive a response for a key as we
// only need the first response for latency measurement.
    public void ClearSentAt(@NonNull Cid c) {
        sentAt.remove(c);
    }

}
