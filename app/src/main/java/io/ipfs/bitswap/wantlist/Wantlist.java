package io.ipfs.bitswap.wantlist;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.ipfs.cid.Cid;
import io.protos.bitswap.BitswapProtos;

public class Wantlist {
    private final Map<Cid, Entry> set = new HashMap<>();

    public static void SortEntries(@NonNull List<Entry> peerEntries) {
        peerEntries.sort(new Comparator<Entry>() {
            @Override
            public int compare(Entry o1, Entry o2) {
                return Integer.compare(o1.Priority, o2.Priority); // TODO check
            }
        });
    }

    public boolean Add(Cid c, int priority, BitswapProtos.Message.Wantlist.WantType wtype) {
        Entry e = set.get(c);


        // Adding want-have should not override want-block
        if (e != null && (e.WantType == BitswapProtos.Message.Wantlist.WantType.Block
                || wtype == BitswapProtos.Message.Wantlist.WantType.Have)) {
            return false;
        }
        Entry entry = new Entry();
        entry.Cid = c;
        entry.Priority = priority;
        entry.WantType = wtype;
        set.put(c, entry);

        return true;
    }

    // TODO check optimize
    public boolean Remove(@NonNull Cid c) {
        Entry e = set.get(c);
        if (e == null) {
            return false;
        }
        set.remove(c);

        return true;
    }

    public boolean RemoveType(Cid c, BitswapProtos.Message.Wantlist.WantType wtype) {
        Entry e = set.get(c);
        if (e == null) {
            return false;
        }

        // Removing want-have should not remove want-block
        if (e.WantType == BitswapProtos.Message.Wantlist.WantType.Block &&
                wtype == BitswapProtos.Message.Wantlist.WantType.Have) {
            return false;
        }
        set.remove(c);
        return true;
    }

    public Collection<Entry> Entries() {
        return set.values();
    }
}
