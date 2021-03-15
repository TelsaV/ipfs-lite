package io.ipfs.bitswap.wantlist;

import io.protos.bitswap.BitswapProtos;

public class Entry {
    public io.ipfs.cid.Cid Cid;
    public int Priority;
    public BitswapProtos.Message.Wantlist.WantType WantType;
}

