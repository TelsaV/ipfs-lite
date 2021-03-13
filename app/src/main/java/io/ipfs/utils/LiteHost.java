package io.ipfs.utils;

import androidx.annotation.NonNull;

import io.ipfs.bitswap.BitSwapNetwork;
import io.ipfs.bitswap.network.Receiver;
import io.ipfs.host.Host;

public class LiteHost implements BitSwapNetwork {
    private final Host host;
    private Receiver receiver;

    public LiteHost(@NonNull Host host) {
        this.host = host;
    }

    public static BitSwapNetwork NewLiteHost(@NonNull Host host) {
        return new LiteHost(host);
    }


}
